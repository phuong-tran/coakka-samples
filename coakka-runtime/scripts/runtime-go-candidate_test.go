package main

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"testing"
	"time"
)

// The catalog is an untrusted cold input. Bound bytes before the standard JSON
// parser; successful admission must retain the selected semantic-major pair.
func FuzzCandidateCatalog(f *testing.F) {
	f.Add([]byte(`{}`))
	f.Add([]byte(`{"schema":1,"state":"private-candidate","module":"github.com/phuong-tran/coakka-runtime-go/v3","version":"3.0.0","minimumRuntime":"1.22.12","coreCommit":"1522b4a9f30da1039ef8c7d198bbecfc749a2456","moduleManifestSha256":"0000000000000000000000000000000000000000000000000000000000000000","members":{"go.mod":{"bytes":1,"sha256":"0000000000000000000000000000000000000000000000000000000000000000"}}}`))
	f.Fuzz(func(t *testing.T, data []byte) {
		if len(data) > 1<<20 {
			return
		}
		value, err := decodeCandidate(data)
		if err == nil && (value.Module != "github.com/phuong-tran/coakka-runtime-go/v3" || value.Version != "3.0.0") {
			t.Fatal("admitted inconsistent semantic-major coordinate")
		}
	})
}

func controlDir(t *testing.T) string {
	t.Helper()
	root := os.Getenv("COAKKA_SAMPLE_WORK_ROOT")
	if root == "" {
		t.Fatal("explicit external test work root required")
	}
	path, err := os.MkdirTemp(root, "coakka-go-control-")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if err := os.RemoveAll(path); err != nil {
			t.Error(err)
		}
	})
	return path
}

func candidateFixture(t *testing.T) (string, goCandidate) {
	t.Helper()
	dir := controlDir(t)
	content := []byte("module example.invalid/sample\n")
	digest := sha256.Sum256(content)
	if err := os.WriteFile(filepath.Join(dir, "go.mod"), content, 0600); err != nil {
		t.Fatal(err)
	}
	return dir, goCandidate{Schema: 1, State: "private-candidate", Module: "github.com/phuong-tran/coakka-runtime-go/v3",
		Version: "3.0.0", MinimumRuntime: "1.22.12", CoreCommit: "1522b4a9f30da1039ef8c7d198bbecfc749a2456",
		ModuleManifestSHA256: hex.EncodeToString(digest[:]), Members: map[string]candidateMember{
			"go.mod": {Bytes: int64(len(content)), SHA256: hex.EncodeToString(digest[:])}}}
}

func TestCandidateCatalogRefusesUnknownAndBoundViolations(t *testing.T) {
	_, catalog := candidateFixture(t)
	encode := func(value goCandidate) []byte {
		data, err := json.Marshal(value)
		if err != nil {
			t.Fatal(err)
		}
		return data
	}
	if _, err := decodeCandidate(encode(catalog)); err != nil {
		t.Fatal(err)
	}
	for _, change := range []func(*goCandidate){
		func(c *goCandidate) { c.State = "published" }, func(c *goCandidate) { c.Module = "other" },
		func(c *goCandidate) { c.Members = nil }, func(c *goCandidate) {
			c.Members = map[string]candidateMember{"../outside": {Bytes: 1, SHA256: catalog.ModuleManifestSHA256}}
		},
		func(c *goCandidate) {
			c.Members = map[string]candidateMember{"go.mod": {Bytes: 8<<20 + 1, SHA256: catalog.ModuleManifestSHA256}}
		},
		func(c *goCandidate) { c.ModuleManifestSHA256 = "wrong" },
		func(c *goCandidate) { c.Module = "github.com/phuong-tran/coakka-runtime-go/v2" },
		func(c *goCandidate) { c.Version = "2.0.0" },
	} {
		copy := catalog
		change(&copy)
		if _, err := decodeCandidate(encode(copy)); err == nil {
			t.Fatal("invalid catalog admitted")
		}
	}
	for _, data := range [][]byte{append(encode(catalog), []byte(" {}")...), []byte(`{"unknown":true}`), []byte("not-json")} {
		if _, err := decodeCandidate(data); err == nil {
			t.Fatal("foreign catalog admitted")
		}
	}
}

func TestCandidateExactMembersAndRegularRead(t *testing.T) {
	dir, catalog := candidateFixture(t)
	if _, err := admitModule(dir, catalog); err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(dir, "go.mod")
	if _, err := boundedFile(path, 1); err == nil {
		t.Fatal("oversized read admitted")
	}
	if _, err := boundedFile(dir, 1); err == nil {
		t.Fatal("directory read admitted")
	}
	content, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	content[0] ^= 1
	if err := os.WriteFile(path, content, 0600); err != nil {
		t.Fatal(err)
	}
	if _, err := admitModule(dir, catalog); err == nil {
		t.Fatal("changed member admitted")
	}
	if err := os.Remove(path); err != nil {
		t.Fatal(err)
	}
	if _, err := admitModule(dir, catalog); err == nil {
		t.Fatal("missing member admitted")
	}
	if err := os.WriteFile(filepath.Join(dir, "extra"), []byte("x"), 0600); err != nil {
		t.Fatal(err)
	}
	if _, err := admitModule(dir, catalog); err == nil {
		t.Fatal("extra member admitted")
	}
}

