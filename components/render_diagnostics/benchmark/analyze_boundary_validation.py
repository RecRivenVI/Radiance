"""Functional evidence only. SDK presentation counts are not generated-image inspection."""
from pathlib import Path
from datetime import datetime, timezone
import hashlib
import json
import shutil
import zipfile
import array
import math
import sys

ROOT = Path(__file__).resolve().parents[3]
VALIDATION = ROOT / 'validations/conformance-boundaries'


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def summarize(report):
    print(json.dumps({key: value for key, value in report.items() if key != 'terrainCompletedFrameChains'}, indent=2))
    print('GPU completion -> publication -> completed frame chains:', len(report['terrainCompletedFrameChains']))


def check_fg_inputs(archive):
    with zipfile.ZipFile(archive/'runtime-evidence.zip') as z:
        names = [name for name in z.namelist() if name.endswith('images.tsv') and 'radiance-fg-captures' in name]
        if not names:
            return {'exercised': False}
        base = names[0].rsplit('/', 1)[0]
        formats = {row.split('\t')[0]: int(row.split('\t')[3]) for row in z.read(names[0]).decode().splitlines()}
        if formats != {'final-color': 37, 'hudless': 37, 'ui-alpha': 100}:
            raise RuntimeError('Readback formats changed; do not interpret R32 floats as RGBA bytes')
        values = array.array('f', z.read(base+'/ui-alpha.bin'))
        if sys.byteorder != 'little':
            values.byteswap()
        final = z.read(base+'/final-color.bin')
        hudless = z.read(base+'/hudless.bin')
        if len(final) != len(hudless) or len(final) != len(values)*4:
            raise RuntimeError('Capture extents do not match')
        if not all(math.isfinite(v) and 0 <= v <= 1 for v in values):
            raise RuntimeError('Invalid UI coverage')
        error = max(abs(v-final[i*4+3]/255) for i, v in enumerate(values))
        rgb_error = max(max(abs(final[i*4+c]-hudless[i*4+c]) for c in range(3))
                        for i, v in enumerate(values) if v == 0)
        if error > 3e-7 or rgb_error > 2:
            raise RuntimeError('Real-frame alpha/uncovered RGB contract failed')
        return {'pixels': len(values), 'zero': sum(v == 0 for v in values),
                'fractional': sum(0 < v < 1 for v in values), 'one': sum(v == 1 for v in values),
                'maxAlphaAbsoluteError': error, 'zeroCoverageRgbMaxByteError': rgb_error,
                'scope': 'real-frame inputs only; no generated-image evidence'}


