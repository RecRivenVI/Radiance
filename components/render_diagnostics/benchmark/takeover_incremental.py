"""Takeover incremental-work attribution and a bounded fixed-city ABBA. Never retries failures."""
from pathlib import Path
import argparse
import json
import shutil

from takeover_experiment import ROOT, TARGET, digest, probe_jar, storage_preflight, write
from takeover_instancing import archive_closed_case
import vanilla_performance as vp

RESULT = ROOT / 'validations/performance-vanilla/result/incremental'
FROZEN = ROOT / 'components/render_diagnostics/build/takeover-incremental'
BASELINE = ROOT / 'components/render_diagnostics/build/gui-policy/radiance.jar'
FIXTURE = ROOT / 'validations/performance-vanilla/result/instancing/city-fixture.zip'


def prepare(final=False):
    frozen = FROZEN.with_name(FROZEN.name+'-final') if final else FROZEN
    occupancy = storage_preflight()
    manifest = frozen / 'manifest.json'
    if manifest.exists():
        raise RuntimeError('Keep the frozen comparison; use a new, explicit experiment after changes')
    products = [p for p in (ROOT/f'versions/{TARGET}/build/libs').glob('*.jar')
                if not p.name.endswith('-sources.jar')]
    if len(products) != 1:
        raise RuntimeError('Ambiguous built production artifact')
    product = products[0]
    probe = probe_jar()
    reserve = product.stat().st_size + probe.stat().st_size
    if occupancy['workspace'] + reserve > 16 * 2**30:
        raise RuntimeError('Insufficient freeze reserve')
    if digest(BASELINE) != '86c1f1b11d330e3f41ef4ca334bab843811991e9ce5be0b7a7043b330bd4ba04':
        raise RuntimeError('Original current-product baseline changed')
    if digest(FIXTURE) != '8a7f468828b8aca4ed51bbd31ce203ee71d82b85284af56a7128baf8f8dc10dc':
        raise RuntimeError('Frozen city changed')
    frozen.mkdir(parents=True, exist_ok=True)
    shutil.copy2(product, frozen / 'radiance.jar')
    shutil.copy2(probe, frozen / 'probe.jar')
    values = dict(baseline=BASELINE,
                  candidate=frozen/'radiance.jar', probe=frozen/'probe.jar', fixture=FIXTURE)
    write(manifest, {key: dict(path=p.relative_to(ROOT).as_posix(), sha256=digest(p)) for key,p in values.items()})


def products(scene=False, final=False):
    frozen = FROZEN.with_name(FROZEN.name+'-final' if final else FROZEN.name+'-scene') if scene or final else FROZEN
    manifest = json.loads((frozen/'manifest.json').read_text())
    paths = {}
    for key, value in manifest.items():
        path = ROOT/value['path']
        if digest(path) != value['sha256']:
            raise RuntimeError('Frozen input changed: '+key)
        paths[key] = path
    return paths


def pairs():
    paths = products()
    progress = RESULT/'paired-progress.json'
    rows = json.loads(progress.read_text()) if progress.exists() else []
    for label in ('A1','B1','B2','A2'):
        if any(row['label']==label for row in rows):
            continue
        case = vp.run('r32-1080', plan='takeover',
                      product=paths['baseline' if label[0]=='A' else 'candidate'],
                      frozen_probe=paths['probe'], fixture=paths['fixture'],
                      group='takeover-incremental-'+label, seconds=20)
        archive_closed_case(case)
        rows.append(dict(label=label, case=case.relative_to(ROOT).as_posix()))
        write(progress, rows)


