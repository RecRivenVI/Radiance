param([Parameter(Mandatory=$true)][string]$LaunchJson)
$ErrorActionPreference='Stop'
$launch=Get-Content -LiteralPath $LaunchJson -Raw | ConvertFrom-Json
$case=[IO.Path]::GetFullPath($launch.case)
$runRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../run'))
if(!$case.StartsWith($runRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Only repository run instances are allowed'}
if(!(Test-Path -LiteralPath (Join-Path $case '.radiance-audit-test-instance'))){throw 'Missing disposable instance marker'}
if(!$launch.args.Contains('-Dradiance.audit.unattended=true')){throw 'This launcher requires the explicit unattended mode'}
foreach($required in @('-Dradiance.audit.benchmark=true','-Dradiance.audit.experiments=true')){
    if(!$launch.args.Contains($required)){throw "Missing $required"}
}
if(!@($launch.args | Where-Object {$_ -like '-javaagent:*'}).Count){throw 'Missing startup companion agent'}
foreach($item in @($launch.artifacts)+@($launch.inputs)){
    if($null -eq $item){continue}
    if((Get-FileHash -LiteralPath $item.path -Algorithm SHA256).Hash -ne $item.sha256){throw "Artifact mismatch: $($item.path)"}
}
if(Test-Path -LiteralPath (Join-Path $case 'start.json')){throw 'Use a new per-run instance; existing evidence will not be overwritten'}
$masterVolume=@(Get-Content -LiteralPath (Join-Path $case 'options.txt') | Where-Object {$_ -match '^soundCategory_master:'})
if($masterVolume.Count -ne 1 -or $masterVolume[0] -notmatch '^soundCategory_master:0(?:\.0+)?$'){
    throw 'Automated Minecraft tests require master volume zero; prepare a new muted case without rewriting historical evidence'
}
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class AuditWindowEvidence {
 [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
 [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hwnd,out uint pid);
 public static uint ForegroundPid() { uint p; GetWindowThreadProcessId(GetForegroundWindow(),out p); return p; }
}
'@
$si=[Diagnostics.ProcessStartInfo]::new()
$si.FileName=$launch.java; $si.WorkingDirectory=$case
$si.UseShellExecute=$false; $si.CreateNoWindow=$true
$si.RedirectStandardOutput=$true; $si.RedirectStandardError=$true
foreach($name in @($si.Environment.Keys)){
    if($name -match '^(RADIANCE_|MCVR_|VK_|MOD_CLASSES$|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$)'){$si.Environment.Remove($name)|Out-Null}
}
foreach($argument in $launch.args){$si.ArgumentList.Add([string]$argument)}
$stdout=[IO.File]::Open((Join-Path $case 'stdout.log'),[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
$stderr=[IO.File]::Open((Join-Path $case 'stderr.log'),[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
$process=[Diagnostics.Process]::new();$process.StartInfo=$si
$before=[AuditWindowEvidence]::ForegroundPid()
if(!$process.Start()){throw 'Launch failed'}
$start=Get-Date
@{pid=$process.Id;utc=[DateTime]::UtcNow.ToString('o');foregroundBefore=$before;launchJson=(Resolve-Path -LiteralPath $LaunchJson).Path}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $case 'start.json')
$copyOut=$process.StandardOutput.BaseStream.CopyToAsync($stdout);$copyErr=$process.StandardError.BaseStream.CopyToAsync($stderr)
$samples=[Collections.Generic.List[object]]::new();$loaded=$false;$lastProcessSample=[DateTime]::MinValue;$closeRequested=$false
$runtimeModules=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::OrdinalIgnoreCase)
$processSamples=[Collections.Generic.List[object]]::new()
$timeout=if($launch.timeoutSeconds){[Math]::Clamp([int]$launch.timeoutSeconds,60,7500)}else{240}
while(!$process.WaitForExit(250)){
    $elapsed=((Get-Date)-$start).TotalSeconds
    $samples.Add(@{seconds=$elapsed;foregroundPid=[AuditWindowEvidence]::ForegroundPid()})
    if(((Get-Date)-$lastProcessSample).TotalSeconds -ge 1){
        $lastProcessSample=Get-Date
        $live=Get-Process -Id $process.Id -ErrorAction SilentlyContinue
        if($live){
            $processSamples.Add(@{seconds=$elapsed;cpuSeconds=$live.TotalProcessorTime.TotalSeconds;privateBytes=$live.PrivateMemorySize64;workingSetBytes=$live.WorkingSet64})
            foreach($module in @($live.Modules | Where-Object {$_.ModuleName -match '^(core|nvngx_.*|sl\..*)\.dll$'})){
                if(!$runtimeModules.ContainsKey($module.FileName)){
                    $identity=@{path=$module.FileName;sha256=(Get-FileHash -LiteralPath $module.FileName).Hash;firstObservedSeconds=$elapsed}
                    $runtimeModules.Add($module.FileName,$identity)
                    if($module.ModuleName -eq 'core.dll'){$identity|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $case 'loaded-core.json');$loaded=$true}
                }
            }
        }
    }
    if($elapsed -gt $timeout -and !$closeRequested){$closeRequested=$true;$process.CloseMainWindow()|Out-Null}
    if($elapsed -gt $timeout+20){break}
}
$samples|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $case 'foreground.json')
@($runtimeModules.Values)|ConvertTo-Json -Depth 4|Set-Content -LiteralPath (Join-Path $case 'loaded-runtimes.json')
$processSamples|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $case 'process-samples.json')
if(!$process.HasExited){throw "Bounded wait ended; process $($process.Id) still alive. Inspect; do not restart or force-kill."}
$copyOut.GetAwaiter().GetResult()|Out-Null;$copyErr.GetAwaiter().GetResult()|Out-Null;$stdout.Dispose();$stderr.Dispose()
@{pid=$process.Id;exitCode=$process.ExitCode;seconds=((Get-Date)-$start).TotalSeconds;loadedCore=$loaded;timeoutClose=$closeRequested;observedForegroundSamples=@($samples|Where-Object foregroundPid -eq $process.Id).Count}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $case 'result.json')
Get-Content -LiteralPath (Join-Path $case 'result.json')
