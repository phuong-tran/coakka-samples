param(
  [Parameter(Position = 0)]
  [string]$Mode = "smoke",

  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$EvidenceArguments
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$EvidenceRelease = "1.3.4+dc6ec284"
$EvidenceVersion = "1.3.4"
$ScriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepositoryRoot = (Resolve-Path (Join-Path $ScriptDirectory "..")).Path
$SystemTar = Join-Path $env:WINDIR "System32\tar.exe"
$TemporaryRoot = Join-Path ([IO.Path]::GetTempPath()) (
  "coakka-native-evidence-{0}-{1}" -f $PID, [Guid]::NewGuid().ToString("N"))
$CandidateAdmission = ""

function Write-EvidenceStatus([string]$Message) {
  [Console]::Error.WriteLine("[coakka-sample] $Message")
}

function Resolve-CandidateDirectory([string]$Path) {
  $Item = Get-Item -LiteralPath $Path -Force
  if (-not $Item.PSIsContainer) { throw "candidate path must be an existing directory" }
  $Resolved = [IO.Path]::GetFullPath($Item.FullName).TrimEnd('\', '/')
  if ($Resolved -eq [IO.Path]::GetPathRoot($Item.FullName).TrimEnd('\', '/')) {
    throw "native-startup does not accept filesystem roots"
  }
  $Current = $Item
  for ($Depth = 0; $Depth -lt 128; $Depth++) {
    if (($Current.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
      throw "native-startup does not accept Windows reparse-point paths"
    }
    $Parent = Split-Path -Parent $Current.FullName
    if (-not $Parent -or $Parent -eq $Current.FullName) { return $Resolved }
    $Current = Get-Item -LiteralPath $Parent -Force
  }
  throw "native-startup directory depth exceeds its admission bound"
}

function Test-DirectoryContains([string]$Parent, [string]$Child) {
  return ($Child + '\').StartsWith($Parent + '\', [StringComparison]::OrdinalIgnoreCase)
}

try {
  if ($Mode -eq "native-startup") {
    if ($EvidenceArguments.Count -ne 0) {
      throw "native-startup accepts no arguments"
    }
    if ($env:COAKKA_NATIVE_EVIDENCE_USE_PREBUILT -eq "1" -or
        $env:COAKKA_NATIVE_EVIDENCE_RUNTIME_SOURCE_DIR) {
      throw "native-startup requires an installed candidate; no published/prebuilt or Core source fallback"
    }
    $CandidatePrefix = $env:COAKKA_NATIVE_EVIDENCE_RUNTIME_PREFIX
    $CandidateBuild = $env:COAKKA_NATIVE_EVIDENCE_BUILD_DIR
    if (-not $CandidatePrefix -or -not $CandidateBuild) {
      throw "native-startup requires COAKKA_NATIVE_EVIDENCE_RUNTIME_PREFIX and COAKKA_NATIVE_EVIDENCE_BUILD_DIR"
    }
    if ($CandidatePrefix -notmatch '^(?:[A-Za-z]:[\\/]|\\\\)' -or
        $CandidateBuild -notmatch '^(?:[A-Za-z]:[\\/]|\\\\)' -or
        $CandidateBuild -match '^[A-Za-z]:[\\/]?$') {
      throw "native-startup prefix/build directory must be explicit absolute non-root paths"
    }
    $CandidatePrefix = Resolve-CandidateDirectory $CandidatePrefix
    $CandidateBuild = Resolve-CandidateDirectory $CandidateBuild
    $CandidateSourceRoot = Resolve-CandidateDirectory $RepositoryRoot
    if ((Test-DirectoryContains $CandidatePrefix $CandidateBuild) -or
        (Test-DirectoryContains $CandidateBuild $CandidatePrefix) -or
        (Test-DirectoryContains $CandidateSourceRoot $CandidateBuild) -or
        (Test-DirectoryContains $CandidateBuild $CandidateSourceRoot) -or
        (Test-Path -LiteralPath (Join-Path $CandidateBuild '.git')) -or
        (Test-Path -LiteralPath (Join-Path $CandidateBuild 'CMakeLists.txt'))) {
      throw "native-startup prefix/build overlap or source build root is forbidden"
    }
    $CandidateSanitizer = if ($env:COAKKA_NATIVE_EVIDENCE_ENABLE_SANITIZERS) { $env:COAKKA_NATIVE_EVIDENCE_ENABLE_SANITIZERS } else { '0' }
    $CandidateTsan = if ($env:COAKKA_NATIVE_EVIDENCE_ENABLE_TSAN) { $env:COAKKA_NATIVE_EVIDENCE_ENABLE_TSAN } else { '0' }
    if ($CandidateSanitizer -notin @('0', '1') -or $CandidateTsan -notin @('0', '1')) {
      throw "native-startup sanitizer preferences must be 0 or 1"
    }
    if ($CandidateSanitizer -eq '1' -and $CandidateTsan -eq '1') {
      throw "native-startup ASan/UBSan and TSan require separate builds"
    }
    $CandidateAdmission = Join-Path $CandidateBuild ("native-startup-admission-" + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $CandidateAdmission | Out-Null
    cmake -S (Join-Path $ScriptDirectory 'native-startup-preflight') -B $CandidateAdmission `
      "-DCOAKKA_NATIVE_EVIDENCE_RUNTIME_PREFIX=$CandidatePrefix" |
      ForEach-Object { [Console]::Error.WriteLine($_) }
    if ($LASTEXITCODE -ne 0) { throw "native-startup candidate admission failed" }
    $CandidateArguments = @("-S", $ScriptDirectory, "-B", $CandidateBuild,
      "-DCMAKE_BUILD_TYPE=Release", "-DCMAKE_PREFIX_PATH=$CandidatePrefix",
      "-DCOAKKA_NATIVE_EVIDENCE_RUNTIME_PACKAGE=CoAkkaV2",
      "-DCOAKKA_NATIVE_EVIDENCE_RUNTIME_PREFIX=$CandidatePrefix",
      "-DCOAKKA_NATIVE_EVIDENCE_RUNTIME_SOURCE_DIR=",
      "-DCOAKKA_NATIVE_EVIDENCE_REQUIRE_NATIVE_STARTUP=ON",
      "-DCOAKKA_NATIVE_EVIDENCE_ENABLE_SANITIZERS=OFF",
      "-DCOAKKA_NATIVE_EVIDENCE_ENABLE_TSAN=OFF",
      "-DCMAKE_SKIP_RPATH=OFF", "-DCMAKE_SKIP_BUILD_RPATH=OFF",
      "-DCMAKE_BUILD_WITH_INSTALL_RPATH=OFF")
    if ($CandidateSanitizer -eq "1") {
      $CandidateArguments += "-DCOAKKA_NATIVE_EVIDENCE_ENABLE_SANITIZERS=ON"
    }
    if ($CandidateTsan -eq "1") {
      $CandidateArguments += "-DCOAKKA_NATIVE_EVIDENCE_ENABLE_TSAN=ON"
    }
    cmake @CandidateArguments | ForEach-Object { [Console]::Error.WriteLine($_) }
    if ($LASTEXITCODE -ne 0) { throw "failed to configure native-startup candidate" }
    cmake --build $CandidateBuild --config Release --target coakka_runtime_v2_native_startup_evidence |
      ForEach-Object { [Console]::Error.WriteLine($_) }
    if ($LASTEXITCODE -ne 0) { throw "failed to build native-startup candidate" }
    $CandidateExecutable = Join-Path $CandidateBuild "Release\coakka_runtime_v2_native_startup_evidence.exe"
    if (-not (Test-Path $CandidateExecutable)) {
      $CandidateExecutable = Join-Path $CandidateBuild "coakka_runtime_v2_native_startup_evidence.exe"
    }
    if (-not (Test-Path $CandidateExecutable)) { throw "native-startup executable missing" }
    $env:PATH = "$(Join-Path $CandidatePrefix 'bin');$env:PATH"
    $env:COAKKA_EVIDENCE_EXECUTION_PATH = "installed-source-candidate"
    cmake -P (Join-Path $CandidateBuild 'native-startup-Release.cmake')
    exit $LASTEXITCODE
  }
  if ($Mode -eq "file-lane") {
    $Mode = "file-lane-simple"
  } elseif ($Mode -eq "stream-lane") {
    $Mode = "stream-lane-simple"
  }
  $IsFileLane = $Mode -like "file-lane-*"
  $IsStreamLane = $Mode -like "stream-lane-*"
  $IsOwnerAware = $Mode -like "*-owner-aware"
  $Architecture = [Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString().ToLowerInvariant()
  $Platform = if ($Architecture -eq "arm64") { "windows-aarch64" } else { "windows-x86_64" }
  if (($IsFileLane -or $IsStreamLane) -and $env:COAKKA_NATIVE_EVIDENCE_RUNTIME_SOURCE_DIR) {
    $RequireOption = if ($IsStreamLane) {
      "COAKKA_NATIVE_EVIDENCE_REQUIRE_STREAM_LANE"
    } else {
      "COAKKA_NATIVE_EVIDENCE_REQUIRE_FILE_LANE"
    }
    $EvidenceTarget = if ($IsStreamLane) {
      "coakka_runtime_v2_stream_lane_evidence"
    } else {
      "coakka_runtime_v2_file_lane_evidence"
    }
    $BuildDirectory = Join-Path $TemporaryRoot "build"
    cmake -S $ScriptDirectory -B $BuildDirectory `
      "-DCOAKKA_NATIVE_EVIDENCE_RUNTIME_SOURCE_DIR=$env:COAKKA_NATIVE_EVIDENCE_RUNTIME_SOURCE_DIR" `
      "-D$RequireOption=ON"
    if ($LASTEXITCODE -ne 0) {
      throw "failed to configure $Mode evidence"
    }
    cmake --build $BuildDirectory --config Release --target $EvidenceTarget
    if ($LASTEXITCODE -ne 0) {
      throw "failed to build $Mode evidence"
    }
    $Executable = Join-Path $BuildDirectory "Release\$EvidenceTarget.exe"
    if (-not (Test-Path $Executable)) {
      $Executable = Join-Path $BuildDirectory "$EvidenceTarget.exe"
    }
    if (-not (Test-Path $Executable)) {
      throw "built $Mode evidence executable is missing"
    }
    $RuntimeDll = Get-ChildItem -Path $BuildDirectory -Filter "coakka_runtime_v2.dll" -Recurse | Select-Object -First 1
    if (-not $RuntimeDll) {
      throw "built $Mode runtime DLL is missing"
    }
    $env:PATH = "$($RuntimeDll.DirectoryName);$env:PATH"
    $env:COAKKA_EVIDENCE_EXECUTION_PATH = "core-source"
    Write-EvidenceStatus "starting native runtime evidence mode=$Mode path=core-source platform=$Platform"
    if ($IsOwnerAware) {
      & $Executable --owner-aware
    } else {
      & $Executable
    }
    exit $LASTEXITCODE
  }
  if ($IsFileLane -or $IsStreamLane -or $Mode -in @("race", "hot-reload")) {
    $RuntimeVersion = "2.5.0"
    $RuntimeRelease = "2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a"
    $RuntimeSha256 = "1a7c33f167e03554e7eaa137b92d87f697c8dcec7186fa42b12b70460006055c"
    $RuntimeRawCommit = "bd216a1ff02917e372ca18bb148a188c00fa2c51"
    $RuntimeArtifact = "coakka-runtime-native-v2-$RuntimeVersion.tar.gz"
    $RuntimeRelativePath = "runtime/native/releases/$RuntimeRelease/$RuntimeArtifact"
    $RuntimeArchive = Join-Path $TemporaryRoot "artifacts\$RuntimeArtifact"
    $RuntimeExtractDirectory = Join-Path $TemporaryRoot "runtime"
    $BuildDirectory = Join-Path $TemporaryRoot "build"
    New-Item -ItemType Directory -Force -Path (Split-Path $RuntimeArchive) | Out-Null
    New-Item -ItemType Directory -Force -Path $RuntimeExtractDirectory | Out-Null

    $PublishRoot = if ($env:COAKKA_PUBLISH_ROOT) {
      $env:COAKKA_PUBLISH_ROOT
    } else {
      Join-Path $RepositoryRoot "..\coakka-publish"
    }
    $LocalRuntimeArchive = Join-Path $PublishRoot $RuntimeRelativePath
    if (Test-Path $LocalRuntimeArchive) {
      Copy-Item $LocalRuntimeArchive $RuntimeArchive
    } else {
      $RuntimeUrl = "https://raw.githubusercontent.com/phuong-tran/coakka-publish/$RuntimeRawCommit/$RuntimeRelativePath"
      Write-EvidenceStatus "downloading runtime package generation=$RuntimeRelease platform=$Platform"
      Invoke-WebRequest -Uri $RuntimeUrl -OutFile $RuntimeArchive
    }
    $ActualRuntimeSha256 = (Get-FileHash -Algorithm SHA256 -Path $RuntimeArchive).Hash.ToLowerInvariant()
    if ($ActualRuntimeSha256 -ne $RuntimeSha256) {
      throw "published runtime checksum mismatch for $RuntimeRelativePath"
    }
    & $SystemTar -C $RuntimeExtractDirectory -xzf $RuntimeArchive
    if ($LASTEXITCODE -ne 0) {
      throw "failed to extract published runtime package"
    }
    $RuntimePackageRoot = Join-Path $RuntimeExtractDirectory "coakka-runtime-native-v2-$RuntimeVersion"
    $ConfigureArguments = @(
      "-S", $ScriptDirectory,
      "-B", $BuildDirectory,
      "-A", $(if ($Platform -eq "windows-aarch64") { "ARM64" } else { "x64" }),
      "-DCMAKE_PREFIX_PATH=$RuntimePackageRoot"
    )
    if ($IsFileLane) {
      $ConfigureArguments += "-DCOAKKA_NATIVE_EVIDENCE_REQUIRE_FILE_LANE=ON"
    } elseif ($IsStreamLane) {
      $ConfigureArguments += "-DCOAKKA_NATIVE_EVIDENCE_REQUIRE_STREAM_LANE=ON"
    }
    cmake @ConfigureArguments
    if ($LASTEXITCODE -ne 0) {
      throw "failed to configure concurrency evidence"
    }
    $EvidenceTarget = if ($IsFileLane) {
      "coakka_runtime_v2_file_lane_evidence"
    } elseif ($IsStreamLane) {
      "coakka_runtime_v2_stream_lane_evidence"
    } else {
      "coakka_runtime_v2_concurrency_evidence"
    }
    cmake --build $BuildDirectory --config Release --target $EvidenceTarget
    if ($LASTEXITCODE -ne 0) {
      throw "failed to build $Mode evidence"
    }
    $Executable = Join-Path $BuildDirectory "Release\$EvidenceTarget.exe"
    if (-not (Test-Path $Executable)) {
      $Executable = Join-Path $BuildDirectory "$EvidenceTarget.exe"
    }
    if (-not (Test-Path $Executable)) {
      throw "built concurrency evidence executable is missing"
    }
    $NativePath = Join-Path $RuntimePackageRoot "native\$Platform"
    $env:PATH = "$NativePath;$env:PATH"
    $env:COAKKA_EVIDENCE_EXECUTION_PATH = "source"
    Write-EvidenceStatus "starting native runtime evidence mode=$Mode path=source platform=$Platform runtime=$RuntimeVersion"
    if ($IsOwnerAware) {
      & $Executable --owner-aware
    } elseif ($IsFileLane -or $IsStreamLane) {
      & $Executable
    } else {
      & $Executable $Mode @EvidenceArguments
    }
    exit $LASTEXITCODE
  }
  $ExpectedSha256 = if ($Platform -eq "windows-aarch64") {
    "3521ca0e83f86d140e19998452c2e4326b45bb03929e097c87acbb3cecbd5d89"
  } else {
    "03c91235ccaab00d77ba0c192fb893dd14eac056efdb8ee17fbcdc4cfadd701e"
  }
  $ArtifactName = "coakka-runtime-native-evidence-v2-$EvidenceVersion-$Platform.zip"
  $ArtifactRelativePath = "runtime/evidence/native/releases/$EvidenceRelease/$ArtifactName"
  $ArchivePath = Join-Path $TemporaryRoot "artifacts\$ArtifactName"
  $ExtractDirectory = Join-Path $TemporaryRoot "evidence"
  New-Item -ItemType Directory -Force -Path (Split-Path $ArchivePath) | Out-Null
  New-Item -ItemType Directory -Force -Path $ExtractDirectory | Out-Null

  $PublishRoot = if ($env:COAKKA_PUBLISH_ROOT) {
    $env:COAKKA_PUBLISH_ROOT
  } else {
    Join-Path $RepositoryRoot "..\coakka-publish"
  }
  $LocalArchive = Join-Path $PublishRoot $ArtifactRelativePath
  if (Test-Path $LocalArchive) {
    Copy-Item $LocalArchive $ArchivePath
  } else {
    $ArtifactUrl = "https://raw.githubusercontent.com/phuong-tran/coakka-publish/main/$ArtifactRelativePath"
    Write-EvidenceStatus "downloading published native evidence runner platform=$Platform"
    Invoke-WebRequest -Uri $ArtifactUrl -OutFile $ArchivePath
  }

  $ActualSha256 = (Get-FileHash -Algorithm SHA256 -Path $ArchivePath).Hash.ToLowerInvariant()
  if ($ActualSha256 -ne $ExpectedSha256) {
    throw "published native evidence checksum mismatch for $ArtifactRelativePath"
  }

  Expand-Archive -Path $ArchivePath -DestinationPath $ExtractDirectory
  $PackageRoot = Join-Path $ExtractDirectory "coakka-runtime-native-evidence-v2-$EvidenceVersion-$Platform"
  $Executable = Join-Path $PackageRoot "bin\coakka-runtime-native-evidence.exe"
  if (-not (Test-Path $Executable)) {
    throw "published native evidence executable is missing"
  }

  Write-EvidenceStatus "starting native runtime evidence mode=$Mode path=prebuilt platform=$Platform"
  $env:COAKKA_EVIDENCE_EXECUTION_PATH = "prebuilt"
  & $Executable $Mode @EvidenceArguments
  exit $LASTEXITCODE
} catch {
  [Console]::Error.WriteLine("[coakka-sample] $($_.Exception.Message)")
  exit 1
} finally {
  if ($CandidateAdmission -and (Test-Path -LiteralPath $CandidateAdmission)) {
    Remove-Item -LiteralPath $CandidateAdmission -Recurse -Force
  }
  for ($Attempt = 0; $Attempt -lt 5 -and (Test-Path $TemporaryRoot); $Attempt++) {
    try {
      Remove-Item -Recurse -Force $TemporaryRoot -ErrorAction Stop
    } catch {
      if ($Attempt -eq 4) {
        [Console]::Error.WriteLine("[coakka-sample] temporary cleanup deferred: $TemporaryRoot")
      } else {
        Start-Sleep -Milliseconds 200
      }
    }
  }
}
