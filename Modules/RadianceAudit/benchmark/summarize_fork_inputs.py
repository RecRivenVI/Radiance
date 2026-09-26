"""Summarize only completed, gated cases; instrumented runs stay separate."""
from pathlib import Path
import argparse
import collections
import csv
import json
import statistics
import analyze
import compare


def summarize(root):
    root=Path(root).resolve()
    plan=json.loads((root/'formal-series.json').read_text())
    groups=collections.defaultdict(list)
    for name in plan['cases']:
        case=Path(name)
        if not (case/'result.json').is_file():raise ValueError('Incomplete case: '+name)
        result=compare.collect(case)
        if not result['configurationGate']['passed']:raise ValueError(str(result['configurationGate']))
        requested=json.loads((case/'REQUESTED_SETTINGS.json').read_text())
        if requested['observerMode']!='portable-frame-only':raise ValueError('Instrumented case in formal results')
        fixture=json.loads((case/'FIXTURE.json').read_text())
        mode=requested['forkInputVariant']
        variant='combined' if mode['compactVertices'] else ('direct' if mode['directEntityInput'] else 'legacy')
        frames=next((case/'radiance-audit/benchmark').glob('*/frames.csv'))
        values=[int(r['start_interval_ns'])/1e6 for r in csv.DictReader(frames.open()) if int(r['start_interval_ns'])>0]
        process=json.loads((case/'process-samples.json').read_text(encoding='utf-8-sig'))
        gpu=list(csv.DictReader((case/'gpu-device.csv').open()))
        gpu=[{k.strip():v.strip() for k,v in r.items()} for r in gpu]
        run={'case':str(case),'meanMs':statistics.mean(values),'frames':len(values),'rawMs':values,
             'privatePeakMiB':max(r['privateBytes'] for r in process)/1048576,
             'workingSetPeakMiB':max(r['workingSetBytes'] for r in process)/1048576,
             'deviceVramPeakMiB':max(float(r['memory.used [MiB]'].split()[0]) for r in gpu),
             'drivers':sorted({r['driver_version'] for r in gpu})}
        groups[(fixture['scene'],variant)].append(run)
    rows=[]
    for (scene,variant),runs in groups.items():
        pooled=analyze.distribution([x for r in runs for x in r['rawMs']])
        mean=statistics.mean(r['meanMs'] for r in runs)
        rows.append({'scene':scene,'variant':variant,'runs':len(runs),'meanMs':mean,'fps':1000/mean,
                     'runMeansMs':[r['meanMs'] for r in runs],'pooled':pooled,
                     'privatePeakRangeMiB':[min(r['privatePeakMiB'] for r in runs),max(r['privatePeakMiB'] for r in runs)],
                     'deviceVramPeakRangeMiB':[min(r['deviceVramPeakMiB'] for r in runs),max(r['deviceVramPeakMiB'] for r in runs)],
                     'cases':[{k:v for k,v in r.items() if k!='rawMs'} for r in runs]})
    for row in rows:
        baseline=next(r['meanMs'] for r in rows if r['scene']==row['scene'] and r['variant']=='legacy')
        row['frameReductionPercent']=100*(1-row['meanMs']/baseline)
    result={'scope':'same candidate, muted, 60s warmup/30s observation; source flags, not per-frame native activation telemetry',
            'statistics':'equal weight per run; percentiles pooled frames; memory peaks include startup; VRAM is whole device',
            'rows':rows}
    (root/'FORMAL_SUMMARY.json').write_text(json.dumps(result,indent=2)+'\n')
    lines=['# Same-artifact input-path comparison','',result['scope'], '',
           '| Scene | Mode | Runs | Mean ms | Real FPS | p50 / p95 / p99 ms | Frame reduction |',
           '| --- | --- | ---: | ---: | ---: | --- | ---: |']
    for r in rows:
        q=r['pooled'];lines.append(f"| {r['scene']} | {r['variant']} | {r['runs']} | {r['meanMs']:.3f} | {r['fps']:.2f} | {q['p50']:.2f} / {q['p95']:.2f} / {q['p99']:.2f} | {r['frameReductionPercent']:.2f}% |")
    lines+=['',result['statistics'],'','All listed cases passed configuration/save/exit gates. Visual and long-duration acceptance are not implied.']
    (root/'FORMAL_SUMMARY.md').write_text('\n'.join(lines)+'\n')
    return result


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('root',type=Path)
    for r in summarize(parser.parse_args().root)['rows']:
        print(r['scene'],r['variant'],round(r['meanMs'],3),round(r['frameReductionPercent'],2))
