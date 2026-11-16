Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Load the actual helpers without running the script's emulator/build actions.
$backend = Join-Path (Split-Path -Parent $PSScriptRoot) 'android.ps1'
$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile($backend, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count -gt 0) { throw 'android.ps1 contains syntax errors.' }
$helpers = @('Invoke-Native', 'Assert-Tool', 'Get-AvdNames', 'Get-EmulatorSerial', 'Wait-Emulator')
foreach ($definition in $ast.FindAll({ param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] }, $true)) {
    if ($helpers -contains $definition.Name) {
        . ([scriptblock]::Create($definition.Extent.Text))
    }
}

function Clear-PreviousExitCode {
    Remove-Variable -Name LASTEXITCODE -Scope Global -ErrorAction SilentlyContinue
    Remove-Variable -Name LASTEXITCODE -Scope Script -ErrorAction SilentlyContinue
}

# Some command wrappers return successfully without defining LASTEXITCODE.
function Command-WithoutExitCode { }
Clear-PreviousExitCode
Invoke-Native 'Command-WithoutExitCode' @()
Write-Host 'PASS: fresh session without LASTEXITCODE'

# An initialized default must never hide a real native command failure.
Clear-PreviousExitCode
$failedAsExpected = $false
try {
    Invoke-Native $env:ComSpec @('/d', '/c', 'exit 7')
} catch {
    if ($_.Exception.Message -notmatch 'exit code 7') { throw }
    $failedAsExpected = $true
}
if (-not $failedAsExpected) { throw 'Native exit code 7 was incorrectly accepted.' }
Write-Host 'PASS: real nonzero exit code remains an error'

Invoke-Native $env:ComSpec @('/d', '/c', 'exit 0')
Write-Host 'PASS: native success after failure'

# Exercise the query helpers with a command wrapper that leaves the exit code unset.
$testDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ('costly-exit-' + [guid]::NewGuid())
$null = New-Item -ItemType Directory -Path $testDirectory
$stub = Join-Path $testDirectory 'android-tool.ps1'
try {
    @'
if ($args[0] -eq '-list-avds') {
    'Test_AVD'
} elseif ($args[0] -eq 'devices') {
    "emulator-5554`tdevice"
} elseif ($args -contains 'getprop') {
    '1'
} else {
    'Test_AVD'
    'OK'
}
'@ | Set-Content -LiteralPath $stub -Encoding ASCII
    $emulatorPath = $stub
    $adbPath = $stub
    $BootTimeoutSeconds = 1
    Clear-PreviousExitCode
    $names = @(Get-AvdNames)
    if ($names.Count -ne 1 -or $names[0] -ne 'Test_AVD') { throw 'AVD query failed.' }
    Clear-PreviousExitCode
    if ((Get-EmulatorSerial 'Test_AVD') -ne 'emulator-5554') { throw 'Serial query failed.' }
    Clear-PreviousExitCode
    if ((Wait-Emulator 'Test_AVD') -ne 'emulator-5554') { throw 'Boot query failed.' }
    Write-Host 'PASS: AVD, serial, and boot queries without LASTEXITCODE'
} finally {
    # Only remove the single file and unique empty directory created by this test.
    Remove-Item -LiteralPath $stub -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $testDirectory -ErrorAction SilentlyContinue
}
