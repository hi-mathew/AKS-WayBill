$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

$Version = '1.4.0'

$TargetDir = Join-Path $ProjectRoot 'target'
$InputDir = Join-Path $TargetDir 'package-input'
$RuntimeDir = Join-Path $TargetDir 'runtime-image'
$InstallerDir = Join-Path $TargetDir 'installer'

$Icon = Join-Path $ProjectRoot 'packaging\wasp.ico'

$JavaFxVersion = '21.0.6'
$LocalRepo = 'E:\Projects\Local_Maven_Repository'

$JavaFxBase = Join-Path $LocalRepo "org\openjfx\javafx-base\$JavaFxVersion\javafx-base-$JavaFxVersion-win.jar"
$JavaFxGraphics = Join-Path $LocalRepo "org\openjfx\javafx-graphics\$JavaFxVersion\javafx-graphics-$JavaFxVersion-win.jar"
$JavaFxControls = Join-Path $LocalRepo "org\openjfx\javafx-controls\$JavaFxVersion\javafx-controls-$JavaFxVersion-win.jar"

Write-Host ''
Write-Host '=============================================' -ForegroundColor Cyan
Write-Host ' W.A.S.P - Windows Packaging' -ForegroundColor Cyan
Write-Host '=============================================' -ForegroundColor Cyan
Write-Host ''

# ------------------------------------------------------------
# Validate required tools
# ------------------------------------------------------------

Write-Host 'Checking required tools...' -ForegroundColor Cyan

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'Java was not found in PATH.'
}

if (-not (Get-Command jlink -ErrorAction SilentlyContinue)) {
    throw 'jlink was not found in PATH.'
}

if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    throw 'jpackage was not found in PATH.'
}

if (-not (Test-Path $Icon)) {
    throw "Application icon not found: $Icon"
}

foreach ($File in @(
    $JavaFxBase,
    $JavaFxGraphics,
    $JavaFxControls
)) {
    if (-not (Test-Path $File)) {
        throw "Required JavaFX runtime module not found: $File"
    }
}

# ------------------------------------------------------------
# Clean previous build
# ------------------------------------------------------------

Write-Host ''
Write-Host 'Cleaning previous packaging output...' -ForegroundColor Cyan

if (Test-Path $TargetDir) {
    Remove-Item $TargetDir -Recurse -Force
}

New-Item -ItemType Directory -Force -Path $InputDir | Out-Null
New-Item -ItemType Directory -Force -Path $RuntimeDir | Out-Null
New-Item -ItemType Directory -Force -Path $InstallerDir | Out-Null

# ------------------------------------------------------------
# Build application
# ------------------------------------------------------------

Write-Host ''
Write-Host 'Building W.A.S.P...' -ForegroundColor Cyan

mvn clean package dependency:copy-dependencies `
    '-DincludeScope=runtime' `
    '-DoutputDirectory=target/package-input' `
    '-DskipTests'

if ($LASTEXITCODE -ne 0) {
    throw 'Maven build failed.'
}

$Jar = Join-Path $TargetDir "aks-waybill-desktop-$Version.jar"

if (-not (Test-Path $Jar)) {
    throw "Application JAR not found: $Jar"
}

Write-Host "Application JAR found: $Jar" -ForegroundColor Green

# ------------------------------------------------------------
# Verify JavaFX modules
# ------------------------------------------------------------

Write-Host ''
Write-Host 'JavaFX runtime modules:' -ForegroundColor Cyan
Write-Host "  $JavaFxBase"
Write-Host "  $JavaFxGraphics"
Write-Host "  $JavaFxControls"
Write-Host 'JavaFX module verification passed.' -ForegroundColor Green

# ------------------------------------------------------------
# Build JavaFX runtime image
# ------------------------------------------------------------

Write-Host ''
Write-Host 'Creating JavaFX runtime image...' -ForegroundColor Cyan

$TempJavaFxModules = Join-Path $TargetDir 'javafx-modules'

New-Item -ItemType Directory -Force -Path $TempJavaFxModules | Out-Null

Copy-Item $JavaFxBase $TempJavaFxModules -Force
Copy-Item $JavaFxGraphics $TempJavaFxModules -Force
Copy-Item $JavaFxControls $TempJavaFxModules -Force

Write-Host ''
Write-Host 'Running jlink...' -ForegroundColor Cyan

jlink `
    --module-path "$env:JAVA_HOME\jmods;$TempJavaFxModules" `
    --add-modules "java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.prefs,java.sql,java.xml,jdk.crypto.ec,jdk.unsupported,jdk.xml.dom,javafx.base,javafx.graphics,javafx.controls" `
    --bind-services `
    --strip-debug `
    --no-header-files `
    --no-man-pages `
    --compress=2 `
    --output $RuntimeDir

if ($LASTEXITCODE -ne 0) {
    throw 'jlink failed.'
}

if (-not (Test-Path (Join-Path $RuntimeDir 'bin'))) {
    throw 'jlink completed but runtime image is invalid.'
}

Write-Host ''
Write-Host 'Runtime image created successfully.' -ForegroundColor Green

# ------------------------------------------------------------
# Copy application JAR
# ------------------------------------------------------------

Write-Host ''
Write-Host 'Preparing application files...' -ForegroundColor Cyan

Copy-Item $Jar $InputDir -Force

# Copy all runtime dependencies except JavaFX.
Get-ChildItem $InputDir -Filter '*.jar' | ForEach-Object {

    if ($_.Name -like 'javafx-*-21.0.6*.jar') {
        Remove-Item $_.FullName -Force
    }
}

Write-Host ''
Write-Host 'Application dependencies prepared.' -ForegroundColor Green

# ------------------------------------------------------------
# Create Windows installer
# ------------------------------------------------------------

Write-Host ''
Write-Host 'Creating Windows EXE installer...' -ForegroundColor Cyan

jpackage `
    --type exe `
    --name 'W.A.S.P' `
    --app-version $Version `
    --vendor 'AKS Global Logistics' `
    --description 'W.A.S.P - Waybill Automation & Shipping Platform' `
    --input $InputDir `
    --main-jar "aks-waybill-desktop-$Version.jar" `
    --main-class 'com.aks.waybill.Main' `
    --runtime-image $RuntimeDir `
    --dest $InstallerDir `
    --icon $Icon `
    --win-shortcut `
    --win-menu `
    --win-menu-group 'AKS Global Logistics' `
    --win-per-user-install `
    --win-dir-chooser `
    --win-upgrade-uuid '8a4e5a2e-6f2c-4a4d-8e70-3c7f5c7b9a11' `
    --java-options '-Dfile.encoding=UTF-8'

if ($LASTEXITCODE -ne 0) {
    throw 'jpackage failed.'
}

# ------------------------------------------------------------
# Display result
# ------------------------------------------------------------

Write-Host ''
Write-Host '=============================================' -ForegroundColor Green
Write-Host ' Packaging completed successfully' -ForegroundColor Green
Write-Host '=============================================' -ForegroundColor Green
Write-Host ''

Get-ChildItem $InstallerDir -Filter '*.exe' |
    Select-Object FullName, Length |
    Format-Table -AutoSize

Write-Host ''