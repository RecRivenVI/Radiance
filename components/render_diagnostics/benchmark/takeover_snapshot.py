"""Freeze exact source bytes, gitlinks and release inputs without staging anything."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[3]
RESULT = ROOT / 'validations/conformance-takeover/result'


def git(*args):
    return subprocess.check_output(['git', '--no-optional-locks', *args], cwd=ROOT)


def is_source_input(name):
    parts = Path(name).parts
    if not parts or parts[0] in ('build', 'instances', 'run', 'dev', 'logs', '.gradle'):
        return False
    if parts[0] == 'validations' and len(parts) > 2 and parts[2] in ('instance', 'result'):
        return False
    if parts[0] in ('components', 'versions') and len(parts) > 2 and parts[2] == 'build':
        return False
    return name != 'local.toml'


def capture(name):
    output = (ROOT / 'validations/conformance-entity_takeover/result' / name
              if name.startswith('source-stage3') else
              ROOT / 'validations/conformance-vanilla/result' / name
              if name.startswith(('source-stage67','source-stage6-','source-instancing')) else RESULT / name)
    if output.with_suffix('.zip').exists():
        raise RuntimeError('Do not overwrite a source checkpoint')
    names = sorted(set(p.decode('utf8') for p in
        git('ls-files', '--cached', '--others', '--exclude-standard', '-z').split(b'\0')
        if p and is_source_input(p.decode('utf8'))))
    files, links = [], []
    for line in git('ls-files', '--stage').decode('utf8').splitlines():
        metadata, path = line.split('\t', 1)
        mode, commit, _ = metadata.split()
        if mode == '160000':
            links.append(dict(path=path, commit=commit))
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output.with_suffix('.zip'), 'w', zipfile.ZIP_DEFLATED) as archive:
        for name in names:
            source = ROOT / name
            if not source.is_file():
                continue
            data = source.read_bytes()
            files.append(dict(path=name, bytes=len(data), sha256=hashlib.sha256(data).hexdigest()))
            archive.writestr(name, data)
    with zipfile.ZipFile(output.with_suffix('.zip')) as archive:
        for record in files:
            if hashlib.sha256(archive.read(record['path'])).hexdigest() != record['sha256']:
                raise RuntimeError('Source readback mismatch: ' + record['path'])
    artifacts = []
    jars = list((ROOT / 'versions/1.21.1-neoforge/build/libs').glob('radiance-*.jar'))
    jars += list((ROOT / 'components/render_diagnostics/build/takeover-candidate').glob('radiance-*.jar'))
    jars += list((ROOT / 'components/render_diagnostics/build/takeover-stage3').glob('*.jar'))
    jars += list((ROOT / 'components/render_diagnostics/build/takeover-stage67').glob('*.jar'))
    jars += list((ROOT / 'components/render_diagnostics/build/takeover-stage6').glob('radiance.jar'))
    for jar in jars:
        if jar.name.endswith('-sources.jar'):
            continue
        members = []
        with zipfile.ZipFile(jar) as archive:
            for entry in archive.namelist():
                if entry == 'core.dll' or entry.endswith('/core.dll') or entry.endswith('.jar'):
                    data = archive.read(entry)
                    members.append(dict(path=entry, bytes=len(data), sha256=hashlib.sha256(data).hexdigest()))
        artifacts.append(dict(path=jar.relative_to(ROOT).as_posix(),
                              role=('stage6-frozen' if 'takeover-stage6' in jar.parts else
                                    'stage67-frozen' if 'takeover-stage67' in jar.parts else
                                    'stage3-frozen' if 'takeover-stage3' in jar.parts else
                                    'acceptance-frozen' if 'takeover-candidate' in jar.parts else 'verification-repack'),
                              bytes=jar.stat().st_size,
                              sha256=hashlib.sha256(jar.read_bytes()).hexdigest(), members=members))
    result = dict(head=git('rev-parse', 'HEAD').decode().strip(),
                  normalization='none; exact source bytes, generated and ignored files excluded',
                  files=files, gitlinks=links,
                  recursiveSubmodules=git('submodule', 'status', '--recursive').decode('utf8'),
                  artifacts=artifacts,
                  zipSha256=hashlib.sha256(output.with_suffix('.zip').read_bytes()).hexdigest())
    output.with_suffix('.json').write_text(json.dumps(result, indent=2)+'\n', encoding='utf8')
    print(json.dumps(dict(path=str(output), files=len(files), gitlinks=len(links),
                         zipSha256=result['zipSha256'], artifacts=artifacts), indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('name', choices=('source-candidate', 'source-final', 'source-stage3-candidate', 'source-stage3-final', 'source-stage67-final', 'source-stage6-final', 'source-instancing-final', 'source-gui-policy-final'))
    capture(parser.parse_args().name)
