enum SampleScopeError: Error, CustomStringConvertible {
    case operationAndCleanup(any Error, any Error)

    var description: String {
        switch self {
        case let .operationAndCleanup(primary, cleanup):
            return "operation failed: \(primary); shutdown failed: \(cleanup)"
        }
    }
}

enum SampleScope {
    static func run<T>(_ operation: () throws -> T, close: () throws -> Void) throws -> T {
        let outcome = Result(catching: operation)
        do { try close() }
        catch {
            // Swift defer cannot throw; preserve both causes at this app-owned scope.
            if case .failure(let primary) = outcome { throw SampleScopeError.operationAndCleanup(primary, error) }
            throw error
        }
        return try outcome.get()
    }
}
