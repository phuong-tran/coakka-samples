#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command swift "Install Swift 6.0 or newer on macOS 13 or newer, then retry."

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

mkdir -p "${tmp_dir}/Sources/ConsumerSmoke"
cp "${script_dir}/main.swift" "${tmp_dir}/Sources/ConsumerSmoke/main.swift"

cat > "${tmp_dir}/Package.swift" <<'EOF'
// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "CoAkkaRuntimeSwiftBasicSample",
    platforms: [.macOS(.v13)],
    dependencies: [
        .package(url: "https://github.com/phuong-tran/coakka-runtime-swift.git", exact: "1.4.1"),
    ],
    targets: [
        .executableTarget(
            name: "ConsumerSmoke",
            dependencies: [
                .product(name: "CoAkkaRuntime", package: "coakka-runtime-swift"),
            ]
        ),
    ]
)
EOF

(
  cd "${tmp_dir}"
  swift run ConsumerSmoke
)
