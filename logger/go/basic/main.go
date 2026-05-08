package main

import (
	"fmt"
	"os"

	logger "github.com/phuong-tran/coakka-logger-go"
)

func main() {
	info, err := logger.ReadInfo("")
	if err != nil {
		fail("ReadInfo failed: %v", err)
	}
	fmt.Printf("coakka_logger_info abi=%d version=%s git=%s\n", info.ABIVersion, info.RuntimeVersion, info.GitCommit)

	log, err := logger.Start(logger.LoggerSpec{SystemName: "go-sample-logger", MinLevel: logger.LevelInfo}, "")
	if err != nil {
		fail("Start failed: %v", err)
	}
	defer log.Close()

	category := "samples.logger.go.basic"
	message := `{"event":"hello","language":"go"}`
	sequence, accepted, err := log.Info(category, message)
	if err != nil {
		fail("Info failed: %v", err)
	}
	if !accepted {
		fail("expected INFO log to be accepted")
	}

	record, err := log.AwaitNext(1000)
	if err != nil {
		fail("AwaitNext failed: %v", err)
	}
	if record == nil {
		fail("expected one drained logger record")
	}
	if record.Sequence != sequence {
		fail("record sequence=%d did not match emitted sequence=%d", record.Sequence, sequence)
	}
	if record.Category != category || record.Message != message {
		fail("unexpected record: %+v", record)
	}

	fmt.Printf(
		"coakka_logger_record sequence=%d level=%s category=%s message=%s\n",
		record.Sequence,
		record.LevelName,
		record.Category,
		record.Message,
	)

	stats, err := log.Stats()
	if err != nil {
		fail("Stats failed: %v", err)
	}
	fmt.Printf(
		"coakka_logger_stats emitted=%d delivered=%d dropped=%d\n",
		stats.EmittedCount,
		stats.DeliveredCount,
		stats.DroppedCount,
	)
}

func fail(format string, args ...any) {
	fmt.Fprintf(os.Stderr, format+"\n", args...)
	os.Exit(1)
}
