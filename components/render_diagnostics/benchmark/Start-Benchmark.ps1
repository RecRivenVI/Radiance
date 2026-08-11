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
if((Test-Path -LiteralPath (Join-Path $case 'start.json')) -or (Test-Path -LiteralPath (Join-Path $case 'environment.json'))){throw 'Use a new per-run instance; existing evidence will not be overwritten'}
$masterVolume=@(Get-Content -LiteralPath (Join-Path $case 'options.txt') | Where-Object {$_ -match '^soundCategory_master:'})
if($masterVolume.Count -ne 1 -or $masterVolume[0] -notmatch '^soundCategory_master:0(?:\.0+)?$'){
    throw 'Automated Minecraft tests require master volume zero; prepare a new muted case without rewriting historical evidence'
}
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class AuditWindowEvidence {
 [StructLayout(LayoutKind.Sequential)] struct LastInputInfo { public uint cbSize; public uint dwTime; }
 [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
 [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hwnd,out uint pid);
 [DllImport("user32.dll")] static extern bool GetLastInputInfo(ref LastInputInfo info);
 [DllImport("user32.dll",EntryPoint="GetWindowLongPtrW")] static extern IntPtr GetWindowLongPtr(IntPtr hwnd,int index);
 // Read-only; the system, not WM_MOUSEACTIVATE's DefWindowProc answer, enforces WS_EX_NOACTIVATE.
 public static long ExtendedStyle(IntPtr hwnd) { return GetWindowLongPtr(hwnd,-20).ToInt64(); }
 public static uint ForegroundPid() { uint p; GetWindowThreadProcessId(GetForegroundWindow(),out p); return p; }
 // Milliseconds since the last keyboard/mouse input in this session, or -1 if unavailable.
 // Distinguishes user-driven foreground changes from system/program activation.
 public static long IdleMilliseconds() {
  var info=new LastInputInfo(); info.cbSize=(uint)Marshal.SizeOf(info);
  if(!GetLastInputInfo(ref info)) return -1;
  return unchecked((uint)Environment.TickCount-info.dwTime);
 }
}
'@
# Machine load. Competing work distorts timing and can change timing-sensitive behavior, so every
# run first waits for a quiet machine and then records other processes' CPU while it runs.
# CPU is the sum of per-process CPU-time deltas over all logical processors; processes this
# account cannot read (a few system services) are not counted. GPU use cannot be attributed per
# process under WDDM, so it is only checked before launch, when no benchmark client exists.
$environmentLimits=@{preflightCpuPct=8.0;preflightGpuPct=15.0;runForeignCpuAvgPct=5.0;runForeignCpuP95Pct=10.0}
$minecraftPattern='--gameDir|--assetsDir|--launchTarget|fabric\.dli\.|KnotClient|net\.minecraft\.client\.main\.Main'
$overlayPattern='^(RTSS|RTSSHooksLoader64|ToDesk|obs64|obs32|MSIAfterburner|NVIDIA Overlay|Discord)$'
function Get-CpuSnapshot {
    $snapshot=@{}
    foreach($p in [Diagnostics.Process]::GetProcesses()){
        try{$snapshot[$p.Id]=@($p.ProcessName,$p.TotalProcessorTime.TotalSeconds)}catch{}finally{$p.Dispose()}
    }
    $snapshot
}
function Measure-ForeignCpu($before,$after,[double]$seconds,$exclude){
    $cores=[Environment]::ProcessorCount;$total=0.0;$byName=@{}
    foreach($id in $after.Keys){
        if($exclude -contains $id){continue}
        $previous=$before[$id]
        $used=$after[$id][1]-$(if($previous -and $previous[0] -eq $after[$id][0]){$previous[1]}else{0})
        if($used -le 0){continue}
        $total+=$used;$byName[$after[$id][0]]=[double]$byName[$after[$id][0]]+$used
    }
    $scale=100.0/($seconds*$cores)
    @{cpuPct=[Math]::Round($total*$scale,2)
      top=@($byName.GetEnumerator()|Sort-Object Value -Descending|Select-Object -First 5|ForEach-Object{"$($_.Key)=$([Math]::Round($_.Value*$scale,2))"})}
}
function Get-MinecraftClients($exclude){
    @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'"|Where-Object{
        $exclude -notcontains [int]$_.ProcessId -and $_.CommandLine -match $minecraftPattern}|ForEach-Object{
        @{pid=[int]$_.ProcessId;commandLine=$_.CommandLine.Substring(0,[Math]::Min(240,$_.CommandLine.Length))}})
}
function Measure-Quiet([int]$seconds){
    $gpuTool=Get-Command nvidia-smi -ErrorAction SilentlyContinue
    $before=Get-CpuSnapshot;$started=Get-Date;$gpu=[Collections.Generic.List[double]]::new()
    while(((Get-Date)-$started).TotalSeconds -lt $seconds){
        if($gpuTool){
            $line=(& $gpuTool.Source --query-gpu=utilization.gpu --format=csv,noheader,nounits 2>$null|Select-Object -First 1)
            if($line -match '^\s*(\d+)'){$gpu.Add([double]$Matches[1])}
        }
        Start-Sleep -Milliseconds 1000
    }
    $cpu=Measure-ForeignCpu $before (Get-CpuSnapshot) ((Get-Date)-$started).TotalSeconds @($PID)
    $gpuPct=if($gpu.Count){[Math]::Round(($gpu|Measure-Object -Average).Average,2)}else{$null}
    $clients=Get-MinecraftClients @()
    $reasons=@()
    if($cpu.cpuPct -gt $environmentLimits.preflightCpuPct){$reasons+="cpu $($cpu.cpuPct)% > $($environmentLimits.preflightCpuPct)%"}
    if($null -ne $gpuPct -and $gpuPct -gt $environmentLimits.preflightGpuPct){$reasons+="gpu $gpuPct% > $($environmentLimits.preflightGpuPct)%"}
    if($clients.Count){$reasons+="other Minecraft client(s): $(@($clients|ForEach-Object{$_.pid}) -join ',')"}
    @{utc=[DateTime]::UtcNow.ToString('o');seconds=$seconds;cpuPct=$cpu.cpuPct;topCpu=$cpu.top;gpuPct=$gpuPct
      gpuSampled=$gpu.Count;minecraftClients=$clients;passed=!$reasons.Count;reasons=$reasons}
}
. (Join-Path $PSScriptRoot 'MemoryAttributionPreflight.ps1')
$memoryAttributionOverride=$false
$compatibilityOverride=$false
$preflightSeconds=if($launch.preflightSeconds){[Math]::Clamp([int]$launch.preflightSeconds,10,120)}else{20}
$preflightMaxWait=if($null -ne $launch.preflightMaxWaitSeconds){[Math]::Clamp([int]$launch.preflightMaxWaitSeconds,0,3600)}else{600}
$overlays=@(Get-Process|Where-Object{$_.ProcessName -match $overlayPattern}|ForEach-Object{$_.ProcessName}|Sort-Object -Unique)
$attempts=[Collections.Generic.List[object]]::new();$waitStart=Get-Date
while($true){
    $attempt=Measure-Quiet $preflightSeconds;$attempts.Add($attempt)
    if($attempt.passed){break}
    $freeMiB=$null
    $gpuTool=Get-Command nvidia-smi -ErrorAction SilentlyContinue
    if($gpuTool){
        $freeLine=(& $gpuTool.Source --query-gpu=memory.free --format=csv,noheader,nounits 2>$null|Select-Object -First 1)
        if($freeLine -match '^\s*(\d+)'){$freeMiB=[double]$Matches[1]}
    }
    if(Test-MemoryAttributionPreflight $launch (Test-Path -LiteralPath (Join-Path $case '.radiance-audit-test-instance')) $attempt.minecraftClients.Count $freeMiB){
        $memoryAttributionOverride=$true
        $attempt.memoryAttributionOverride=$true
        $attempt.freeGpuMiB=$freeMiB
        break
    }
    if(Test-CompatibilityPreflight $launch (Test-Path -LiteralPath (Join-Path $case '.radiance-audit-test-instance')) $attempt.minecraftClients.Count $freeMiB $attempt.cpuPct $attempt.gpuPct){
        $compatibilityOverride=$true
        $attempt.compatibilityOverride=$true
        $attempt.freeGpuMiB=$freeMiB
        break
    }
    if(((Get-Date)-$waitStart).TotalSeconds -ge $preflightMaxWait){
        @{limits=$environmentLimits;overlaysPresent=$overlays;preflight=@{passed=$false;attempts=$attempts}}|
            ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $case 'environment.json')
        throw "Machine not quiet after $preflightMaxWait s: $($attempt.reasons -join '; '). Not launched."
    }
    Start-Sleep -Seconds 10
}
$si=[Diagnostics.ProcessStartInfo]::new()
$si.FileName=$launch.java; $si.WorkingDirectory=$case
$si.UseShellExecute=$false; $si.CreateNoWindow=$true
$si.RedirectStandardOutput=$true; $si.RedirectStandardError=$true
foreach($name in @($si.Environment.Keys)){
    if($name -match '^(RADIANCE_|MCVR_|VK_|MOD_CLASSES$|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$)'){$si.Environment.Remove($name)|Out-Null}
}
# Native counters are opt-in per isolated case, never inherited from the desktop.
# Do not allow arbitrary environment entries or Vulkan/driver overrides here.
$diagnosticEnvironment=@{}
if($launch.diagnosticEnvironment){
    foreach($entry in $launch.diagnosticEnvironment.PSObject.Properties){
        if($entry.Name -notin @('RADIANCE_CHUNK_PERF','RADIANCE_CHUNK_TRACE','RADIANCE_CHUNK_BENCH','RADIANCE_CHUNK_CENSUS','RADIANCE_MEMORY_PROBE','RADIANCE_AUDIT_SMOKE','RADIANCE_AUDIT_REFERENCE_BUFFER_SWEEP','RADIANCE_KEEP_SCENE_DESCRIPTORS_ON_LEAVE','RADIANCE_KEEP_RECONSTRUCTION_ON_LEAVE') -or
           [string]$entry.Value -ne '1'){
            throw "Unsupported diagnostic environment entry: $($entry.Name)"
        }
        $si.Environment[$entry.Name]='1'
        $diagnosticEnvironment[$entry.Name]='1'
    }
}
foreach($argument in $launch.args){$si.ArgumentList.Add([string]$argument)}
$stdout=[IO.File]::Open((Join-Path $case 'stdout.log'),[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
$stderr=[IO.File]::Open((Join-Path $case 'stderr.log'),[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
$process=[Diagnostics.Process]::new();$process.StartInfo=$si
$before=[AuditWindowEvidence]::ForegroundPid()
if(!$process.Start()){throw 'Launch failed'}
$start=Get-Date
@{pid=$process.Id;utc=[DateTime]::UtcNow.ToString('o');foregroundBefore=$before;launchJson=(Resolve-Path -LiteralPath $LaunchJson).Path;diagnosticEnvironment=$diagnosticEnvironment}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $case 'start.json')
$copyOut=$process.StandardOutput.BaseStream.CopyToAsync($stdout);$copyErr=$process.StandardError.BaseStream.CopyToAsync($stderr)
$gpuMonitor=$null;$gpuStopFile=$null
if($launch.monitorGpu){
    $nvmlMonitor=$launch.gpuMonitorBackend -eq 'nvml'
    $gpuTool=Get-Command $(if($nvmlMonitor){'python'}else{'nvidia-smi'}) -ErrorAction SilentlyContinue
    if($gpuTool){
        $gpuInfo=[Diagnostics.ProcessStartInfo]::new()
        $gpuInfo.FileName=$gpuTool.Source;$gpuInfo.UseShellExecute=$false
        $gpuInfo.CreateNoWindow=$true;$gpuInfo.WindowStyle=[Diagnostics.ProcessWindowStyle]::Hidden
        if($nvmlMonitor){
            $gpuStopFile=Join-Path $case '.gpu-monitor-stop'
            $sensorTimeout=if($launch.timeoutSeconds){[Math]::Clamp([int]$launch.timeoutSeconds,60,7500)}else{240}
            $sensorDuration=[Math]::Min(7600,($sensorTimeout+30))
            foreach($arg in @((Join-Path $PSScriptRoot 'gpu_monitor.py'),'--output',(Join-Path $case 'gpu.csv'),'--stop-file',$gpuStopFile,'--duration',[string]$sensorDuration)){$gpuInfo.ArgumentList.Add($arg)}
        }else{
            foreach($arg in @('--query-gpu=timestamp,memory.used,memory.total,utilization.gpu,clocks.current.graphics,clocks.current.memory','--format=csv','--loop-ms=1000',('--filename='+(Join-Path $case 'gpu.csv')))){$gpuInfo.ArgumentList.Add($arg)}
        }
        $gpuMonitor=[Diagnostics.Process]::new();$gpuMonitor.StartInfo=$gpuInfo
        try{if(!$gpuMonitor.Start()){$gpuMonitor=$null}}catch{$gpuMonitor=$null;Write-Warning 'GPU sensor monitor unavailable'}
    }
}
try {
$samples=[Collections.Generic.List[object]]::new();$loaded=$false;$lastProcessSample=[DateTime]::MinValue;$closeRequested=$false
$runtimeModules=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::OrdinalIgnoreCase)
$processSamples=[Collections.Generic.List[object]]::new()
$activationChecks=[Collections.Generic.List[object]]::new()
$timeout=if($launch.timeoutSeconds){[Math]::Clamp([int]$launch.timeoutSeconds,60,7500)}else{240}
$ownPids=@($PID,$process.Id)+@(if($gpuMonitor){$gpuMonitor.Id})
$environmentSamples=[Collections.Generic.List[object]]::new()
# Per-process GPU memory as Task Manager reports it (WDDM counters): dedicated, shared, committed.
$gpuMemorySamples=[Collections.Generic.List[object]]::new()
$foreignStarts=[Collections.Generic.List[object]]::new()
$foreignClients=[Collections.Generic.List[object]]::new()
$environmentSnapshot=Get-CpuSnapshot;$lastEnvironmentSample=Get-Date
while(!$process.WaitForExit(250)){
    $elapsed=((Get-Date)-$start).TotalSeconds
    if(((Get-Date)-$lastEnvironmentSample).TotalSeconds -ge 5){
        $now=Get-Date;$snapshot=Get-CpuSnapshot
        $foreign=Measure-ForeignCpu $environmentSnapshot $snapshot ($now-$lastEnvironmentSample).TotalSeconds $ownPids
        $environmentSamples.Add(@{seconds=[Math]::Round($elapsed,1);foreignCpuPct=$foreign.cpuPct;top=$foreign.top})
        $gpuMemory=@(Get-CimInstance Win32_PerfFormattedData_GPUPerformanceCounters_GPUProcessMemory -Filter "Name LIKE 'pid_$($process.Id)_%'" -ErrorAction SilentlyContinue)
        if($gpuMemory.Count){$gpuMemorySamples.Add(@{seconds=[Math]::Round($elapsed,1)
            dedicatedBytes=[long]($gpuMemory|Measure-Object DedicatedUsage -Sum).Sum;sharedBytes=[long]($gpuMemory|Measure-Object SharedUsage -Sum).Sum
            committedBytes=[long]($gpuMemory|Measure-Object TotalCommitted -Sum).Sum})}
        foreach($id in $snapshot.Keys){
            if($environmentSnapshot.ContainsKey($id) -or $ownPids -contains $id){continue}
            $name=$snapshot[$id][0];$entry=@{seconds=[Math]::Round($elapsed,1);pid=$id;name=$name}
            if($name -match '^(java|javaw|python|node|gradle|cl|link|MSBuild|cmake|cargo|rustc|dotnet|ffmpeg)$'){
                $info=Get-CimInstance Win32_Process -Filter "ProcessId=$id" -ErrorAction SilentlyContinue
                if($info -and $info.CommandLine){
                    $entry.commandLine=$info.CommandLine.Substring(0,[Math]::Min(240,$info.CommandLine.Length))
                    if($name -match '^javaw?$' -and $info.CommandLine -match $minecraftPattern){$foreignClients.Add($entry)}
                }
            }
            $foreignStarts.Add($entry)
        }
        $environmentSnapshot=$snapshot;$lastEnvironmentSample=$now
    }
    $samples.Add(@{seconds=$elapsed;foregroundPid=[AuditWindowEvidence]::ForegroundPid();inputIdleMs=[AuditWindowEvidence]::IdleMilliseconds()})
    if(((Get-Date)-$lastProcessSample).TotalSeconds -ge 1){
        $lastProcessSample=Get-Date
        $live=Get-Process -Id $process.Id -ErrorAction SilentlyContinue
        if($live){
            $processSamples.Add(@{seconds=$elapsed;cpuSeconds=$live.TotalProcessorTime.TotalSeconds;privateBytes=$live.PrivateMemorySize64;workingSetBytes=$live.WorkingSet64})
            # Record the game window's activation contract whenever its handle or extended style changes.
            $hwnd=$live.MainWindowHandle
            if($hwnd -ne [IntPtr]::Zero){
                $style=[AuditWindowEvidence]::ExtendedStyle($hwnd)
                $last=if($activationChecks.Count){$activationChecks[$activationChecks.Count-1]}else{$null}
                if(!$last -or $last.hwnd -ne $hwnd.ToInt64() -or $last.exStyle -ne $style){
                    $activationChecks.Add(@{seconds=$elapsed;hwnd=$hwnd.ToInt64();exStyle=$style;noActivate=(($style -band 0x08000000) -ne 0)})
                }
            }
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
ConvertTo-Json -InputObject @($activationChecks) -Depth 3|Set-Content -LiteralPath (Join-Path $case 'window-activation.json')
$activationGuardObserved=$activationChecks.Count -gt 0 -and !@($activationChecks|Where-Object {!$_.noActivate}).Count
$foreignCpu=@($environmentSamples|ForEach-Object{[double]$_.foreignCpuPct}|Sort-Object)
$foreignAvg=if($foreignCpu.Count){[Math]::Round(($foreignCpu|Measure-Object -Average).Average,2)}else{$null}
$foreignP95=if($foreignCpu.Count){$foreignCpu[[Math]::Min($foreignCpu.Count-1,[int][Math]::Floor(0.95*$foreignCpu.Count))]}else{$null}
$runReasons=@()
if($memoryAttributionOverride){$runReasons+='memory-only competing-load exception; invalid for performance comparison'}
if($launch.classification -eq 'NEOFORGE_MINIMUM_COMPATIBILITY'){$runReasons+='loader compatibility qualification; invalid for performance comparison'}
if($compatibilityOverride){$runReasons+='bounded moderate-load exception for loader qualification'}
if(!$foreignCpu.Count){$runReasons+='no run-time load samples'}
if($null -ne $foreignAvg -and $foreignAvg -gt $environmentLimits.runForeignCpuAvgPct){$runReasons+="foreign cpu avg $foreignAvg% > $($environmentLimits.runForeignCpuAvgPct)%"}
if($null -ne $foreignP95 -and $foreignP95 -gt $environmentLimits.runForeignCpuP95Pct){$runReasons+="foreign cpu p95 $foreignP95% > $($environmentLimits.runForeignCpuP95Pct)%"}
if($foreignClients.Count){$runReasons+="other Minecraft client(s) started: $(@($foreignClients|ForEach-Object{$_.pid}) -join ',')"}
$environment=@{limits=$environmentLimits;overlaysPresent=$overlays
    preflight=@{passed=$true;attempts=$attempts}
    run=@{samples=$environmentSamples;foreignCpuAvgPct=$foreignAvg;foreignCpuP95Pct=$foreignP95
          foreignCpuMaxPct=$(if($foreignCpu.Count){$foreignCpu[-1]}else{$null});foreignStarts=$foreignStarts
          minecraftClients=$foreignClients;quiet=!$runReasons.Count;reasons=$runReasons}}
$environment|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $case 'environment.json')
ConvertTo-Json -InputObject @($gpuMemorySamples) -Depth 3|Set-Content -LiteralPath (Join-Path $case 'gpu-process-memory.json')
$environmentSummary=@{quiet=!$runReasons.Count;reasons=$runReasons;preflightCpuPct=$attempts[$attempts.Count-1].cpuPct
    preflightGpuPct=$attempts[$attempts.Count-1].gpuPct;preflightWaitSeconds=[Math]::Round(($start-$waitStart).TotalSeconds,1)
    foreignCpuAvgPct=$foreignAvg;foreignCpuP95Pct=$foreignP95;overlaysPresent=$overlays}
if(!$process.HasExited){throw "Bounded wait ended; process $($process.Id) still alive. Inspect; do not restart or force-kill."}
$copyOut.GetAwaiter().GetResult()|Out-Null;$copyErr.GetAwaiter().GetResult()|Out-Null;$stdout.Dispose();$stderr.Dispose()
@{pid=$process.Id;exitCode=$process.ExitCode;seconds=((Get-Date)-$start).TotalSeconds;loadedCore=$loaded;timeoutClose=$closeRequested;observedForegroundSamples=@($samples|Where-Object foregroundPid -eq $process.Id).Count;activationGuardObserved=$activationGuardObserved;activationChecks=$activationChecks.Count;environment=$environmentSummary}|ConvertTo-Json -Depth 4|Set-Content -LiteralPath (Join-Path $case 'result.json')
Get-Content -LiteralPath (Join-Path $case 'result.json')
} finally {
    # This is our read-only sensor helper, never the Minecraft process.
    if($gpuMonitor){
        if($gpuStopFile -and !$gpuMonitor.HasExited){[IO.File]::WriteAllText($gpuStopFile,'stop');$gpuMonitor.WaitForExit(2500)|Out-Null}
        if(!$gpuMonitor.HasExited){$gpuMonitor.Kill();$gpuMonitor.WaitForExit(2000)|Out-Null}
        $gpuMonitor.Dispose()
    }
}
