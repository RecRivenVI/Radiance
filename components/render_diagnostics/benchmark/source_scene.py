"""Source-owned scene comparison using the already frozen 129 world, mods and loader."""
from pathlib import Path
import argparse
import json
import os
import zipfile

import sublevel_investigation as observation
from takeover_experiment import ROOT, TARGET, EXTRA_ROOT, digest, write, storage_preflight


def run(kind, label, lifecycle=False):
    result = ROOT / 'validations/conformance-takeover/result/source-scene'
    result.mkdir(parents=True, exist_ok=True)
    occupancy = storage_preflight(validation_limit_gib=8)
    # Frozen world is 144 MB, Native/SDK copies 394 MB. Observed bounded evidence/cache
    # stays below 80 MB; reserve 650 MB including readback archives and growth margin.
    reserve = 450_000_000 if kind in ('vanilla', 'factory', 'retirement') else 650_000_000
    if occupancy['validations'] + reserve > 8 * 1024**3 or occupancy['workspace'] + reserve > 16 * 1024**3:
        raise RuntimeError('Insufficient complete-client reserve; preserve evidence and recycle generated copies: '
                           + repr(occupancy))
    baseline_file = ROOT / 'validations/conformance-takeover/result/platform-sublevel/candidate.json'
    baseline = json.loads(baseline_file.read_text(encoding='utf8'))
    if kind in ('vanilla', 'factory', 'retirement'):
        import platform_integration
        if kind == 'retirement':
            probe = next((ROOT / f'versions/{TARGET}/build/intermediates/probe').glob('Radiance-probe-*.jar'))
            with zipfile.ZipFile(probe) as package:
                if ('com/radiance/audit/mixin/PersistentSelectionRegressionMixin.class' not in package.namelist()
                        or b'PersistentSelectionRegressionMixin' not in package.read('radiance-audit.mixins.json')):
                    raise RuntimeError('Build :version:1.21.1-neoforge:probeDevelopmentJar before the retirement regression')
        candidate = 'source-scene-fix' if kind == 'retirement' else 'source-scene'
        core = next((ROOT / f'versions/{TARGET}/build/candidates/{candidate}').glob('*.jar'))
        platform_integration.RESULT = result / 'retirement-fix' if kind == 'retirement' else result
        platform_integration.CLIENT = ROOT / f'validations/conformance-takeover/instance/{TARGET}/s130'
        extra = next((EXTRA_ROOT / f'versions/{TARGET}/build/candidates/source-scene').glob('*.jar'))
        previous = os.environ.get('JAVA_TOOL_OPTIONS')
        try:
            if kind == 'retirement':
                os.environ['JAVA_TOOL_OPTIONS'] = (previous or '') + ' -Dradiance.audit.persistentSelectionRegression=true'
            platform_integration.run('core' if kind == 'vanilla' else 'sources',
                                     core_override=core, extra_override=extra, validation_limit_gib=8)
            if kind == 'retirement':
                cases = sorted(platform_integration.RESULT.glob('sources-*'))
                evidence = json.loads((cases[-1] / 'persistent-selection-regression.json').read_text())
                if not evidence.get('passed'):
                    raise RuntimeError('Selected-and-removed production submission was not verified')
        finally:
            if previous is None:
                os.environ.pop('JAVA_TOOL_OPTIONS', None)
            else:
                os.environ['JAVA_TOOL_OPTIONS'] = previous
        return
    if kind == 'baseline':
        if lifecycle:
            raise RuntimeError('The new lifecycle contract is tested only on its implementing candidate')
        core = Path(baseline['products']['core']['path'])
        extra = Path(baseline['products']['extra']['path'])
        for name, path in (('core', core), ('extra', extra)):
            if digest(path) != baseline['products'][name]['sha256']:
                raise RuntimeError('Protected baseline product changed: ' + name)
    else:
        core = next((ROOT / f'versions/{TARGET}/build/candidates/source-scene').glob('*.jar'))
        extra = next((EXTRA_ROOT / f'versions/{TARGET}/build/candidates/source-scene').glob('*.jar'))
    write(result / (label + '-input.json'), dict(kind=kind, lifecycle=lifecycle,
          baseline=str(baseline_file), fixture=str(observation.BUILD / 'fixture.json'),
          core=dict(path=str(core), sha256=digest(core)), extra=dict(path=str(extra), sha256=digest(extra))))
    observation.RESULT = result
    observation.CLIENT = ROOT / f'validations/conformance-takeover/instance/{TARGET}/s130'
    previous = os.environ.get('JAVA_TOOL_OPTIONS')
    try:
        os.environ['JAVA_TOOL_OPTIONS'] = (previous or '') + ' -Dradiance.audit.sourceSceneLifecycle=' + str(lifecycle).lower()
        observation.run(label, 1920, 1080, core_override=core, extra=extra, trace=lifecycle)
    finally:
        if previous is None:
            os.environ.pop('JAVA_TOOL_OPTIONS', None)
        else:
            os.environ['JAVA_TOOL_OPTIONS'] = previous


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('kind', choices=('baseline', 'candidate', 'vanilla', 'factory', 'retirement'))
    parser.add_argument('--label', required=True)
    parser.add_argument('--lifecycle', action='store_true')
    args = parser.parse_args()
    run(args.kind, args.label, args.lifecycle)
