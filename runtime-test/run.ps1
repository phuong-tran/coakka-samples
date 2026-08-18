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

function Write-EvidenceStatus([string]$Message) {
  [Console]::Error.WriteLine("[coakka-sample] $Message")
}

try {
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
    if (-not ($IsFileLane -or $IsStreamLane) -and $Platform -ne "windows-x86_64") {
      throw "runtime 2.3.0 concurrency evidence is not published for $Platform"
    }
    if ($IsFileLane -or $IsStreamLane) {
      $RuntimeVersion = "2.5.0"
      $RuntimeRelease = "2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a"
      $RuntimeSha256 = "1a7c33f167e03554e7eaa137b92d87f697c8dcec7186fa42b12b70460006055c"
      $RuntimeRawCommit = "53ade103faf819f180c6cb518d5d4d8c4e855861"
    } else {
      $RuntimeVersion = "2.3.0"
      $RuntimeRelease = "2.3.0+a83ab412"
      $RuntimeSha256 = "7d1c58a17c0b24b547fe6339886387d4deb3f778c987970db2f85b0d9921e1ab"
      $RuntimeRawCommit = "621f36edcb489c5151348986e76b7ef42893f5e7"
    }
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
