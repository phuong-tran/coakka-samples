// Standalone standard-library installer/build runner, not connector policy.
package main

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"io/fs"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"syscall"
	"time"
)

type candidateMember struct {
	Bytes  int64  `json:"bytes"`
	SHA256 string `json:"sha256"`
}

type goCandidate struct {
	Schema               int                        `json:"schema"`
	State                string                     `json:"state"`
	Module               string                     `json:"module"`
	Version              string                     `json:"version"`
	MinimumRuntime       string                     `json:"minimumRuntime"`
	CoreCommit           string                     `json:"coreCommit"`
	ModuleManifestSHA256 string                     `json:"moduleManifestSha256"`
	Members              map[string]candidateMember `json:"members"`
}

func main() {
	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()
	if err := entry(ctx, os.Args[1:]); err != nil {
		fmt.Fprintln(os.Stderr, "[runtime-go-sample]", err)
		cancel()
		os.Exit(1) // entry has already reaped its direct child and retired staging.
	}
}

func entry(ctx context.Context, args []string) error {
	flags := flag.NewFlagSet("runtime-go-candidate", flag.ContinueOnError)
	repo := flags.String("repo-root", "", "absolute sample repository root")
	if err := flags.Parse(args); err != nil {
		return err
	}
	if flags.NArg() != 1 || (flags.Arg(0) != "basic" && flags.Arg(0) != "deadletter") || !filepath.IsAbs(*repo) {
		return errors.New("usage: runtime-go-candidate --repo-root ABSOLUTE basic|deadletter")
	}
	for _, pair := range os.Environ() {
		key, value, _ := strings.Cut(pair, "=")
		key = strings.ToUpper(key)
		if value == "" {
			continue
		}
		if (strings.HasPrefix(key, "COAKKA_") && key != "COAKKA_SAMPLE_WORK_ROOT" && key != "COAKKA_GO_CANDIDATE_MODULE") ||
			strings.HasPrefix(key, "LD_") || strings.HasPrefix(key, "DYLD_") || key == "GOFLAGS" {
			return fmt.Errorf("remove sample override: %s", key)
		}
	}
	for _, name := range []string{"GOCACHE", "GOTMPDIR"} {
		if err := realDirectory(os.Getenv(name)); err != nil {
			return fmt.Errorf("set explicit %s: %w", name, err)
		}
	}
	work, module := os.Getenv("COAKKA_SAMPLE_WORK_ROOT"), os.Getenv("COAKKA_GO_CANDIDATE_MODULE")
	if err := realDirectory(work); err != nil {
		return fmt.Errorf("work root: %w", err)
	}
	if err := realDirectory(module); err != nil {
		return fmt.Errorf("candidate module: %w", err)
	}
	data, err := boundedFile(filepath.Join(*repo, "artifacts/runtime-go-candidate.json"), 1<<20)
	if err != nil {
		return err
	}
	catalog, err := decodeCandidate(data)
	if err != nil {
		return err
	}
	if err := minimumGo(runtime.Version(), catalog.MinimumRuntime); err != nil {
		return err
	}
	files, err := admitModule(module, catalog)
	if err != nil {
		return err
	}
	appSource, err := boundedFile(filepath.Join(*repo, "runtime/go", flags.Arg(0), "main.go"), 128<<10)
	if err != nil {
		return err
	}
	goTool, err := exec.LookPath("go")
	if err != nil {
		return err
	}
	stage, err := os.MkdirTemp(work, "coakka-go-sample-")
	if err != nil {
		return err
	}
	// Only this unique directory is owned. Shared compiler caches are never removed.
	return withStage(stage, func() error {
		stagedModule, app, nativeCache := filepath.Join(stage, "module"), filepath.Join(stage, "app"), filepath.Join(stage, "native-cache")
		for _, path := range []string{stagedModule, app, nativeCache} {
			if err := os.Mkdir(path, 0700); err != nil {
				return err
			}
		}
		for name, content := range files {
			path := filepath.Join(stagedModule, filepath.FromSlash(name))
			if err := os.MkdirAll(filepath.Dir(path), 0700); err != nil {
				return err
			}
			if err := os.WriteFile(path, content, 0600); err != nil {
				return err
			}
		}
		quotedModule, _ := json.Marshal(filepath.ToSlash(stagedModule))
		mod := "module coakka-runtime-sample\n\ngo 1.22\n\nrequire " + catalog.Module + " v0.0.0\nreplace " + catalog.Module + " => " + string(quotedModule) + "\n"
		if err := os.WriteFile(filepath.Join(app, "go.mod"), []byte(mod), 0600); err != nil {
			return err
		}
		if err := os.WriteFile(filepath.Join(app, "main.go"), appSource, 0600); err != nil {
			return err
		}
		environment := append(os.Environ(), "GOTOOLCHAIN=local", "GOWORK=off", "GOPROXY=off", "GOSUMDB=off", "GOENV=off")
		executable := filepath.Join(stage, "sample")
		if runtime.GOOS == "windows" {
			executable += ".exe"
		}
		if err := runCommand(ctx, goTool, []string{"build", "-trimpath", "-o", executable, "."}, app, environment, 180*time.Second); err != nil {
			return err
		}
		fmt.Printf("coakka_sample_package runtime=go version=%s moduleManifest=%s\n", catalog.Version, catalog.ModuleManifestSHA256)
		// Move the actual build inputs before running the embedded-image consumer.
		if err := os.Rename(stagedModule, stagedModule+"-retired"); err != nil {
			return err
		}
		if err := os.Rename(app, app+"-retired"); err != nil {
			return err
		}
		return runCommand(ctx, executable, nil, stage, append(environment, "COAKKA_RUNTIME_CACHE_DIR="+nativeCache), 30*time.Second)
	})
}

