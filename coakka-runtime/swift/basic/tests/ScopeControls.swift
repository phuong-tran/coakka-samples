final class Marker: Error { }
enum ControlFailure: Error { case lostCause; case cleanupCount }

@main struct ScopeControls {
    static func main() throws {
        for bodyFails in [false, true] {
            for closeFails in [false, true] {
                let body = Marker(), cleanup = Marker()
                var closes = 0
                var observed: (any Error)?
                do {
                    try SampleScope.run({ if bodyFails { throw body } }, close: {
                        closes += 1
                        if closeFails { throw cleanup }
                    })
                } catch { observed = error }
                guard closes == 1 else { throw ControlFailure.cleanupCount }
                if bodyFails && closeFails {
                    guard let combined = observed as? SampleScopeError,
                          case let .operationAndCleanup(primary, secondary) = combined,
                          (primary as? Marker) === body, (secondary as? Marker) === cleanup
                    else { throw ControlFailure.lostCause }
                } else if bodyFails {
                    guard (observed as? Marker) === body else { throw ControlFailure.lostCause }
                } else if closeFails {
                    guard (observed as? Marker) === cleanup else { throw ControlFailure.lostCause }
                } else {
                    guard observed == nil else { throw ControlFailure.lostCause }
                }
            }
        }
        print("swift_sample_cleanup_controls cases=4")
    }
}
