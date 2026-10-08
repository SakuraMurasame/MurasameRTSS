param(
    [string]$Sdk = $env:ANDROID_SDK_ROOT,
    [string]$Jdk = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$taskRoot = $PSScriptRoot
if (-not $Sdk) { $Sdk = 'A:\Android\android-sdk' }
if (-not $Jdk) { $Jdk = 'C:\Program Files\Android\openjdk\jdk-21.0.8' }
$taskTools = Join-Path $Sdk 'build-tools\35.0.0'
$taskPlatform = Join-Path $Sdk 'platforms\android-35'
$taskAndroidJar = Join-Path $taskPlatform 'android.jar'
$taskJava = Join-Path $Jdk 'bin\java.exe'
$taskJavac = Join-Path $Jdk 'bin\javac.exe'
foreach ($taskRequired in @($taskJava, $taskJavac, $taskAndroidJar, (Join-Path $taskTools 'aapt2.exe'))) {
    if (-not (Test-Path -LiteralPath $taskRequired)) { throw "Missing build tool: $taskRequired. Pass -Sdk and -Jdk explicitly." }
}
# Each build uses a new workspace-contained directory; no recursive deletion or shared SDK mutation.
$taskBuild = Join-Path $taskRoot ('build\manual-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$taskDist = Join-Path $taskRoot 'dist'
$taskGen = Join-Path $taskBuild 'generated'
$taskClasses = Join-Path $taskBuild 'classes'
$taskDex = Join-Path $taskBuild 'dex'
New-Item -ItemType Directory -Force -Path $taskBuild,$taskDist,$taskGen,$taskClasses,$taskDex | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskLibraries = @()
foreach ($taskArtifact in @('api','provider','shared','aidl')) {
    $taskArchive = [IO.Compression.ZipFile]::OpenRead((Join-Path $taskRoot "vendor\$taskArtifact-13.1.5.aar"))
    try {
        $taskOutJar = Join-Path $taskBuild "$taskArtifact.jar"
        [IO.Compression.ZipFileExtensions]::ExtractToFile($taskArchive.GetEntry('classes.jar'), $taskOutJar, $true)
        $taskLibraries += $taskOutJar
    } finally { $taskArchive.Dispose() }
}
$taskLibraries += Join-Path $taskRoot 'vendor\annotation-1.3.0.jar'
function Assert-Exit([string]$stage) { if ($LASTEXITCODE -ne 0) { throw "$stage failed ($LASTEXITCODE)" } }
& (Join-Path $taskTools 'aapt2.exe') compile --dir (Join-Path $taskRoot 'app\src\main\res') -o (Join-Path $taskBuild 'resources.zip')
Assert-Exit 'Resource compilation'
# AGP 8+ obtains namespace from build.gradle; standalone AAPT2 needs it in its manifest copy.
[xml]$taskManifest = Get-Content -LiteralPath (Join-Path $taskRoot 'app\src\main\AndroidManifest.xml') -Raw
$taskManifest.DocumentElement.SetAttribute('package', 'io.perfoverlay.app')
$taskManifestPath = Join-Path $taskBuild 'AndroidManifest.xml'
$taskManifest.Save($taskManifestPath)
& (Join-Path $taskTools 'aapt2.exe') link -o (Join-Path $taskBuild 'unsigned.apk') -I $taskAndroidJar --manifest $taskManifestPath --java $taskGen (Join-Path $taskBuild 'resources.zip')
Assert-Exit 'Resource linking'
& (Join-Path $taskTools 'aidl.exe') "-p$(Join-Path $taskPlatform 'framework.aidl')" "-I$(Join-Path $taskRoot 'app\src\main\aidl')" "-o$taskGen" (Join-Path $taskRoot 'app\src\main\aidl\io\perfoverlay\app\IHardwareService.aidl')
Assert-Exit 'AIDL generation'
$taskSources = @(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'app\src\main\java'),$taskGen -Recurse -Filter '*.java' | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' })
$taskArgFile = Join-Path $taskBuild 'sources.txt'
[IO.File]::WriteAllLines($taskArgFile, $taskSources, [Text.UTF8Encoding]::new($false))
$taskClasspath = (@($taskAndroidJar) + $taskLibraries) -join ';'
& $taskJavac -encoding UTF-8 --release 17 -classpath $taskClasspath -d $taskClasses "@$taskArgFile"
Assert-Exit 'Java compilation'
$taskJar = Join-Path $taskBuild 'app.jar'
& (Join-Path $Jdk 'bin\jar.exe') --create --file $taskJar -C $taskClasses .
Assert-Exit 'Class archive'
& $taskJava -cp (Join-Path $taskTools 'lib\d8.jar') com.android.tools.r8.D8 --release --min-api 29 --lib $taskAndroidJar --output $taskDex $taskJar @taskLibraries
Assert-Exit 'DEX compilation'
$taskApk = Join-Path $taskBuild 'unsigned.apk'
$taskZip = [IO.Compression.ZipFile]::Open($taskApk, [IO.Compression.ZipArchiveMode]::Update)
try {
    foreach ($taskDexFile in (Get-ChildItem -LiteralPath $taskDex -Filter '*.dex')) {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($taskZip, $taskDexFile.FullName, $taskDexFile.Name, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($taskZip, (Join-Path $taskRoot 'vendor\Shizuku-LICENSE.txt'), 'assets/Shizuku-LICENSE.txt') | Out-Null
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($taskZip, (Join-Path $taskRoot 'vendor\Apache-2.0.txt'), 'assets/Apache-2.0.txt') | Out-Null
} finally { $taskZip.Dispose() }
$taskAligned = Join-Path $taskBuild 'aligned.apk'
& (Join-Path $taskTools 'zipalign.exe') -f -p 4 $taskApk $taskAligned
Assert-Exit 'APK alignment'
$taskSigning = Join-Path $taskRoot 'signing'
New-Item -ItemType Directory -Force -Path $taskSigning | Out-Null
$taskKey = Join-Path $taskSigning 'development.jks'
if (-not (Test-Path -LiteralPath $taskKey)) {
    & (Join-Path $Jdk 'bin\keytool.exe') -genkeypair -keystore $taskKey -storepass android -keypass android -alias perfoverlay -dname 'CN=MurasameRTSS Development' -keyalg RSA -keysize 2048 -validity 10000 -noprompt
    Assert-Exit 'Development key generation'
}
$taskFinal = Join-Path $taskDist 'MurasameRTSS-0.4.0.apk'
& $taskJava -jar (Join-Path $taskTools 'lib\apksigner.jar') sign --ks $taskKey --ks-key-alias perfoverlay --ks-pass pass:android --key-pass pass:android --out $taskFinal $taskAligned
Assert-Exit 'APK signing'
& $taskJava -jar (Join-Path $taskTools 'lib\apksigner.jar') verify --verbose $taskFinal
Assert-Exit 'Signature verification'
& (Join-Path $taskTools 'zipalign.exe') -c -p 4 $taskFinal
Assert-Exit 'Alignment verification'
$taskHash = (Get-FileHash -LiteralPath $taskFinal -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText((Join-Path $taskDist 'SHA256SUMS-0.4.0.txt'), "$taskHash  MurasameRTSS-0.4.0.apk`n", [Text.UTF8Encoding]::new($false))
Write-Output "APK: $taskFinal"
Write-Output "SHA256: $taskHash"