func TestCandidateLinkRefusal(t *testing.T) {
	dir, catalog := candidateFixture(t)
	if runtime.GOOS == "windows" {
		// An unprivileged real reparse point, without Developer Mode or file-
		// symlink privileges. Values cross as environment data, not shell code.
		command := exec.Command("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-Command",
			"New-Item -ItemType Junction -Path $env:COAKKA_CONTROL_LINK -Target $env:COAKKA_CONTROL_TARGET | Out-Null")
		command.Env = append(os.Environ(), "COAKKA_CONTROL_LINK="+filepath.Join(dir, "link"), "COAKKA_CONTROL_TARGET="+dir)
		if output, err := command.CombinedOutput(); err != nil {
			t.Fatalf("junction: %v %s", err, output)
		}
	} else {
		if err := os.Symlink(filepath.Join(dir, "go.mod"), filepath.Join(dir, "link")); err != nil {
			t.Fatal(err)
		}
	}
	if _, err := admitModule(dir, catalog); err == nil {
		t.Fatal("link admitted")
	}
}

func TestCandidateTreeCeilingAndDepth(t *testing.T) {
	dir, catalog := candidateFixture(t)
	for i := 0; i < 256; i++ {
		if err := os.Mkdir(filepath.Join(dir, fmt.Sprintf("empty-%03d", i)), 0700); err != nil {
			t.Fatal(err)
		}
	}
	if _, err := admitModule(dir, catalog); err == nil {
		t.Fatal("tree ceiling not enforced")
	}
	deep, second := candidateFixture(t)
	path := deep
	for i := 0; i < 13; i++ {
		path = filepath.Join(path, "child")
		if err := os.Mkdir(path, 0700); err != nil {
			t.Fatal(err)
		}
	}
	if _, err := admitModule(deep, second); err == nil {
		t.Fatal("depth ceiling not enforced")
	}
}

func TestCandidateMinimumUsesActualFloorAndPatch(t *testing.T) {
	for _, actual := range []string{"go1.22.12", "go1.22.12 X:cgocheck2", "go1.26.3"} {
		if err := minimumGo(actual, "1.22.12"); err != nil {
			t.Fatal(err)
		}
	}
	for _, actual := range []string{"go1.22.11", "go1.21.99", "devel go1.22", "go1.23rc1", "unknown"} {
		if err := minimumGo(actual, "1.22.12"); err == nil {
			t.Fatal("unsupported version admitted")
		}
	}
}

func TestCandidateSampleSelectorIsClosed(t *testing.T) {
	for _, name := range []string{"basic", "deadletter", "replica-file-fanout"} {
		if !sampleName(name) {
			t.Fatal("concrete sample refused")
		}
	}
	for _, name := range []string{"", "../basic", "go/basic", "replica", "replica-file-fanout/../basic"} {
		if sampleName(name) {
			t.Fatal("unknown sample admitted")
		}
	}
}

func TestStageRetiresOnSuccessFailureAndCancelledChild(t *testing.T) {
	parent := controlDir(t)
	for _, fail := range []bool{false, true} {
		stage, err := os.MkdirTemp(parent, "stage-")
		if err != nil {
			t.Fatal(err)
		}
		injected := errors.New("injected")
		result := withStage(stage, func() error {
			if fail {
				return injected
			}
			return nil
		})
		if fail && !errors.Is(result, injected) {
			t.Fatal(result)
		}
		if !fail && result != nil {
			t.Fatal(result)
		}
		if _, err := os.Stat(stage); !os.IsNotExist(err) {
			t.Fatal("stage retained")
		}
	}
	exe, err := os.Executable()
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if err := runCommand(ctx, exe, []string{"-test.run=TestCandidateChildFixture"}, parent,
		append(os.Environ(), "COAKKA_CONTROL_CHILD=1"), time.Second); !errors.Is(err, context.Canceled) {
		t.Fatal("cancelled child admitted")
	}
	if err := runCommand(context.Background(), exe, []string{"-test.run=TestCandidateChildFixture"}, parent,
		append(os.Environ(), "COAKKA_CONTROL_CHILD=1"), 50*time.Millisecond); !errors.Is(err, context.DeadlineExceeded) {
		t.Fatal("child deadline not enforced")
	}
}

func TestCandidateChildFixture(t *testing.T) {
	if os.Getenv("COAKKA_CONTROL_CHILD") == "1" {
		time.Sleep(10 * time.Second)
		os.Exit(0)
	}
}
