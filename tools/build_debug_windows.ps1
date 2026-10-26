$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$logDirectory = Join-Path $projectRoot 'build-diagnostics'
$logPath = Join-Path $logDirectory "assembleDebug-$timestamp.log"
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null

Write-Host 'KAAM Android build preflight' -ForegroundColor Cyan
Write-Host "Project: $projectRoot"

# Prefer Android Studio's bundled JBR when available; it avoids depending on a
# separately installed Java and is supported by current Android Gradle Plugin.
$studioJbrCandidates = @(
    (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'),
    (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr'),
    (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio Preview\jbr'),
    'C:\Android\Android Studio\jbr'
) | Where-Object { $_ -and (Test-Path (Join-Path $_ 'bin\java.exe')) }
if ($studioJbrCandidates.Count -gt 0) {
    $env:JAVA_HOME = $studioJbrCandidates[0]
    $env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
    Write-Host "Using Android Studio JBR: $env:JAVA_HOME"
}

$javaCommand = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCommand) {
    throw 'Java was not found. Install/update Android Studio, then run this script again.'
}
$javaOutput = (& java -version 2>&1 | Out-String)
Write-Host $javaOutput.Trim()
$versionMatch = [regex]::Match($javaOutput, 'version\s+"?(\d+)(?:\.(\d+))?')
if ($versionMatch.Success) {
    $javaMajor = [int]$versionMatch.Groups[1].Value
    if ($javaMajor -eq 1 -and $versionMatch.Groups[2].Success) {
        $javaMajor = [int]$versionMatch.Groups[2].Value
    }
    if ($javaMajor -lt 17) {
        throw "Java $javaMajor is too old for this project. Use JDK 17 or newer."
    }
}

$sdkRoot = $env:ANDROID_HOME
if (-not $sdkRoot) { $sdkRoot = $env:ANDROID_SDK_ROOT }
if (-not $sdkRoot) {
    $localProperties = Join-Path $projectRoot 'local.properties'
    if (Test-Path $localProperties) {
        $line = Get-Content $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($line) { $sdkRoot = ($line -replace '^sdk\.dir=', '').Replace('\\', '\') }
    }
}
if (-not $sdkRoot) {
    $sdkCandidates = @(
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
        'C:\Android\Sdk'
    ) | Where-Object { $_ -and (Test-Path $_) }
    if ($sdkCandidates.Count -gt 0) { $sdkRoot = $sdkCandidates[0] }
}
if ($sdkRoot -and (Test-Path $sdkRoot)) {
    $env:ANDROID_HOME = $sdkRoot
    $env:ANDROID_SDK_ROOT = $sdkRoot
    Write-Host "Android SDK: $sdkRoot"
    if (-not (Test-Path (Join-Path $sdkRoot 'platforms\android-37\android.jar'))) {
        Write-Warning 'Android SDK Platform 37 is missing. Install Android API 37 in Android Studio > SDK Manager.'
    }
    if (-not (Test-Path (Join-Path $sdkRoot 'build-tools\36.0.0'))) {
        Write-Warning 'Android SDK Build-Tools 36.0.0 is missing; install it in Android Studio > SDK Manager if Gradle requests it.'
    }
} else {
    Write-Warning 'Android SDK path was not found. Set ANDROID_HOME or open this project in Android Studio and install Android API 37.'
}

$gradleWrapper = Join-Path $projectRoot 'gradlew.bat'
if (-not (Test-Path $gradleWrapper)) { throw "Gradle wrapper is missing: $gradleWrapper" }
Write-Host "Build log: $logPath"
Write-Host 'Running :app:assembleDebug (first run needs network access to download Gradle/dependencies).' -ForegroundColor Yellow

# The wrapper prefers the direct official distribution host. If only that host
# fails DNS while the standard Gradle service host resolves, temporarily switch
# to the service URL for this build and restore the original file afterward.
$wrapperPropertiesPath = Join-Path $projectRoot 'gradle\wrapper\gradle-wrapper.properties'
$originalWrapperProperties = [System.IO.File]::ReadAllText($wrapperPropertiesPath)
$usingTemporaryDistributionFallback = $false
$directHostAddresses = @()
$serviceHostAddresses = @()
try { $directHostAddresses = [System.Net.Dns]::GetHostAddresses('downloads.gradle.org') } catch { }
if ($directHostAddresses.Count -eq 0) {
    try { $serviceHostAddresses = [System.Net.Dns]::GetHostAddresses('services.gradle.org') } catch { }
    if ($serviceHostAddresses.Count -gt 0) {
        $directUrl = 'distributionUrl=https\://downloads.gradle.org/distributions/gradle-9.6.0-bin.zip'
        $fallbackUrl = 'distributionUrl=https\://services.gradle.org/distributions/gradle-9.6.0-bin.zip'
        if ($originalWrapperProperties.Contains($directUrl)) {
            $temporaryWrapperProperties = $originalWrapperProperties.Replace($directUrl, $fallbackUrl)
            [System.IO.File]::WriteAllText($wrapperPropertiesPath, $temporaryWrapperProperties, [System.Text.UTF8Encoding]::new($false))
            $usingTemporaryDistributionFallback = $true
            Write-Warning 'downloads.gradle.org did not resolve, but services.gradle.org does. Using the standard host for this build only.'
        }
    } else {
        Write-Warning 'Neither downloads.gradle.org nor services.gradle.org resolved in DNS; the Gradle wrapper will record the network failure.'
    }
}

Push-Location $projectRoot
try {
    & $gradleWrapper ':app:assembleDebug' '--stacktrace' '--no-daemon' '--console=plain' 2>&1 | Tee-Object -FilePath $logPath
    $buildExitCode = $LASTEXITCODE
} finally {
    try {
        if ($usingTemporaryDistributionFallback) {
            [System.IO.File]::WriteAllText($wrapperPropertiesPath, $originalWrapperProperties, [System.Text.UTF8Encoding]::new($false))
            Write-Host 'Restored the project Gradle wrapper URL after the build attempt.'
        }
    } finally {
        Pop-Location
    }
}
if ($buildExitCode -ne 0) {
    Write-Host "BUILD FAILED (exit $buildExitCode). Log saved at: $logPath" -ForegroundColor Red
    Write-Host 'Send that log file back so the first real Gradle/compiler error can be fixed.'
    exit $buildExitCode
}
$apkPath = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (Test-Path $apkPath) {
    Write-Host "BUILD SUCCEEDED: $apkPath" -ForegroundColor Green
} else {
    Write-Warning "Gradle succeeded but the expected APK was not found at $apkPath"
}