def report(scene=False):
    paths=products(scene)
    result = RESULT/'scene' if scene else RESULT
    rows = json.loads((result/'paired-progress.json').read_text())
    if {r['label'] for r in rows} != {'A1','B1','B2','A2'} or len(rows)!=4:
        raise RuntimeError('Incomplete comparison; do not pool partial samples')
    runs = {}
    for row in rows:
        case=ROOT/row['case']
        if not json.loads((case/'result.json').read_text())['passed']:
            raise RuntimeError('Failed process in comparison')
        launch = json.loads((case/'launch.json').read_text())
        if (launch['product'] != digest(paths['baseline' if row['label'][0]=='A' else 'candidate'])
                or launch['probe'] != digest(paths['probe'])
                or ROOT/launch['fixture'] != paths['fixture']
                or (launch['width'],launch['height'],launch['distance']) != (1920,1080,32)):
            raise RuntimeError('Mixed product/probe/fixture or configuration in comparison')
        if scene:
            if launch['nativeControls'] != {'MCVR_STATIC_INSTANCE_CACHE':'0' if row['label'][0]=='A' else '1',
                                            'MCVR_CHUNK_SCENE_VERIFY':'0'}:
                raise RuntimeError('Wrong static instance comparison control')
        runs[row['label']]=json.loads((case/'analysis.json').read_text())['windows']
        if set(runs[row['label']]) != {'FRAME_ONLY','TIMED'}:
            raise RuntimeError('Unexpected profiling windows')
    paired=[]
    for a,b in (('A1','B1'),('A2','B2')):
        modes={}
        for mode in ('FRAME_ONLY','TIMED'):
            aw,bw=runs[a][mode],runs[b][mode]
            if aw['sceneState']!=bw['sceneState']:
                raise RuntimeError('Camera/time/weather mismatch')
            keys={
                'frameMeanMs':(aw['intervalMs']['mean'],bw['intervalMs']['mean']),
                'frameP95Ms':(aw['intervalMs']['p95'],bw['intervalMs']['p95']),
                'frameP99Ms':(aw['intervalMs']['p99'],bw['intervalMs']['p99']),
                'cpuActiveMs':(aw['cpuActiveMs']['mean'],bw['cpuActiveMs']['mean']),
                'baseGpuMs':(aw['baseGpu']['meanMs'],bw['baseGpu']['meanMs']),
            }
            if mode=='TIMED':
                for stage in ('ENTITIES','ENTITY_SOURCE_LOOKUP','ENTITY_SOURCE_STATE','ENTITY_POSE_EXTRACT',
                              'ENTITY_PART_BINDINGS','ENTITY_BATCH_PACK','ENTITY_OUTPUT','SUBMIT_PRESENT'):
                    key='java/'+stage
                    keys[key]=tuple(w['stagesMs'][key]['mean'] for w in (aw,bw))
                for key in ('java.optional-source.class-lookups','entity.rigid.instances',
                            'entity.dynamic.blas-inputs','world.tlas.instances','metadata.upload','java.pbr.generated',
                            'chunk.instances.reused','chunk.instances.generated','chunk.instances.cpu-cache'):
                    for field in ('count','logicalBytes'):
                        keys[key+'/'+field]=tuple(w['workPerFrame'].get(key,{}).get(field) for w in (aw,bw))
                if scene:
                    key='0/pt.prepare.static-instance-append'
                    keys[key]=tuple(w['stagesMs'][key]['mean'] for w in (aw,bw))
            modes[mode]={key:dict(baseline=x,candidate=y,delta=None if x is None or y is None else y-x)
                         for key,(x,y) in keys.items()}
        paired.append(dict(baseline=a,candidate=b,modes=modes))
    write(result/'comparison.json',dict(pairs=paired,runs=rows,
          scope='Same frozen city, 32 distance, 1080p, ordinary coverage stochastic, FG off. Process/window means paired; no frame pooling.',
          limitations=['Concurrent workloads are observed per process; GPU counters are whole-card.',
                       'No cuts to entities, geometry, animation, light or quality. Simulation can evolve differently during load.',
                       'CPU/native/GPU inclusive stages overlap and are not summed; logical bytes are not PCIe measurements.']))
    print(json.dumps(paired,ensure_ascii=False))


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('command',choices=('prepare','pairs','report','report-scene','final'))
    args=parser.parse_args()
    if args.command=='final':
        prepare(final=True)
        paths=products(final=True)
        case=vp.run('r32-1080', plan='functional', product=paths['candidate'],
                    frozen_probe=paths['probe'], fixture=paths['fixture'],
                    group='takeover-incremental-final')
        archive_closed_case(case)
        write(RESULT/'final.json',dict(case=case.relative_to(ROOT).as_posix(),passed=True))
    else:
        if args.command=='prepare': prepare()
        elif args.command=='pairs': pairs()
        else: report(args.command=='report-scene')
