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
