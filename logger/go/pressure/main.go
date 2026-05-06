package main

import (
	"fmt"
	"os"

	logger "github.com/phuong-tran/coakka-logger-go"
)

func main() {
	const attempts = 8
	const category = "samples.logger.go.pressure"

	log, err := logger.Start(logger.LoggerSpec{
		SystemName:    "go-pressure-logger",
		QueueCapacity: 2,
		MinLevel:      logger.LevelInfo,
	}, "")
	if err != nil {
		fail("Start failed: %v", err)
	}
	defer log.Close()

	accepted := 0
	rejected := 0
	for index := 0; index < attempts; index++ {
		_, ok, err := log.Info(category, fmt.Sprintf(`{"event":"pressure","index":%d}`, index))
		if err != nil {
			rejected++
			continue
		}
		if ok {
			accepted++
		}
	}

	beforeDrain, err := log.Stats()
	if err != nil {
		fail("Stats before drain failed: %v", err)
	}

	drained := 0
	for {
		record, err := log.Poll()
		if err != nil {
			fail("Poll failed: %v", err)
		}
		if record == nil {
			break
		}
		drained++
	}

	afterDrain, err := log.Stats()
	if err != nil {
		fail("Stats after drain failed: %v", err)
	}

	if accepted != 2 {
		fail("expected accepted=2, got %d", accepted)
	}
	if rejected != attempts-accepted {
		fail("expected rejected=%d, got %d", attempts-accepted, rejected)
	}
	if drained != accepted {
		fail("expected drained=%d, got %d", accepted, drained)
	}
	if beforeDrain.QueueHighWatermark != 2 {
		fail("expected queueHighWatermark=2, got %d", beforeDrain.QueueHighWatermark)
	}
	if afterDrain.DroppedCount != uint64(rejected) {
		fail("expected dropped=%d, got %d", rejected, afterDrain.DroppedCount)
	}

	fmt.Printf(
		"coakka_logger_pressure attempts=%d accepted=%d rejected=%d capacity=%d highWatermark=%d\n",
		attempts,
		accepted,
		rejected,
		afterDrain.QueueCapacity,
		afterDrain.QueueHighWatermark,
	)
	fmt.Printf(
		"coakka_logger_stats emitted=%d delivered=%d dropped=%d\n",
		afterDrain.EmittedCount,
		afterDrain.DeliveredCount,
		afterDrain.DroppedCount,
	)
}

func fail(format string, args ...any) {
	fmt.Fprintf(os.Stderr, format+"\n", args...)
	os.Exit(1)
}
