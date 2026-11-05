param(
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$RuntimeTestArguments
)

$SamplesRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$PublishRoot = if ($env:COAKKA_PUBLISH_ROOT) {
  $env:COAKKA_PUBLISH_ROOT
} else {
  Join-Path $SamplesRoot '../coakka-publish'
}
$RuntimeTest = Join-Path $PublishRoot 'coakka-runtime/coakka-runtime-test/run.ps1'
if (-not (Test-Path -LiteralPath $RuntimeTest -PathType Leaf)) {
  throw 'Clone coakka-publish beside Samples or set COAKKA_PUBLISH_ROOT. See https://github.com/phuong-tran/coakka-publish/tree/main/coakka-runtime/coakka-runtime-test'
}
& $RuntimeTest @RuntimeTestArguments
exit $LASTEXITCODE
