# In the name of God, the Most Gracious, the Most Merciful
<#
.SYNOPSIS
    Builds the Herz debug app, installs it on a USB-connected phone, launches it and shows its log.

.EXAMPLE
    .\scripts\run-on-phone.ps1
    .\scripts\run-on-phone.ps1 -NoBuild
    .\scripts\run-on-phone.ps1 -Serial 4hjnvwwgayv8q4b6 -NoLogcat
    .\scripts\run-on-phone.ps1 -WaitForDebugger
#>
param(
    # Device serial from `adb devices`. Needed only when more than one phone is connected.
    [string]$Serial,
    # Install the APK from the last build without building again.
    [switch]$NoBuild,
    # Exit after launching instead of following the app log.
    [switch]$NoLogcat,
    # Start the app paused until a debugger attaches.
    [switch]$WaitForDebugger,
    # Use fewer Gradle workers and compile Kotlin in the Gradle process. Helps on low-RAM machines.
    [switch]$LowMemory
)

$ErrorActionPreference = 'Stop'
$AppId = 'com.raghim.herz'
$Activity = "$AppId/.MainActivity"
$AndroidDir = Split-Path -Parent $PSScriptRoot

function Fail([string]$message) {
    Write-Host "ERROR: $message" -ForegroundColor Red
    exit 1
}

function Step([string]$message) {
    Write-Host "==> $message" -ForegroundColor Cyan
}

function Find-Adb {
    $sdkDirs = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
    $localProps = Join-Path $AndroidDir 'local.properties'
    if (Test-Path $localProps) {
        $line = Select-String -Path $localProps -Pattern '^sdk\.dir=(.+)$' | Select-Object -First 1
        if ($line) { $sdkDirs += ($line.Matches[0].Groups[1].Value -replace '\\:', ':' -replace '\\\\', '\') }
    }
    $sdkDirs += Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    foreach ($dir in $sdkDirs | Where-Object { $_ }) {
        $adb = Join-Path $dir 'platform-tools\adb.exe'
        if (Test-Path $adb) { return $adb }
    }
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    Fail 'adb not found. Install "Android SDK Platform-Tools" from the Android Studio SDK Manager.'
}

function Select-Phone([string]$adb) {
    & $adb start-server | Out-Null
    $devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\S' } | ForEach-Object {
        $parts = $_ -split '\s+'
        [pscustomobject]@{ Serial = $parts[0]; State = $parts[1] }
    }
    $phones = @($devices | Where-Object { $_.Serial -notlike 'emulator-*' })

    if ($Serial) {
        $phone = $phones | Where-Object Serial -eq $Serial
        if (-not $phone) { Fail "Device $Serial is not connected. Connected: $($phones.Serial -join ', ')" }
        $phones = @($phone)
    }

    if ($phones.Count -eq 0) {
        Fail @'
No phone found. Check that:
  - the USB cable supports data (not charge-only),
  - the phone's USB mode is "File transfer",
  - Developer options > USB debugging is on.
'@
    }
    if ($phones.Count -gt 1) {
        Fail "More than one phone connected. Pick one with -Serial: $($phones.Serial -join ', ')"
    }

    $phone = $phones[0]
    switch ($phone.State) {
        'device' { return $phone.Serial }
        'unauthorized' { Fail 'The phone has not allowed this computer. Unlock it, accept "Allow USB debugging?", then run again.' }
        default { Fail "The phone is in state '$($phone.State)'. Reconnect the cable and run again." }
    }
}

$adb = Find-Adb
$serial = Select-Phone $adb
$model = (& $adb -s $serial shell getprop ro.product.model).Trim()
$sdk = (& $adb -s $serial shell getprop ro.build.version.sdk).Trim()
Step "Phone: $model (serial $serial, API $sdk)"

Push-Location $AndroidDir
try {
    if (-not $NoBuild) {
        Step 'Building debug APK'
        $gradleArgs = @('assembleDebug', '--console=plain')
        if ($LowMemory) { $gradleArgs += @('--max-workers=2', '-Pkotlin.compiler.execution.strategy=in-process') }
        & .\gradlew.bat @gradleArgs
        if ($LASTEXITCODE -ne 0) { Fail 'Build failed. See the Gradle output above.' }
    }

    $apk = Get-ChildItem -Path 'app\build\outputs\apk' -Recurse -Filter '*debug*.apk' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $apk) { Fail 'No debug APK found. Run without -NoBuild.' }

    Step "Installing $($apk.Name)"
    $install = & $adb -s $serial install -r -t $apk.FullName 2>&1 | Out-String
    Write-Host $install.Trim()
    if ($install -notmatch 'Success') {
        if ($install -match 'INSTALL_FAILED_USER_RESTRICTED') {
            Fail @'
Xiaomi blocked the install. On the phone:
  1. Settings > Additional settings > Developer options.
  2. Turn on "Install via USB" (needs a Xiaomi account and SIM / mobile data on some models).
  3. Also turn on "USB debugging (Security settings)" if it exists.
  4. Run this script again and tap "Install" if the phone asks.
'@
        }
        if ($install -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
            Fail "An app with a different signature is installed. Remove it with: & '$adb' -s $serial uninstall $AppId"
        }
        Fail 'Install failed. See the message above.'
    }

    Step 'Launching Herz'
    & $adb -s $serial logcat -c
    & $adb -s $serial shell input keyevent KEYCODE_WAKEUP | Out-Null
    $startArgs = @('shell', 'am', 'start', '-n', $Activity)
    if ($WaitForDebugger) { $startArgs = @('shell', 'am', 'start', '-D', '-n', $Activity) }
    & $adb -s $serial @startArgs
    if ($WaitForDebugger) {
        Write-Host 'The app waits for a debugger. Attach from Android Studio: Run > Attach Debugger to Android Process.' -ForegroundColor Yellow
    }

    if ($NoLogcat) { return }

    $appPid = $null
    foreach ($i in 1..20) {
        $appPid = (& $adb -s $serial shell pidof $AppId 2>$null | Out-String).Trim()
        if ($appPid) { break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $appPid) { Fail 'The app did not start. Check the phone screen.' }

    Step "Showing app log (pid $appPid). Press Ctrl+C to stop."
    & $adb -s $serial logcat --pid=$appPid -v color -v time
}
finally {
    Pop-Location
}
