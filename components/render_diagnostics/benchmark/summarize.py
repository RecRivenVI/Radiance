"""Summarize accepted series without hiding failed gates or conflating process/GPU scopes."""
from pathlib import Path
from collections import defaultdict
import argparse
import csv
import datetime as dt
import json
import statistics
import analyze
import compare


def summarize(manifests, output):
    results=[]
    groups=defaultdict(list)
    for manifest in manifests:
        for case_name in json.loads(Path(manifest).read_text())['cases']:
            case=Path(case_name)
            analysis=json.loads((case/'ANALYSIS.json').read_text())
            errors=compare.gate(case,analysis)
            if errors:raise ValueError(f'{case.name}: {errors}')
            folder=next((case/'radiance-audit/benchmark').iterdir())
            with (folder/'frames.csv').open() as f:rows=list(csv.DictReader(f))
            intervals=[int(row['start_interval_ns'])/1e6 for row in rows]
            elapsed=sum(intervals)/1000
            # Folder name is wall time immediately before observer initialization; approximate
            # correlation only. Trim two seconds at each end for one-second external samplers.
            start=int(folder.name)/1000+int(rows[0]['start_ns'])/1e9+2
            end=int(folder.name)/1000+int(rows[-1]['start_ns'])/1e9-2
            proc=json.loads((case/'process-samples.json').read_text(encoding='utf-8-sig'))
            started=json.loads((case/'start.json').read_text(encoding='utf-8-sig'))
            epoch=dt.datetime.fromisoformat(started['utc'].replace('Z','+00:00')).timestamp()
            timed=[p for p in proc if start<=epoch+p['seconds']<=end]
            cpu=((timed[-1]['cpuSeconds']-timed[0]['cpuSeconds'])/(timed[-1]['seconds']-timed[0]['seconds'])) if len(timed)>1 else None
            with (case/'gpu-device.csv').open() as f:
                all_gpu=list(csv.DictReader(f,skipinitialspace=True))
            gpu=[p for p in all_gpu if start<=dt.datetime.strptime(p['timestamp'],'%Y/%m/%d %H:%M:%S.%f').timestamp()<=end]
            def number(row,key):return float(row[key].split()[0])
            fixture=json.loads((case/'FIXTURE.json').read_text())
            wanted=json.loads((case/'REQUESTED_SETTINGS.json').read_text())
            target=wanted['target']
            # Saved contents, not just entity counts; this does not prove GPU draw coverage.
            equipped=0;items=defaultdict(int)
            for chunk in analyze.chunks(case/'saves/Audit Benchmark/entities'):
                for entity in chunk.get('Entities',[]):
                    if str(entity['id'])=='minecraft:armor_stand':
                        armor=[str(a.get('id','')) for a in entity.get('ArmorItems',[])]
                        if armor==['minecraft:diamond_'+p for p in ('boots','leggings','chestplate','helmet')]:equipped+=1
                    if str(entity['id'])=='minecraft:item_frame':items[str(entity.get('Item',{}).get('id',''))]+=1
            if fixture['scene'] in ('model-city','factory'):
                expected=fixture['expected']['armor_stands']
                if equipped!=expected:raise ValueError('Armor mismatch: '+case.name)
                if any(items['minecraft:'+p]!=fixture['expected']['item_frames']//4 for p in ('oak_fence','hopper','brewing_stand','anvil')):
                    raise ValueError('Item frame contents mismatch: '+case.name)
            result={'case':case.name,'scene':fixture['scene'],'target':target,'frame_ms':analyze.distribution(intervals),
                    'render_thread_cpu_ms_per_frame':sum(int(r['render_thread_cpu_ns']) for r in rows)/len(rows)/1e6,
                    'process_cpu_core_equivalents_approx':cpu,
                    'process_private_peak_gib_full_lifecycle':max(p['privateBytes'] for p in proc)/2**30,
                    'device_vram_peak_mib_full_lifecycle':max(number(p,'memory.used [MiB]') for p in all_gpu),
                    'device_gpu_utilization_percent_sample_approx':statistics.mean(number(p,'utilization.gpu [%]') for p in gpu) if gpu else None,
                    'device_vram_mib_sample_approx':statistics.mean(number(p,'memory.used [MiB]') for p in gpu) if gpu else None,
                    'device_clock_mhz_sample_approx':statistics.mean(number(p,'clocks.current.graphics [MHz]') for p in gpu) if gpu else None,
                    'sample_seconds_from_intervals':elapsed,'equipped_armor_stands':equipped,'item_frame_contents':dict(items),
                    'camera':analysis['camera'],'configuration_gate':'passed','exit_code':analysis['process']['exitCode']}
            results.append(result);groups[(fixture['scene'],target)].append((result,intervals))
    aggregate=[]
    for (scene,target),runs in groups.items():
        pooled=[v for _,values in runs for v in values]
        means=[r['frame_ms']['mean'] for r,_ in runs]
        mean=statistics.mean(means)
        aggregate.append({'scene':scene,'target':target,'runs':len(runs),'mean_frame_ms_equal_run_weight':mean,
                          'real_fps_from_mean':1000/mean,'run_mean_range_ms':[min(means),max(means)],
                          'pooled_frames_ms':analyze.distribution(pooled),
                          'mean_render_thread_cpu_ms':statistics.mean(r['render_thread_cpu_ms_per_frame'] for r,_ in runs),
                          'mean_process_cpu_cores_approx':statistics.mean(r['process_cpu_core_equivalents_approx'] for r,_ in runs),
                          'device_vram_peak_mib_full_lifecycle':max(r['device_vram_peak_mib_full_lifecycle'] for r,_ in runs)})
    report={'runs':results,'aggregate':aggregate,'boundaries':[
        'Static camera, 60 s world warmup and 30 s sample per fresh process; no route/loading-latency result.',
        'Equal requested settings and saved contents do not prove equal rendered geometry, backend coverage or pixels.',
        'Frame intervals are real CPU frame-loop cadence, not presented/generated-frame timestamps or GPU pass timings.',
        'One-second external telemetry approximately aligned to observer initialization, with 2 s edge exclusion.',
        'CPU cores = process CPU-seconds / wall-seconds; render-thread CPU is quantized on Windows.',
        'NVML device VRAM includes desktop, SDK and any other process; peaks include startup and shutdown.',
        'Short bounded comparisons do not establish visual equivalence or long-duration stability.']}
    output=Path(output);output.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(aggregate,indent=2))
    return report


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('manifests',nargs='+',type=Path);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();summarize(a.manifests,a.output)
