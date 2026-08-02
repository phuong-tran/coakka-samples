param(
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$RuntimeTestArguments
)

$RuntimeTest = Join-Path $PSScriptRoot "..\..\..\runtime-test\run.ps1"
& $RuntimeTest @RuntimeTestArguments
exit $LASTEXITCODE
