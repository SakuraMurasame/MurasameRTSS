param([string]$Jdk = 'C:\Program Files\Android\openjdk\jdk-21.0.8', [string]$Sdk = 'A:\Android\android-sdk')
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskBuild = Join-Path $taskRoot 'build\frame-test'
$taskClasses = Join-Path $taskBuild 'classes'
$taskDex = Join-Path $taskBuild 'dex'
New-Item -ItemType Directory -Force -Path $taskBuild,$taskClasses,$taskDex | Out-Null
$taskTools = Join-Path $Sdk 'build-tools\35.0.0'
$taskPlatform = Join-Path $Sdk 'platforms\android-35\android.jar'
& (Join-Path $taskTools 'aapt2.exe') link -o (Join-Path $taskBuild 'unsigned.apk') -I $taskPlatform --manifest (Join-Path $PSScriptRoot 'device\AndroidManifest.xml')
if ($LASTEXITCODE -ne 0) { throw 'Test resource linking failed' }
& (Join-Path $Jdk 'bin\javac.exe') -encoding UTF-8 --release 17 -classpath $taskPlatform -d $taskClasses (Join-Path $PSScriptRoot 'device\FrameTestActivity.java')
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& (Join-Path $Jdk 'bin\jar.exe') --create --file (Join-Path $taskBuild 'test.jar') -C $taskClasses .
& (Join-Path $Jdk 'bin\java.exe') -cp (Join-Path $taskTools 'lib\d8.jar') com.android.tools.r8.D8 --min-api 29 --lib $taskPlatform --output $taskDex (Join-Path $taskBuild 'test.jar')
if ($LASTEXITCODE -ne 0) { throw 'Test DEX compilation failed' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskZip = [IO.Compression.ZipFile]::Open((Join-Path $taskBuild 'unsigned.apk'), [IO.Compression.ZipArchiveMode]::Update)
try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($taskZip, (Join-Path $taskDex 'classes.dex'), 'classes.dex') | Out-Null } finally { $taskZip.Dispose() }
& (Join-Path $taskTools 'zipalign.exe') -f -p 4 (Join-Path $taskBuild 'unsigned.apk') (Join-Path $taskBuild 'aligned.apk')
& (Join-Path $Jdk 'bin\java.exe') -jar (Join-Path $taskTools 'lib\apksigner.jar') sign --ks (Join-Path $taskRoot 'signing\development.jks') --ks-key-alias perfoverlay --ks-pass pass:android --key-pass pass:android --out (Join-Path $taskBuild 'FrameTest.apk') (Join-Path $taskBuild 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'Test APK signing failed' }
Write-Output (Join-Path $taskBuild 'FrameTest.apk')
