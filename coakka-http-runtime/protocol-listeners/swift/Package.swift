// swift-tools-version: 6.0
import Foundation
import PackageDescription

// A private candidate can be consumed from its unpacked SwiftPM directory.
let dependency: Package.Dependency
if let path = ProcessInfo.processInfo.environment["COAKKA_HTTP_SWIFT_PACKAGE"] {
    dependency = .package(name: "coakka-http-runtime-swift", path: path)
} else {
    dependency = .package(
        url: "https://github.com/phuong-tran/coakka-http-runtime-swift.git", exact: "2.3.1")
}

let package = Package(
    name: "CoAkkaHttpListenerSample",
    platforms: [.macOS(.v14)],
    dependencies: [dependency],
    targets: [
        .executableTarget(name: "Server", dependencies: [
            .product(name: "CoAkkaHttp", package: "coakka-http-runtime-swift"),
        ]),
    ]
)
