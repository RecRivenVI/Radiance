"""Export a reviewable, unapplied checkpoint without staging or changing Git history."""
from pathlib import Path
import argparse
import hashlib
import io
import json
import os
import subprocess
import zipfile
from takeover_snapshot import is_source_input

ROOT = Path(__file__).resolve().parents[3]


def git(*args):
    return subprocess.check_output(['git','--no-optional-locks',*args],cwd=ROOT)


def main():
    args=argparse.ArgumentParser()
    args.add_argument('stage',choices=('0','1','2','terrain'))
    args.add_argument('--output',type=Path,required=True)
    options=args.parse_args()
    destination=options.output.resolve()
    if destination != Path('D:/Workspaces/Scratches/RadianceUnification').resolve():
        raise ValueError('Use the authorized stage-checkpoint location')
    scope=[]
    if options.stage=='0':
        scope=['components/native_renderer/src/core/render/voxy_layout.hpp',
               'components/native_renderer/src/core/render/voxy_sections.hpp',
               'components/native_renderer/src/core/render/voxy_sections.cpp',
               'components/native_renderer/src/core/middleware/com_radiance_compatibility_voxy_VoxyNative.cpp',
               'components/native_renderer/jni/com_radiance_compatibility_voxy_VoxyNative.h',
               'components/native_renderer/tests/voxy_layout_test.cpp',
               'versions/1.21.1-neoforge/src/main/java/com/radiance/compatibility/voxy',
               'versions/1.21.1-neoforge/src/test/java/com/radiance/compatibility/voxy',
               'documents/reference/registry-takeover_coverage.md']
    elif options.stage=='1':
        scope=['versions/1.21.1-neoforge/src/main/java/com/radiance/render/contract',
               'versions/1.21.1-neoforge/src/test/java/com/radiance/render/contract/TakeoverSubmissionTest.java',
               'versions/1.21.1-neoforge/src/main/java/com/radiance/client/vertex/RigidModelCapture.java',
               'versions/1.21.1-neoforge/src/test/java/com/radiance/client/vertex/CapturedDrawBoundaryTest.java',
               'components/native_renderer/src/core/render/scene_resource_version.hpp',
               'components/native_renderer/src/core/render/chunks.cpp',
               'components/native_renderer/src/core/render/chunks.hpp',
               'components/native_renderer/tests/chunk_scene_metadata_test.cpp']
    elif options.stage=='2':
        scope=['.gitignore',
               'versions/1.21.1-neoforge/src/main/java/com/radiance/client/vertex',
               'versions/1.21.1-neoforge/src/main/java/com/radiance/client/render/StaticDrawStates.java',
               'versions/1.21.1-neoforge/src/main/java/com/radiance/client/proxy/world/EntityProxy.java',
               'versions/1.21.1-neoforge/src/main/java/com/radiance/mixin_related/MixinPlugin.java',
               'versions/1.21.1-neoforge/src/main/java/com/radiance/mixins',
               'versions/1.21.1-neoforge/src/main/resources/radiance.mixins.json',
               'versions/1.21.1-neoforge/src/test/java/com/radiance/client/vertex',
               'components/render_diagnostics/benchmark/takeover_experiment.py',
               'components/render_diagnostics/benchmark/prepare_takeover_stages.py',
               'validations/conformance-takeover','validations/performance-takeover',
               'documents/reference/registry-takeover_coverage.md',
               'documents/research/experiment-takeover_stages.md']
        scope += ['components/render_diagnostics/build.gradle.kts',
                  'components/render_diagnostics/benchmark',
                  'versions/1.21.1-neoforge/src/probe/java',
                  'versions/1.21.1-neoforge/src/probe/resources/radiance-audit.mixins.json']
    else:
        scope=['components/native_renderer/src/core/render/scene_resource_version.hpp',
               'components/native_renderer/src/core/render/chunks.cpp',
               'components/native_renderer/src/core/render/chunks.hpp',
               'components/native_renderer/tests/chunk_scene_metadata_test.cpp',
               'versions/1.21.1-neoforge/src/probe/java/com/radiance/audit/TakeoverStageProbe.java',
               'components/render_diagnostics/benchmark/test_takeover_experiment.py',
               'components/render_diagnostics/benchmark/takeover_experiment.py',
               'validations/conformance-takeover']
    extra=['--',*scope] if scope else []
    tracked=git('diff','--no-ext-diff','--binary','HEAD',*extra)
    untracked=[p.decode('utf8') for p in git('ls-files','--others','--exclude-standard','-z',*extra).split(b'\0')
               if p and is_source_input(p.decode('utf8'))]
    payload=io.BytesIO()
    manifest={'stage':options.stage,'baseHead':git('rev-parse','HEAD').decode().strip(),
              'scope':scope,'restoreNote':'Scoped review delta; complete prior dirty inputs and final state are in validations/conformance-takeover/result/source-final.zip',
              'trackedPatchSha256':hashlib.sha256(tracked).hexdigest(),'untracked':{}}
    with zipfile.ZipFile(payload,'w',zipfile.ZIP_DEFLATED) as z:
        z.writestr('tracked.patch',tracked)
        for name in untracked:
            data=(ROOT/name).read_bytes()
            manifest['untracked'][name]=hashlib.sha256(data).hexdigest()
            z.writestr('untracked/'+name,data)
        z.writestr('manifest.json',json.dumps(manifest,indent=2)+'\n')
    with zipfile.ZipFile(io.BytesIO(payload.getvalue())) as z:
        if hashlib.sha256(z.read('tracked.patch')).hexdigest()!=manifest['trackedPatchSha256']:
            raise RuntimeError('Checkpoint patch verification failed')
        for name,expected in manifest['untracked'].items():
            if hashlib.sha256(z.read('untracked/'+name)).hexdigest()!=expected:
                raise RuntimeError('Checkpoint source verification failed')
    external=[Path('D:/Workspaces/Artifacts/Radiance'),
              *(p for p in Path('D:/Workspaces/Scratches').glob('Radiance*') if p.is_dir() and not p.is_junction())]
    size=0
    for directory in external:
        for current,dirs,files in os.walk(directory,followlinks=False):
            dirs[:]=[d for d in dirs if not (Path(current)/d).is_junction() and not (Path(current)/d).is_symlink()]
            size+=sum((Path(current)/f).stat().st_size for f in files if not (Path(current)/f).is_symlink())
    if size+len(payload.getvalue())>8*1024**3:
        raise RuntimeError('External hard limit would be exceeded; checkpoint remains unwritten')
    destination.mkdir(exist_ok=True)
    target=destination/f'takeover-stage-{options.stage}.zip'
    if target.exists(): raise RuntimeError('Do not overwrite a prior checkpoint')
    target.write_bytes(payload.getvalue())
    print(json.dumps(dict(path=str(target),bytes=target.stat().st_size,sha256=hashlib.sha256(payload.getvalue()).hexdigest(),
                          baseHead=manifest['baseHead'],verified=True)))


if __name__=='__main__': main()
