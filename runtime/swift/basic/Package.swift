// swift-tools-version: 6.0
import PackageDescription
// The candidate runner stages this ordinary manifest beside the admitted package.
let package = Package(name: "CoAkkaRuntimeBasicSample", platforms: [.macOS(.v13)],
    dependencies: [.package(name: "RuntimeCandidate", path: "../runtime-candidate")],
    targets: [.executableTarget(name: "CoAkkaRuntimeSample", dependencies: [
        .product(name: "CoAkkaRuntime", package: "RuntimeCandidate")], path: ".",
        exclude: ["tests", "README.md", "run.sh"], sources: ["Sample.swift", "AppScope.swift"])])
