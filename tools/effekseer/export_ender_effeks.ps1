param([switch]$InstallAssets)
$ErrorActionPreference = 'Stop'
$research = $PSScriptRoot
$toolDir = Join-Path $research 'effekseer-1.80.7/Effekseer1.80.7Win/Tool/bin'
$authorDir = Join-Path $research 'authored'
$toolExe = Join-Path $toolDir 'Effekseer.exe'
if (-not (Test-Path -LiteralPath $toolExe)) { throw 'Extract the official Effekseer 1.80.7 Windows ZIP under research/effekseer-1.80.7 first.' }
& python (Join-Path $research 'generate_ender_effeks.py')
if ($LASTEXITCODE -ne 0) { throw 'Project generation failed.' }
foreach ($name in @('ender_departure','ender_arrival')) {
    $inputPath = Join-Path $authorDir ($name + '.efkproj')
    $outputPath = Join-Path $authorDir ($name + '.efkefc')
    $arguments = @('-cui', '-in', ('"' + $inputPath + '"'), '-o', ('"' + $outputPath + '"'))
    $exportProcess = Start-Process -FilePath $toolExe -ArgumentList $arguments -WorkingDirectory $toolDir -WindowStyle Hidden -PassThru -Wait
    if ($exportProcess.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $outputPath)) { throw ('Effekseer export failed: ' + $name) }
}
& python (Join-Path $research 'inspect_efkefc.py')
if ($LASTEXITCODE -ne 0) { throw 'Output verification failed.' }
if ($InstallAssets) {
    $workspace = Split-Path (Split-Path $research)
    $assetDir = Join-Path $workspace 'src/main/resources/assets/megawalls/effeks'
    New-Item -ItemType Directory -Force -Path $assetDir | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $assetDir 'textures') | Out-Null
    foreach ($name in @('ender_departure','ender_arrival')) {
        Copy-Item -LiteralPath (Join-Path $authorDir ($name + '.efkefc')) -Destination $assetDir
        Copy-Item -LiteralPath (Join-Path $authorDir ($name + '.efkefc.mcmeta')) -Destination $assetDir
    }
    foreach ($name in @('rift_star.png','rift_filament.png')) {
        Copy-Item -LiteralPath (Join-Path $authorDir ('textures/' + $name)) -Destination (Join-Path $assetDir 'textures')
    }
    Write-Output ('Installed authored assets: ' + $assetDir)
}