def main():
    instance = VALIDATION / 'instance/1.21.1-neoforge/client'
    result = VALIDATION / 'result'
    if not (instance / 'logs/latest.log').is_file():
        # X-12 recycles the disposable client after evidence is verified. Never relaunch here.
        report = json.loads((result/'analysis.json').read_text(encoding='utf8'))
        archive = (ROOT/report['archive']).resolve()
        if not archive.is_relative_to((result/'runs').resolve()):
            raise RuntimeError('Archive belongs to another validation')
        with zipfile.ZipFile(archive/'runtime-evidence.zip') as z:
            for entry in json.loads((archive/'evidence-index.json').read_text(encoding='utf8')):
                if hashlib.sha256(z.read(entry['path'])).hexdigest() != entry['sha256']:
                    raise RuntimeError('Archived evidence changed: '+entry['path'])
        report['fgInputReadback'] = check_fg_inputs(archive)
        (result/'analysis.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf8')
        summarize(report)
        return
    archive = result / 'runs' / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    archive.mkdir(parents=True, exist_ok=False)
    log = (instance / 'logs/latest.log').read_text(encoding='utf8', errors='replace')
    for path in (instance / 'logs').glob('*.log'):
        shutil.copy2(path, archive / path.name)
    for path in (instance / 'screenshots').glob('boundary-*.png'):
        shutil.copy2(path, archive / path.name)
    # Failure evidence must be kept even when the launcher exits with status zero.
    faults = [word for word in ('VK_ERROR_DEVICE_LOST', 'MCVR fatal', '[FATAL]', 'Crashed!') if word in log]
    if faults:
        (archive / 'failure.json').write_text(json.dumps({'stopGroup': True, 'faults': faults})+'\n')
        raise RuntimeError('Stop runtime group; preserve fault evidence: ' + repr(faults))
    report = json.loads((result / 'boundaries.json').read_text(encoding='utf8'))
    for text in ('TAKEOVER_BOUNDARY world-ready', 'TAKEOVER_BOUNDARY terrain-edit-dispatched',
                 'TAKEOVER_BOUNDARY reload-complete', 'TAKEOVER_BOUNDARY normal-stop',
                 'All dimensions are saved', 'TAKEOVER_OFFSCREEN boundary=boundary-reloaded'):
        if text not in log:
            raise RuntimeError('Missing runtime evidence: '+text)
    if report['stableModel'] == report['changedModel'] or not report['additionalSectionCallbacks']:
        raise RuntimeError('Resource replacement or additional terrain producer not exercised')
    trace = (result / 'chunk-trace.txt').read_text(encoding='utf8')
    events = []
    dropped = 0
    for row in trace.splitlines():
        if row.startswith('clock_ns='):
            dropped = max(dropped, int(row.split('dropped=')[1]))
        parts = row.split(',')
        if len(parts) == 6 and parts[0].isdigit():
            events.append((int(parts[0]), parts[1], *map(int, parts[2:])))
    if dropped:
        raise RuntimeError('Trace overflow; cannot certify publication: '+str(dropped))
    complete = []
    for event in events:
        clock, stage, identity, revision, frame, owner = event
        if stage != 'publish':
            continue
        key = (identity, revision, owner)
        completed = any(e[1] == 'gpu-complete' and (e[2], e[3], e[5]) == key and e[0] <= clock for e in events)
        used = [e for e in events if e[1] == 'tlas-record' and (e[2], e[3], e[5]) == key and e[0] >= clock]
        finished = any(e[1] == 'frame-complete' and any(e[4] == use[4] for use in used) for e in events)
        if completed and used and finished:
            complete.append({'slot': identity, 'revision': revision, 'generation': owner})
    if not complete:
        raise RuntimeError('No GPU-complete -> publish -> scene-record -> completed-frame chain')
    report['terrainCompletedFrameChains'] = complete
    report['terrainTraceDropped'] = dropped
    report['terrainLimit'] = 'Chains prove actual submitted/completed use; not pixel equivalence for every changed block'
    report['fgActivation'] = 'SDK generated-positive intervals observed' if report['fgGeneratedPositiveSamples'] else 'NOT EXERCISED: no generated-positive interval'
    report['fgFocusEvidence'] = 'fgFocusedSamples is Minecraft game focus flag, not GLFW/OS focus; cause of zero generated frames is not established'
    captures = list(instance.glob('radiance-fg-captures/*/frame-*/contract.txt'))
    report['fgRealInputContracts'] = [p.read_text(encoding='utf8') for p in captures]
    report['generatedPixelsInspected'] = False
    report['visualAcceptance'] = 'pending; no user visual feedback for this candidate'
    images = {}
    from PIL import Image
    for name in ('boundary-shared.png', 'boundary-deformed.png', 'boundary-fg-real.png', 'boundary-reloaded.png'):
        with Image.open(archive / name) as im:
            if im.size != (1920, 1080):
                raise RuntimeError('Capture dimensions changed')
            # Unchanged instance A remains a red PT surface after B's independent replacement.
            red = green = 0
            for r, g, b in im.convert('RGB').crop((740, 450, 1180, 630)).get_flattened_data():
                red += r > 100 and r > 2*g and r > 2*b
                green += g > 100 and g > 2*r and g > 2*b
            images[name] = {'sha256': digest(archive/name), 'redPixels': red, 'greenPixels': green}
    report['images'] = images
    for name in ('boundary-shared.png', 'boundary-deformed.png'):
        if images[name]['redPixels'] < 100 or images[name]['greenPixels'] < 100:
            raise RuntimeError('Independent PT panels absent: '+name)
    # Hash every entry before archiving and read every entry back before cleanup is allowed.
    files = [p for p in instance.rglob('*') if p.is_file() and (
        p.suffix.lower() in ('.log', '.json', '.jsonl', '.txt', '.csv', '.xml', '.md', '.png')
        and not any(s in p.parts for s in ('assets', 'libraries', '.radiance', 'saves'))
        or 'radiance-fg-captures' in p.parts or 'gpu-diagnostics' in p.parts)]
    index = []
    with zipfile.ZipFile(archive/'runtime-evidence.zip', 'w', zipfile.ZIP_DEFLATED) as z:
        for path in files:
            name = path.relative_to(instance).as_posix()
            index.append({'path': name, 'bytes': path.stat().st_size, 'sha256': digest(path)})
            z.write(path, name)
    with zipfile.ZipFile(archive/'runtime-evidence.zip') as z:
        for entry in index:
            if hashlib.sha256(z.read(entry['path'])).hexdigest() != entry['sha256']:
                raise RuntimeError('Evidence archive mismatch; do not recycle instance')
    (archive/'evidence-index.json').write_text(json.dumps(index, indent=2)+'\n', encoding='utf8')
    report['evidenceArchiveVerified'] = True
    report['fgInputReadback'] = check_fg_inputs(archive)
    report['archive'] = str(archive.relative_to(ROOT))
    (archive/'boundaries.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf8')
    (result/'analysis.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf8')
    summarize(report)


if __name__ == '__main__':
    main()
