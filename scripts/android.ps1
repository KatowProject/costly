[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Start', 'Run', 'Build', 'BuildRun', 'List', 'Stop', 'Check')]
    [string]$Action,
    [string]$Avd = '',
    [ValidateRange(1, 600)]
    [int]$BootTimeoutSeconds = 180
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$apkPath = Join-Path $projectDirectory 'app\build\outputs\apk\debug\app-debug.apk'

function Find-AndroidSdk {
    $candidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
    $properties = Join-Path $projectDirectory 'local.properties'
    if (Test-Path -LiteralPath $properties) {
        foreach ($line in Get-Content -LiteralPath $properties) {
            if ($line -match '^\s*sdk\.dir\s*=(.*)$') {
                $candidates += $Matches[1].Trim().Replace('\:', ':').Replace('\\', '\').Replace('\ ', ' ')
                break
            }
        }
    }
    if ($env:LOCALAPPDATA) {
        $candidates += Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    }
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate -PathType Container)) {
            return (Resolve-Path -LiteralPath $candidate).ProviderPath
        }
    }
    throw 'Android SDK not found. Check sdk.dir in local.properties or ANDROID_HOME.'
}

function Find-Java {
    # Prefer the complete Android Studio JDK over a VS Code extension's stripped JRE.
    $candidates = @($env:COSTLY_JAVA_HOME)
    if ($env:ProgramFiles) {
        $candidates += Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    }
    $candidates += $env:JAVA_HOME
    foreach ($candidate in $candidates) {
        if ($candidate -and
            (Test-Path -LiteralPath (Join-Path $candidate 'bin\java.exe')) -and
            (Test-Path -LiteralPath (Join-Path $candidate 'bin\jlink.exe'))) {
            return (Resolve-Path -LiteralPath $candidate).ProviderPath
        }
    }
    throw 'Complete JDK not found. Set COSTLY_JAVA_HOME to the Android Studio JDK directory containing bin\jlink.exe.'
}

function Assert-Tool([string]$Path, [string]$Description) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Description not found: $Path. Install the component via Android SDK Manager."
    }
}

function Invoke-Native([string]$Executable, [string[]]$Arguments) {
    # Native commands update the global automatic variable, even from a function.
    # A local default would hide real failures; initialize and read the global scope.
    $global:LASTEXITCODE = 0
    & $Executable @Arguments | Out-Host
    $exitCode = $global:LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "Command failed (exit code $exitCode): $Executable"
    }
}

function Get-AvdNames {
    Assert-Tool $emulatorPath 'Android Emulator'
    $global:LASTEXITCODE = 0
    $output = @(& $emulatorPath -list-avds)
    if ($global:LASTEXITCODE -ne 0) { throw 'Unable to read emulator list.' }
    # SDK diagnostic lines are not AVD names.
    @($output | ForEach-Object { $_.Trim() } | Where-Object { $_ -match '^[A-Za-z0-9_.-]+$' })
}

function Select-Avd {
    $names = @(Get-AvdNames)
    if ($names.Count -eq 0) {
        throw 'No AVD found. Create an emulator via Android Studio > Device Manager first.'
    }
    $requested = $Avd
    if (-not $requested) { $requested = $env:COSTLY_AVD }
    if (-not $requested -and $names -contains 'Medium_Phone_API_37.0') {
        $requested = 'Medium_Phone_API_37.0'
    }
    if (-not $requested -and $names.Count -eq 1) { $requested = $names[0] }
    if (-not $requested -or $names -notcontains $requested) {
        throw "Select an emulator with -Avd NAME. Available emulators: $($names -join ', ')"
    }
    return $requested
}

function Get-EmulatorSerial([string]$Name) {
    $global:LASTEXITCODE = 0
    $devices = @(& $adbPath devices)
    if ($global:LASTEXITCODE -ne 0) { throw 'ADB cannot read devices.' }
    foreach ($line in $devices) {
        if ($line -match '^(emulator-\d+)\s+device(?:\s|$)') {
            $serial = $Matches[1]
            $global:LASTEXITCODE = 0
            $answer = @(& $adbPath -s $serial emu avd name)
            if ($global:LASTEXITCODE -eq 0 -and $answer -contains $Name) { return $serial }
        }
    }
    return $null
}