func withStage(stage string, action func() error) (result error) {
	defer func() { result = errors.Join(result, os.RemoveAll(stage)) }()
	return action()
}

func runCommand(ctx context.Context, executable string, args []string, cwd string, env []string, bound time.Duration) error {
	childContext, cancel := context.WithTimeout(ctx, bound)
	defer cancel()
	command := exec.CommandContext(childContext, executable, args...)
	command.Dir, command.Env = cwd, env
	command.Stdout, command.Stderr = os.Stdout, os.Stderr
	command.WaitDelay = time.Second
	// Trusted Go owns its compiler children. This runner bounds/reaps its direct
	// child; it is not a process-tree sandbox or a graceful Core-stop guarantee.
	return errors.Join(command.Run(), childContext.Err())
}

func realDirectory(path string) error {
	if !filepath.IsAbs(path) {
		return errors.New("expected absolute existing directory")
	}
	info, err := os.Lstat(path)
	if err != nil {
		return err
	}
	if !info.IsDir() || info.Mode()&os.ModeSymlink != 0 {
		return errors.New("directory must not be a link")
	}
	return nil
}

func boundedFile(path string, maximum int64) ([]byte, error) {
	before, err := os.Lstat(path)
	if err != nil {
		return nil, err
	}
	if !before.Mode().IsRegular() || before.Size() <= 0 || before.Size() > maximum {
		return nil, errors.New("expected bounded regular file")
	}
	file, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	opened, err := file.Stat()
	if err != nil {
		return nil, err
	}
	if !os.SameFile(before, opened) {
		return nil, errors.New("input changed before read")
	}
	content, err := io.ReadAll(io.LimitReader(file, maximum+1))
	if err != nil {
		return nil, err
	}
	after, err := file.Stat()
	if err != nil {
		return nil, err
	}
	if int64(len(content)) != before.Size() || after.Size() != before.Size() || !after.ModTime().Equal(opened.ModTime()) {
		return nil, errors.New("input changed during read")
	}
	return content, nil
}

