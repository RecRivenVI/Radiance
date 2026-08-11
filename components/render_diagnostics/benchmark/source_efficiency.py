"""Fixed city source-cost comparison, reusing the existing packaged performance runner."""
from pathlib import Path
import argparse
import json
import tomllib
import zipfile

import vanilla_performance as city
from takeover_experiment import ROOT, TARGET, WORKSPACE_SOFT_LIMIT_GIB, digest


def run(args):
    # Frozen Native baselines use narrow shader-file opens; keep the owned path short.
    validation = ROOT / 'validations/performance-sources'
    config = tomllib.loads((validation / 'validation.toml').read_text(encoding='utf8'))
    if config['tool'] != 'render_diagnostics' or config['targets'] != [TARGET]:
        raise RuntimeError('Unexpected source comparison configuration')
    city.VALIDATION = validation
    if not args.series or len(args.series)>32 or any(c not in 'abcdefghijklmnopqrstuvwxyz0123456789_-' for c in args.series):
        raise ValueError('Comparison series must be a bounded directory label')
    city.RESULT = validation / 'result' / args.series
    city.CLIENT = validation / 'instance' / TARGET / 'client'
    settings = None
    if args.voxy:
        if args.extra is None: raise ValueError('Voxy comparison requires the paired Extra')
        settings = city.RESULT / 'voxy-settings.zip'
        settings.parent.mkdir(parents=True, exist_ok=True)
        payload = json.dumps(dict(enabled=True, enable_rendering=True, ingest_enabled=True,
            section_render_distance=1, sub_division_size=64, service_threads=2, gpu_debug=False), sort_keys=True)
        if settings.exists():
            with zipfile.ZipFile(settings) as z:
                if z.read('config/voxy-config.json').decode() != payload:
                    raise RuntimeError('Frozen Voxy settings differ')
        else:
            with zipfile.ZipFile(settings, 'w', zipfile.ZIP_DEFLATED) as z:
                z.writestr('config/voxy-config.json', payload)
    return city.run('r32-1080', plan='takeover', product=args.core, group=args.label,
        seconds=20, frozen_probe=args.probe, settings=settings, workspace_limit_gib=args.workspace_limit,
        extra_product=args.extra, input_names=('voxy',) if args.voxy else (), movement=args.moving,
        baseline=dict(label=args.label, core=digest(args.core), extra=digest(args.extra) if args.extra else None,
                      probe=digest(args.probe), frozenCity=digest(city.FIXTURE), voxy=args.voxy,
                      settings=digest(settings) if settings else None, moving=args.moving))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--core', type=Path, required=True)
    parser.add_argument('--extra', type=Path)
    parser.add_argument('--probe', type=Path, required=True)
    parser.add_argument('--label', required=True)
    parser.add_argument('--series', default='ab-routing')
    parser.add_argument('--voxy', action='store_true')
    parser.add_argument('--moving', action='store_true')
    parser.add_argument('--workspace-limit', type=int, default=WORKSPACE_SOFT_LIMIT_GIB)
    run(parser.parse_args())
