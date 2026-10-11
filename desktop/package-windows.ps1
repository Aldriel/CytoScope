<#
.SYNOPSIS
    Builds the portable Windows distribution of CytoScope.

.DESCRIPTION
    Produces target\dist\CytoScope-<version>-windows-x64.zip containing "CytoScope.exe", its own
    trimmed Java runtime and an "examples" folder with real FCS files to open or drop on the
    window: users unzip it and run the executable, no Java installation required.

    Requires JDK 21 (jlink and jpackage). Maven is run through the wrapper of the project. The
    JDK is taken from JAVA_HOME, otherwise from the PATH.

.PARAMETER SkipTests
    Packages without running the tests.

.EXAMPLE
    .\package-windows.ps1
#>
param([switch]$SkipTests)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function Invoke-Checked([string]$Tool, [string[]]$Arguments) {
    & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Tool failed with exit code $LASTEXITCODE" }
}

function Find-JdkTool([string]$Name) {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\$Name.exe")) {
        return "$env:JAVA_HOME\bin\$Name.exe"
    }
    return (Get-Command $Name -ErrorAction Stop).Source
}

$mvnw = Resolve-Path '..\mvnw.cmd'
$pomVersion = ([xml](Get-Content ..\pom.xml -Raw -Encoding UTF8)).project.version
# jpackage only accepts numeric versions: 1.0.0-SNAPSHOT becomes 1.0.0.
$version = $pomVersion -replace '-.*$', ''
$name = 'CytoScope'
$work = 'target\jpackage'
$libs = "$work\libs"
$runtime = "$work\runtime"
$image = "$work\image"
$zip = "target\dist\$name-$version-windows-x64.zip"

Write-Host "Packaging $name $version"
if (Test-Path $work) { Remove-Item $work -Recurse -Force }

# 1. Build the modules, then collect the desktop jar and its runtime dependencies.
$mvnArgs = @('-q', '-f', '..\pom.xml', 'install')
# Without tests there is no coverage to check.
if ($SkipTests) { $mvnArgs += @('-DskipTests', '-Djacoco.skip=true') }
Invoke-Checked $mvnw $mvnArgs
Invoke-Checked $mvnw @('-q',
    'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies',
    '-DincludeScope=runtime', "-DoutputDirectory=$libs")
Copy-Item "target\cytoscope-desktop-$pomVersion.jar" $libs
# JavaFX ships an empty jar plus a platform jar per module: keep the Windows ones only.
Get-ChildItem $libs -Filter 'javafx-*.jar' |
    Where-Object { $_.Name -notmatch '-win\.jar$' } |
    Remove-Item

# 2. Trimmed Java runtime. The application and JavaFX run from the class path.
#    java.scripting is required by FXML, java.desktop by the PDF report (fonts, images),
#    jdk.localedata provides the French number formats.
$modules = @(
    'java.base', 'java.desktop', 'java.logging', 'java.net.http', 'java.prefs', 'java.scripting',
    'java.sql', 'java.xml', 'java.naming', 'jdk.unsupported', 'jdk.localedata', 'jdk.charsets',
    'jdk.accessibility'
) -join ','
Invoke-Checked (Find-JdkTool 'jlink') @(
    '--add-modules', $modules, '--strip-debug', '--no-header-files', '--no-man-pages',
    '--compress=zip-6', '--output', $runtime)

# 3. Executable with its icon.
Invoke-Checked (Find-JdkTool 'jpackage') @(
    '--type', 'app-image',
    '--name', $name,
    '--app-version', $version,
    '--vendor', 'Maxime Ethier',
    '--copyright', "(c) $((Get-Date).Year) Maxime Ethier",
    '--description', 'Flow cytometry analysis: compensation, gating, apoptosis, cell cycle, dose-response',
    '--icon', 'packaging\CytoScope.ico',
    '--runtime-image', $runtime,
    '--input', $libs,
    '--main-jar', "cytoscope-desktop-$pomVersion.jar",
    '--main-class', 'com.bioinfo.cytoscope.desktop.DesktopLauncher',
    '--java-options', '-Dfile.encoding=UTF-8',
    '--dest', $image)

# 4. Real FCS files next to the executable, to try opening files, with their attribution.
$examples = New-Item -ItemType Directory -Force "$image\$name\examples\u87-cell-cycle"
$source = '..\core\src\main\resources\com\bioinfo\cytoscope\examples'
Copy-Item "$source\u87-cell-cycle\*.fcs" $examples
Copy-Item "$source\ATTRIBUTION.md" (Split-Path $examples)

# 5. Portable archive.
New-Item -ItemType Directory -Force (Split-Path $zip) | Out-Null
if (Test-Path $zip) { Remove-Item $zip }
Compress-Archive -Path "$image\$name" -DestinationPath $zip -CompressionLevel Optimal
$sizeMb = [math]::Round((Get-Item $zip).Length / 1MB, 1)
Write-Host "Created $zip ($sizeMb MB)"
