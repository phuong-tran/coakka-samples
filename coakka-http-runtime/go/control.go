package main

// These are startup/control-plane recipes, not per-request policy. Core owns
// default resolution, effective settings, monitor generations and refusals.
import (
	"fmt"

	coakkahttp "github.com/phuong-tran/coakka-http-runtime-go"
)

// sampleLimits declares an application memory budget for the tiny echo demo.
// Other fields remain unspecified unless the focused tuning recipe is selected.
func sampleLimits(tuning bool) coakkahttp.Limits {
	limits := coakkahttp.DefaultLimits()
	limits.MaxRequestBodyBytes = 64 << 10
	if tuning {
		limits.Notifications = coakkahttp.NotificationProfiles{
			Request: coakkahttp.NotificationSmall, Terminal: coakkahttp.NotificationMedium,
		}
		limits.TransportTimeouts = coakkahttp.TransportTimeouts{
			HeaderMillis: 3000, BodyMillis: 4000, KeepAliveMillis: 2000, IdleMillis: 5000,
		}
	}
	return limits
}

// verifySettings queries the instance, never reconstructs a default from input.
// Startup CPU placement is not live utilization or a process-wide Go CPU quota.
func verifySettings(service *coakkahttp.Service, tuning, singleCPU bool) error {
	info, err := service.RuntimeInfo()
	if err != nil {
		return err
	}
	if !info.ServiceStarted || !info.Execution.Observed || info.Execution.ActiveEventLoops == 0 {
		return fmt.Errorf("Core did not report an active observed service: %+v", info.Execution)
	}
	if info.Limits.MaxRequestBodyBytes != 64<<10 {
		return fmt.Errorf("Core did not accept the sample body ceiling")
	}
	if tuning && (info.RequestNotificationBatchSize != 1 || info.TerminalNotificationBatchSize != 4 ||
		info.Limits.HeaderTimeoutMillis != 3000 || info.Limits.BodyTimeoutMillis != 4000 ||
		info.Limits.KeepAliveTimeoutMillis != 2000 || info.Limits.IdleTimeoutMillis != 5000) {
		return fmt.Errorf("Core effective configuration differs from explicit tuning")
	}
	if singleCPU && (info.CPU.RequestedPolicy != coakkahttp.CPUSingle ||
		info.CPU.Placement != coakkahttp.CPUPlacementApplied || info.CPU.SelectedCPUCount != 1) {
		return fmt.Errorf("Core did not report requested SINGLE placement: %+v", info.CPU)
	}
	fmt.Printf("core-cpu-placement=%d selected=%d ids=%v loops=%d request-batch=%d terminal-batch=%d\n",
		info.CPU.Placement, info.CPU.SelectedCPUCount, info.CPU.SelectedCPUIDs,
		info.Execution.ActiveEventLoops, info.RequestNotificationBatchSize, info.TerminalNotificationBatchSize)
	return nil
}

// demonstrateMonitorReload changes collection within the startup reservation,
// rejects stale and oversized intent, then restores the original policy. A
// refused operation must preserve both generation and the complete policy.
func demonstrateMonitorReload(service *coakkahttp.Service) error {
	initial, err := service.MonitorConfig()
	if err != nil {
		return err
	}
	desired := coakkahttp.MonitorPolicy{
		Collection:          coakkahttp.MonitorAggregates,
		AggregateCategories: initial.Policy.AggregateCategories,
	}
	accepted, err := service.ApplyMonitorPolicy(initial.Generation, desired)
	if err != nil {
		return err
	}
	if accepted.Reason != coakkahttp.MonitorApplyApplied || !accepted.Changed || accepted.Effective.Policy != desired {
		return fmt.Errorf("monitor apply failed: %+v", accepted)
	}
	stale, err := service.ApplyMonitorPolicy(initial.Generation, initial.Policy)
	if err != nil {
		return err
	}
	if stale.Reason != coakkahttp.MonitorApplyGenerationConflict || stale.Changed ||
		stale.Effective.Generation != accepted.Effective.Generation || stale.Effective.Policy != desired {
		return fmt.Errorf("stale monitor update changed effective state: %+v", stale)
	}
	if initial.ReservedEventCapacity == ^uint32(0) {
		return fmt.Errorf("monitor reservation cannot represent an over-budget example")
	}
	oversized := initial.Policy
	oversized.ActiveEventCapacity = initial.ReservedEventCapacity + 1
	refused, err := service.ApplyMonitorPolicy(accepted.Effective.Generation, oversized)
	if err != nil {
		return err
	}
	if refused.Reason != coakkahttp.MonitorApplyResourceReservationExceeded || refused.Changed ||
		refused.Effective.Generation != accepted.Effective.Generation || refused.Effective.Policy != desired {
		return fmt.Errorf("oversized monitor update changed effective state: %+v", refused)
	}
	restored, err := service.ApplyMonitorPolicy(accepted.Effective.Generation, initial.Policy)
	if err != nil {
		return err
	}
	current, err := service.MonitorConfig()
	if err != nil {
		return err
	}
	if restored.Reason != coakkahttp.MonitorApplyApplied || !restored.Changed ||
		current.Generation != restored.Effective.Generation || current.Policy != initial.Policy {
		return fmt.Errorf("monitor restoration did not preserve the initial policy")
	}
	fmt.Println("go-monitor-reload=pass")
	return nil
}
