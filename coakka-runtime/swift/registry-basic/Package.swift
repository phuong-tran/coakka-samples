// swift-tools-version: 6.0
import PackageDescription
let package = Package(name: "CoAkkaFirstRun", platforms: [.macOS(.v13)],
    dependencies: [.package(url: "https://github.com/phuong-tran/coakka-runtime-swift.git", exact: "3.0.0")],
    targets: [.executableTarget(name: "CoAkkaFirstRun",
        dependencies: [.product(name: "CoAkkaRuntime", package: "coakka-runtime-swift")])])