function Wait-Emulator([string]$Name) {
    Write-Host "Waiting for Android to boot (up to $BootTimeoutSeconds seconds)..."
    $deadline = [DateTime]::UtcNow.AddSeconds($BootTimeoutSeconds)
    do {
        $serial = Get-EmulatorSerial $Name
        if ($serial) {
            $global:LASTEXITCODE = 0
            $boot = @(& $adbPath -s $serial shell getprop sys.boot_completed)
            if ($global:LASTEXITCODE -eq 0 -and ($boot -join '').Trim() -eq '1') {
                Write-Host "Emulator ready: $Name ($serial)"
                return $serial
            }
        }
        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Emulator is not ready. Check the emulator window and retry. Timeout can be adjusted with -BootTimeoutSeconds 300."
}

function Start-CostlyEmulator([string]$Name) {
    $serial = Get-EmulatorSerial $Name
    if ($serial) {
        Write-Host "Using running emulator: $Name ($serial)."
        $headless = @(Get-CimInstance Win32_Process -Filter "Name = 'qemu-system-x86_64.exe' OR Name = 'emulator.exe'" -ErrorAction SilentlyContinue |
            Where-Object { $_.CommandLine -and $_.CommandLine.Contains($Name) -and $_.CommandLine -match '(?:^|\s)-no-window(?:\s|$)' })
        if ($headless.Count -gt 0) {
            Write-Warning 'Emulator is running headlessly. To display it, run stop-emulator.cmd then start-emulator.cmd.'
        }
    } else {
        Write-Host "Starting emulator: $Name"
        # The requested emulator is an interactive window, independent of Android Studio.
        Start-Process -FilePath $emulatorPath -ArgumentList @('-avd', $Name) | Out-Null
    }
    return (Wait-Emulator $Name)
}

function Build-Costly {
    $javaPath = Find-Java
    $env:JAVA_HOME = $javaPath
    if (-not $env:GRADLE_USER_HOME -and $env:USERPROFILE) {
        $env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
    }
    Write-Host "JDK: $javaPath"
    Write-Host 'Building debug APK...'
    Push-Location -LiteralPath $projectDirectory
    try {
        Invoke-Native (Join-Path $projectDirectory 'gradlew.bat') @(
            ':app:assembleDebug', '--console=plain', '--no-configuration-cache',
            '-Pkotlin.compiler.execution.strategy=in-process',
            "-Porg.gradle.java.installations.paths=$javaPath",
            '-Porg.gradle.java.installations.auto-detect=false'
        )
    } finally {
        Pop-Location
    }
    if (-not (Test-Path -LiteralPath $apkPath)) { throw "APK not found: $apkPath" }
    Write-Host "APK ready: $apkPath"
}

function Install-Costly([string]$Name) {
    if (-not (Test-Path -LiteralPath $apkPath)) {
        throw 'APK is not available yet. Run scripts\build-and-run.cmd first.'
    }
    $serial = Start-CostlyEmulator $Name
    Write-Host 'Installing Costly APK...'
    Invoke-Native $adbPath @('-s', $serial, 'install', '-r', $apkPath)
    Write-Host 'Launching Costly...'
    Invoke-Native $adbPath @('-s', $serial, 'shell', 'am', 'start', '-W', '-n', 'com.katowproject.costly/.MainActivity')
}

try {
    if (-not $env:ANDROID_USER_HOME -and $env:USERPROFILE) {
        $env:ANDROID_USER_HOME = Join-Path $env:USERPROFILE '.android'
    }
    $sdkPath = Find-AndroidSdk
    $adbPath = Join-Path $sdkPath 'platform-tools\adb.exe'
    $emulatorPath = Join-Path $sdkPath 'emulator\emulator.exe'
    Write-Host "Android SDK: $sdkPath"

    if ($Action -eq 'Build') {
        Build-Costly
    } elseif ($Action -eq 'List') {
        Write-Host 'Available emulators:'
        Get-AvdNames | ForEach-Object { Write-Host "  $_" }
    } elseif ($Action -eq 'Check') {
        Assert-Tool $adbPath 'Android Platform Tools (ADB)'
        Assert-Tool $emulatorPath 'Android Emulator'
        Write-Host "JDK: $(Find-Java)"
        Write-Host 'Available emulators:'
        Get-AvdNames | ForEach-Object { Write-Host "  $_" }
        Write-Host "APK available: $(Test-Path -LiteralPath $apkPath)"
        Invoke-Native $adbPath @('devices', '-l')
    } else {
        Assert-Tool $adbPath 'Android Platform Tools (ADB)'
        Assert-Tool $emulatorPath 'Android Emulator'
        $selectedAvd = Select-Avd
        Invoke-Native $adbPath @('start-server')
        if ($Action -eq 'Stop') {
            $serial = Get-EmulatorSerial $selectedAvd
            if ($serial) {
                Write-Host "Stopping emulator $selectedAvd ($serial)..."
                Invoke-Native $adbPath @('-s', $serial, 'emu', 'kill')
                $deadline = [DateTime]::UtcNow.AddSeconds(20)
                do {
                    Start-Sleep -Seconds 1
                    if (-not (Get-EmulatorSerial $selectedAvd)) { break }
                } while ([DateTime]::UtcNow -lt $deadline)
            } else {
                Write-Host "Emulator $selectedAvd is not currently running."
            }
        } elseif ($Action -eq 'Start') {
            $null = Start-CostlyEmulator $selectedAvd
        } elseif ($Action -eq 'Run') {
            Install-Costly $selectedAvd
        } elseif ($Action -eq 'BuildRun') {
            Build-Costly
            Install-Costly $selectedAvd
        }
    }
    exit 0
} catch {
    [Console]::Error.WriteLine("FAILED: $($_.Exception.Message)")
    exit 1
}