func decodeCandidate(data []byte) (goCandidate, error) {
	var result goCandidate
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	err := decoder.Decode(&result)
	if err != nil {
		return result, err
	}
	var extra any
	if decoder.Decode(&extra) != io.EOF {
		return result, errors.New("trailing catalog data")
	}
	core, coreErr := hex.DecodeString(result.CoreCommit)
	version := strings.Split(result.Version, "-dev.")
	validVersion := len(version) == 2 && len(version[1]) == 8 && strings.Trim(version[1], "0123456789") == ""
	if validVersion {
		validVersion = minimumGo("go"+version[0], version[0]) == nil
	}
	if result.Schema != 1 || result.State != "private-candidate" || result.Module != "github.com/phuong-tran/coakka-runtime-go" ||
		!validVersion || coreErr != nil || len(core) != 20 || strings.ToLower(result.CoreCommit) != result.CoreCommit ||
		minimumGo("go"+result.MinimumRuntime, result.MinimumRuntime) != nil || !validDigest(result.ModuleManifestSHA256) || len(result.Members) == 0 || len(result.Members) > 128 {
		return result, errors.New("invalid Go candidate identity")
	}
	var total int64
	for name, member := range result.Members {
		if !fs.ValidPath(name) || strings.ContainsAny(name, "\\:") || member.Bytes <= 0 || member.Bytes > 8<<20 || !validDigest(member.SHA256) {
			return result, errors.New("invalid module member")
		}
		total += member.Bytes
	}
	if total > 64<<20 {
		return result, errors.New("module extent exceeds bound")
	}
	if _, present := result.Members["go.mod"]; !present {
		return result, errors.New("module declaration missing")
	}
	return result, nil
}

func validDigest(value string) bool {
	decoded, err := hex.DecodeString(value)
	return err == nil && len(decoded) == sha256.Size && strings.ToLower(value) == value
}

func admitModule(root string, catalog goCandidate) (map[string][]byte, error) {
	if err := realDirectory(root); err != nil {
		return nil, err
	}
	files := make(map[string][]byte, len(catalog.Members))
	entries := 1
	var walk func(string, int) error
	walk = func(directory string, depth int) error {
		if depth > 12 {
			return errors.New("module depth exceeds bound")
		}
		handle, err := os.Open(directory)
		if err != nil {
			return err
		}
		defer handle.Close()
		for {
			// ReadDir(-1)/WalkDir would allocate the whole foreign directory
			// before enforcing the ceiling. Keep one entry and <=13 open dirs.
			items, readErr := handle.ReadDir(1)
			if readErr != nil && readErr != io.EOF {
				return readErr
			}
			for _, item := range items {
				entries++
				if entries > 256 || item.Type()&os.ModeSymlink != 0 {
					return errors.New("module tree/link refused")
				}
				path := filepath.Join(directory, item.Name())
				if item.IsDir() {
					if err := walk(path, depth+1); err != nil {
						return err
					}
					continue
				}
				name, err := filepath.Rel(root, path)
				if err != nil {
					return err
				}
				name = filepath.ToSlash(name)
				member, found := catalog.Members[name]
				if !found {
					return fmt.Errorf("unexpected module member: %s", name)
				}
				content, err := boundedFile(path, 8<<20)
				if err != nil {
					return err
				}
				digest := sha256.Sum256(content)
				if int64(len(content)) != member.Bytes || hex.EncodeToString(digest[:]) != member.SHA256 {
					return fmt.Errorf("module member differs: %s", name)
				}
				files[name] = content
			}
			if readErr == io.EOF {
				return nil
			}
		}
	}
	err := walk(root, 0)
	if err == nil && len(files) != len(catalog.Members) {
		err = errors.New("module members missing")
	}
	return files, err
}
func minimumGo(actual, minimum string) error {
	actual = strings.TrimPrefix(actual, "go")
	actual = strings.TrimSuffix(actual, " X:cgocheck2")
	parse := func(value string) ([3]int, error) {
		var result [3]int
		parts := strings.Split(value, ".")
		if len(parts) != 3 {
			return result, errors.New("expected release Go version")
		}
		for i, part := range parts {
			if part == "" || strings.Trim(part, "0123456789") != "" {
				return result, errors.New("expected release Go version")
			}
			n, err := strconv.Atoi(part)
			if err != nil || n < 0 {
				return result, errors.New("expected release Go version")
			}
			result[i] = n
		}
		return result, nil
	}
	a, err := parse(actual)
	if err != nil {
		return err
	}
	b, err := parse(minimum)
	if err != nil {
		return err
	}
	for i := range a {
		if a[i] > b[i] {
			return nil
		}
		if a[i] < b[i] {
			return fmt.Errorf("sample requires Go >= %s", minimum)
		}
	}
	return nil
}
