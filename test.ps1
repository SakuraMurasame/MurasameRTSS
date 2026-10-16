param([string]$Jdk = 'C:\Program Files\Android\openjdk\jdk-21.0.8')
$ErrorActionPreference = 'Stop'
$taskClasses = Join-Path $PSScriptRoot 'build\tests'
New-Item -ItemType Directory -Force -Path $taskClasses | Out-Null
$taskSource = Join-Path $PSScriptRoot 'app\src\main\java\io\perfoverlay\app'
& (Join-Path $Jdk 'bin\javac.exe') -encoding UTF-8 --release 17 -d $taskClasses (Join-Path $taskSource 'MetricMath.java') (Join-Path $taskSource 'FlowLayout.java') (Join-Path $taskSource 'FrameMath.java') (Join-Path $PSScriptRoot 'tests\CoreTests.java')
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& (Join-Path $Jdk 'bin\java.exe') -ea -cp $taskClasses CoreTests
if ($LASTEXITCODE -ne 0) { throw 'Tests failed' }
& (Join-Path $Jdk 'bin\javac.exe') -encoding UTF-8 --release 17 -d $taskClasses (Join-Path $taskSource 'I18n.java') (Join-Path $taskSource 'Metric.java') (Join-Path $taskSource 'HistorySeries.java') (Join-Path $taskSource 'HistoryStore.java') (Join-Path $taskSource 'HistoryMath.java') (Join-Path $PSScriptRoot 'tests\HistoryTests.java')
if ($LASTEXITCODE -ne 0) { throw 'History test compilation failed' }
& (Join-Path $Jdk 'bin\java.exe') -ea -cp $taskClasses HistoryTests (Join-Path $PSScriptRoot 'build')
if ($LASTEXITCODE -ne 0) { throw 'History tests failed' }
& (Join-Path $Jdk 'bin\javac.exe') -encoding UTF-8 --release 17 -cp $taskClasses -d $taskClasses (Join-Path $PSScriptRoot 'tests\LocalizationTests.java')
if ($LASTEXITCODE -ne 0) { throw 'Localization test compilation failed' }
& (Join-Path $Jdk 'bin\java.exe') -ea -cp $taskClasses LocalizationTests $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Localization tests failed' }
& (Join-Path $Jdk 'bin\javac.exe') -encoding UTF-8 --release 17 -cp $taskClasses -d $taskClasses (Join-Path $taskSource 'AppBatteryData.java') (Join-Path $taskSource 'AppBatteryParser.java') (Join-Path $taskSource 'AppBatteryStore.java') (Join-Path $PSScriptRoot 'tests\AppBatteryTests.java')
if ($LASTEXITCODE -ne 0) { throw 'App battery test compilation failed' }
& (Join-Path $Jdk 'bin\java.exe') -ea -cp $taskClasses AppBatteryTests (Join-Path $PSScriptRoot 'build')
if ($LASTEXITCODE -ne 0) { throw 'App battery tests failed' }
& (Join-Path $Jdk 'bin\javac.exe') -encoding UTF-8 --release 17 -d $taskClasses (Join-Path $taskSource 'UpdatePolicy.java') (Join-Path $PSScriptRoot 'tests\UpdateTests.java')
if ($LASTEXITCODE -ne 0) { throw 'Update test compilation failed' }
& (Join-Path $Jdk 'bin\java.exe') -ea -cp $taskClasses UpdateTests
if ($LASTEXITCODE -ne 0) { throw 'Update tests failed' }
& (Join-Path $Jdk 'bin\javac.exe') -encoding UTF-8 --release 17 -d $taskClasses (Join-Path $taskSource 'BatteryLink.java') (Join-Path $PSScriptRoot 'tests\BatteryLinkTests.java')
if ($LASTEXITCODE -ne 0) { throw 'Battery link test compilation failed' }
& (Join-Path $Jdk 'bin\java.exe') -ea -cp $taskClasses BatteryLinkTests
if ($LASTEXITCODE -ne 0) { throw 'Battery link tests failed' }
