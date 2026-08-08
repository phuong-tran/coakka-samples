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
$TemporaryRoot = Join-Path ([IO.Path]::GetTempPath()) (
  "coakka-native-evidence-{0}-{1}" -f $PID, [Guid]::NewGuid().ToString("N"))

function Write-EvidenceStatus([string]$Message) {
  [Console]::Error.WriteLine("[coakka-sample] $Message")
}

try {
  $Architecture = [Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString().ToLowerInvariant()
  $Platform = if ($Architecture -eq "arm64") { "windows-aarch64" } else { "windows-x86_64" }
  if ($Mode -in @("race", "hot-reload")) {
    if ($Platform -ne "windows-x86_64") {
      throw "runtime 1.4.1 concurrency evidence is not published for $Platform"
    }
    $RuntimeVersion = "1.4.1"
    $RuntimeRelease = "1.4.1+9e02a51d"
    $RuntimeSha256 = "ef31cd8bc709bd71d62dab0497f2513990f9023bda5e128631842ece5360394f"
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
      $RuntimeUrl = "https://raw.githubusercontent.com/phuong-tran/coakka-publish/main/$RuntimeRelativePath"
      Write-EvidenceStatus "downloading runtime package generation=$RuntimeRelease platform=$Platform"
      Invoke-WebRequest -Uri $RuntimeUrl -OutFile $RuntimeArchive
    }
    $ActualRuntimeSha256 = (Get-FileHash -Algorithm SHA256 -Path $RuntimeArchive).Hash.ToLowerInvariant()
    if ($ActualRuntimeSha256 -ne $RuntimeSha256) {
      throw "published runtime checksum mismatch for $RuntimeRelativePath"
    }
    tar -C $RuntimeExtractDirectory -xzf $RuntimeArchive
    if ($LASTEXITCODE -ne 0) {
      throw "failed to extract published runtime package"
    }
    $RuntimePackageRoot = Join-Path $RuntimeExtractDirectory "coakka-runtime-native-v2-$RuntimeVersion"
    cmake -S $ScriptDirectory -B $BuildDirectory "-DCMAKE_PREFIX_PATH=$RuntimePackageRoot"
    if ($LASTEXITCODE -ne 0) {
      throw "failed to configure concurrency evidence"
    }
    cmake --build $BuildDirectory --config Release --target coakka_runtime_v2_concurrency_evidence
    if ($LASTEXITCODE -ne 0) {
      throw "failed to build concurrency evidence"
    }
    $Executable = Join-Path $BuildDirectory "Release\coakka_runtime_v2_concurrency_evidence.exe"
    if (-not (Test-Path $Executable)) {
      $Executable = Join-Path $BuildDirectory "coakka_runtime_v2_concurrency_evidence.exe"
    }
    if (-not (Test-Path $Executable)) {
      throw "built concurrency evidence executable is missing"
    }
    $NativePath = Join-Path $RuntimePackageRoot "native\$Platform"
    $env:PATH = "$NativePath;$env:PATH"
    $env:COAKKA_EVIDENCE_EXECUTION_PATH = "source"
    Write-EvidenceStatus "starting native runtime evidence mode=$Mode path=source platform=$Platform runtime=$RuntimeVersion"
    & $Executable $Mode @EvidenceArguments
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
