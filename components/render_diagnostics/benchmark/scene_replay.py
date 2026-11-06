"""Capture an owned city scene, then run bounded standalone shader A/B windows."""
from pathlib import Path
import argparse
import csv
import hashlib
import json
import os
import shutil
import subprocess
import sys
import threading
import zipfile

import memory_diagnostics
import memory_guard
from takeover_experiment import ROOT, digest, write

NATIVE = ROOT / 'components/native_renderer'


def check_global_stop(result):
    return memory_guard.check_global_stop(ROOT, result)


def prepare(jar, snapshot, extra=None):
    jar = jar.resolve()
    snapshot = snapshot.resolve()
    if snapshot.exists():
        raise RuntimeError('Snapshot directory already exists; preserve it or recycle explicitly')
    if not snapshot.is_relative_to(ROOT / 'validations'):
        raise ValueError('Use an owned validation result directory for the snapshot')
    snapshot.mkdir(parents=True)
    with zipfile.ZipFile(jar) as product:
        for entry in product.infolist():
            if entry.is_dir() or not entry.filename.startswith(('shaders/', 'dlss/')):
                continue
            path = snapshot / 'runtime' / entry.filename
            if not path.resolve().is_relative_to(snapshot / 'runtime'):
                raise RuntimeError('Runtime archive path escapes snapshot')
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(product.read(entry))
        native_hash = hashlib.sha256(product.read('core.dll')).hexdigest()
    identity = dict(product=str(jar.relative_to(ROOT)), productSha256=digest(jar), nativeSha256=native_hash,
                    sourceRevision=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
                    extra=None if extra is None else dict(path=str(extra), sha256=digest(extra)),
                    shaderPacks={p.relative_to(snapshot).as_posix(): digest(p)
                                 for p in (snapshot / 'runtime/shaders/world/ray_tracing').glob('*.zip')})
    write(snapshot / 'capture-identity.json', identity)
    if extra is not None:
        (snapshot / 'inputs').mkdir()
        shutil.copyfile(extra, snapshot / 'inputs/extra.jar')
    print('Prepared:', snapshot, flush=True)
    print('$env:RADIANCE_SCENE_CAPTURE = ' + repr(str(snapshot)))
    print("$env:RADIANCE_SCENE_CAPTURE_MAX_MIB = '4096'")
    print('Start the matching owned client, then create capture.request after its timed windows.')


def distribution(values):
    if not values:
        return None
    values = sorted(values)
    return dict(samples=len(values), mean=sum(values) / len(values),
                p50=values[int((len(values)-1)*.50)], p95=values[int((len(values)-1)*.95)],
                p99=values[int((len(values)-1)*.99)], maximum=values[-1])


def report(output):
    metadata = json.loads((output / 'result.json').read_text(encoding='utf8'))
    with (output / 'frames.csv').open(newline='') as stream:
        frames = {int(row['frame']): row for row in csv.DictReader(stream)}
    windows = {}
    for window in metadata['windows']:
        name = window['name']
        selected = {f for f, row in frames.items() if row['window'] == name and row['warmup'] == '0'}
        windows[name] = dict(prepareMs=window['prepare_ms'], route=window['route'],
                             pipelinePrepareMs=window.get('pipeline_prepare_ms'),
                             scenePrepareMs=window.get('scene_prepare_ms'), warmupMs=window.get('warmup_ms'),
                             frameMs=distribution([float(frames[f]['cpu_frame_ms']) for f in selected]), stages={})
        if 'resources_at_start' in window and 'resources_at_end' in window:
            before, after = window['resources_at_start'], window['resources_at_end']
            windows[name]['resourceEndpoints'] = dict(start=before, end=after)
            if selected and 'process_cpu_100ns' in before and 'process_cpu_100ns' in after:
                windows[name]['processCpuPerFrameMs'] = (after['process_cpu_100ns'] - before['process_cpu_100ns']) / (10000 * len(selected))
    with (output / 'gpu.csv').open(newline='') as stream:
        for row in csv.DictReader(stream):
            frame = frames.get(int(row['frame']))
            if frame is None or frame['warmup'] != '0':
                continue
            key = f"{row['domain']}:{row['stage']}"
            windows[frame['window']]['stages'].setdefault(key, []).append(float(row['gpu_ms']))
    for window in windows.values():
        window['stages'] = {k: distribution(v) for k, v in window['stages'].items()}
    host_path = output / 'host.csv'
    if host_path.is_file():
        for window in windows.values():
            window['hostStages'] = {}
        with host_path.open(newline='') as stream:
            for row in csv.DictReader(stream):
                frame = frames.get(int(row['frame']))
                if frame is not None and frame['warmup'] == '0':
                    stages = windows[frame['window']]['hostStages']
                    stages.setdefault(row['stage'], []).append(float(row['host_ms']))
        for window in windows.values():
            window['hostStages'] = {k: distribution(v) for k, v in window['hostStages'].items()}
    write(output / 'analysis.json', dict(windows=windows, limitations=[
        'CPU frame intervals are replay caller/present intervals, not game CPU costs.',
        'Process CPU sums work across threads; resource endpoints are not in-window peaks.',
        'GPU spans overlap/nest and must not be summed.',
        'Warmup frames excluded. Preparation includes shader compilation and driver creation.',
        'Static snapshot has no Java simulation, streaming, GUI or dynamic source production.']))
    print(json.dumps(windows, indent=2))


def images(snapshot, output):
    import numpy as np
    from PIL import Image
    metadata = json.loads((output / 'result.json').read_text(encoding='utf8'))
    width, height = metadata['width'], metadata['height']
    for window in metadata['windows']:
        file = output / (window['name'] + '.bin')
        data = file.read_bytes()
        if window['image_format'] == 97:
            values = np.frombuffer(data, dtype='<f2').reshape(height, width, 4)
            if not np.all(np.isfinite(values[:, :, :3])):
                raise ValueError('Replay display canvas contains non-finite RGB')
            pixels = (np.clip(values.astype('f4'), 0, 1) * 255 + .5).astype('u1')
        elif window['image_format'] in (37, 43):
            pixels = np.frombuffer(data, dtype='u1').reshape(height, width, 4)
        else:
            raise ValueError('No PNG conversion for replay image format ' + str(window['image_format']))
        Image.fromarray(pixels).save(file.with_suffix('.png'))
        if window.get('image_samples', 1) > 1:
            average = output / (window['name'] + '-average.bin')
            values = np.fromfile(average, dtype='<f4').reshape(height, width, 4)
            if not np.all(np.isfinite(values[:, :, :3])):
                raise ValueError('Replay accumulated canvas contains non-finite RGB')
            pixels = (np.clip(values, 0, 1) * 255 + .5).astype('u1')
            Image.fromarray(pixels).save(average.with_suffix('.png'))
    scene = json.loads((snapshot / 'scene.json').read_text(encoding='utf8'))
    image = scene['reference_image']
    with zipfile.ZipFile(snapshot / 'blobs.zip') as archive:
        data = archive.read(image['file'])
    if image['format'] == 97:  # VK_FORMAT_R16G16B16A16_SFLOAT, display canvas (not HDR scanout)
        pixels = np.clip(np.frombuffer(data, dtype='<f2').reshape(height, width, 4), 0, 1)
        pixels = (pixels.astype('f4') * 255 + .5).astype('u1')
    elif image['format'] in (37, 43):
        pixels = np.frombuffer(data, dtype='u1').reshape(height, width, 4)
    else:
        raise ValueError('No reference PNG conversion for Vulkan format ' + str(image['format']))
    Image.fromarray(pixels).save(output / 'game-reference.png')

    repeated = repeated_image_differences(output, metadata['windows'])
    if repeated:
        path = output / 'analysis.json'
        analysis = json.loads(path.read_text(encoding='utf8'))
        analysis['repeatedImageDifferences'] = repeated
        write(path, analysis)


def repeated_image_differences(output, windows):
    from PIL import Image, ImageChops, ImageStat

    controls = {}
    differences = []
    for window in windows:
        key = tuple(window[k] for k in ('shader_pack', 'route', 'warmup_frames', 'timed_frames', 'image_format'))
        key += (window.get('seed_offset', 0), window.get('image_samples', 1))
        name = window['name']
        control = controls.setdefault(key, name)
        if control == name:
            continue
        with Image.open(output / (control + '.png')) as first, Image.open(output / (name + '.png')) as other:
            delta = ImageChops.difference(first.convert('RGB'), other.convert('RGB'))
        differences.append(dict(control=control, window=name, meanAbsoluteRgb8=ImageStat.Stat(delta).mean))
    return differences


def replay(core, snapshot, plan, output):
    # Preserve Windows SUBST aliases used to keep shader extraction paths short.
    core, snapshot, plan, output = (p.absolute() for p in (core, snapshot, plan, output))
    loader = NATIVE / 'build/v/src/core/RelWithDebInfo/mcvr_scene_replay.exe'
    if output.exists():
        raise RuntimeError('Output already exists; failed evidence is never overwritten')
    campaign = ROOT / 'validations/performance-scene_replay/result'
    if not output.resolve().is_relative_to(campaign.resolve()):
        raise ValueError('Replay output must be in its owned validation result directory')
    approved = check_global_stop(output.parent)
    stop = output.parent / 'STOP.json'
    if stop.exists() and stop.resolve() not in approved:
        raise RuntimeError('Previous native failure retained; no automatic retry: ' + str(stop))
    output.parent.mkdir(parents=True, exist_ok=True)
    memory_guard.physical_reserve(output.parent, 1024**3)
    host = output.parent / (output.name + '-host')
    host.mkdir()
    windows = json.loads(plan.read_text(encoding='utf8'))
    write(output.parent / (output.name + '-inputs.json'), dict(core=str(core), coreSha256=digest(core),
          snapshot=str(snapshot), plan=windows,
          shaderPacks={str(Path(w['shader_pack']).resolve()): digest(Path(w['shader_pack'])) for w in windows}))
    log_path = output.parent / (output.name + '.log')
    with log_path.open('x', encoding='utf8') as log, memory_guard.guarded_process(
            host, [str(loader), str(core), str(snapshot), str(plan), str(output)],
            cwd=core.parent, stdout=log, stderr=subprocess.STDOUT) as process:
        from renderer_campaign import observe_wddm
        from vanilla_performance import monitor

        write(host / 'process.json', dict(pid=process.pid))
        stop_observer = threading.Event()
        observers = [threading.Thread(target=target, args=args, daemon=True) for target, args in (
            (monitor, (host, stop_observer, process.pid)),
            (memory_diagnostics.observe, (host, stop_observer, process.pid)),
            (observe_wddm, (host, stop_observer)))]
        for observer in observers:
            observer.start()
        try:
            process.wait()
        finally:
            stop_observer.set()
            for observer in observers:
                observer.join(timeout=16)
    failure_record = output / 'failure.json'
    with log_path.open(encoding='utf8', errors='replace') as log:
        device_lost = (failure_record.exists() and json.loads(failure_record.read_text(encoding='utf8')).get('deviceLost')
                       or any(memory_guard.device_lost(line) for line in log))
    if process.returncode or device_lost:
        failure = dict(exitCode=process.returncode, core=str(core), coreSha256=digest(core),
                       snapshot=str(snapshot), output=str(output), automaticRetry=False)
        fatal = process.returncode not in (1, 2, 3, 4) or device_lost
        if device_lost:
            write(host / 'DEVICE_LOST.json', failure)
        write(stop if fatal and not stop.exists() else host / 'failure.json', failure)
        raise RuntimeError('Native replay failed; evidence retained, no automatic retry; fatal=' + str(fatal))
    report(output)
    images(snapshot, output)


def capture_city(jar, snapshot, extra, fixture, settings):
    import vanilla_performance as performance
    from renderer_campaign import observe_wddm
    from takeover_experiment import probe_jar, storage_preflight, WORKSPACE_HARD_LIMIT_GIB
    check_global_stop(ROOT / 'validations/performance-scene_replay/result')
    prepare(jar, snapshot, extra)
    # Check actual occupancy against the soft limit. The existing runner also
    # reserves disposable client bytes; that projection must obey the hard cap.
    storage_preflight()
    print('Storage preflight complete; preparing the owned game capture.', flush=True)
    validation = ROOT / 'validations/performance-scene_replay'
    performance.VALIDATION = validation
    performance.RESULT = validation / 'result'
    performance.CLIENT = validation / 'instance/1.21.1-neoforge/sr'
    keys = ('RADIANCE_SCENE_CAPTURE', 'RADIANCE_SCENE_CAPTURE_MAX_MIB')
    saved = {key: os.environ.get(key) for key in keys}
    original_monitor = performance.monitor
    def observe(case, stop, pid, jcmd=None):
        def process_vram():
            try:
                observe_wddm(case, stop)
            except (OSError, ValueError, subprocess.SubprocessError) as error:
                write(case / 'wddm-error.json', dict(error=str(error)))
        observer = threading.Thread(target=process_vram, daemon=True)
        observer.start()
        try:
            original_monitor(case, stop, pid, jcmd)
        finally:
            observer.join(timeout=11)
    try:
        performance.monitor = observe
        os.environ['RADIANCE_SCENE_CAPTURE'] = str(snapshot.resolve())
        os.environ['RADIANCE_SCENE_CAPTURE_MAX_MIB'] = '4096'
        case = performance.run('r32-1440', plan='takeover', product=jar, group='replay-game', seconds=10,
                               frozen_probe=probe_jar(), fixture=fixture, settings=settings,
                               memory_max='16G', memory_min='512M',
                               extra_product=snapshot / 'inputs/extra.jar' if extra else None,
                               input_names=('create', 'sable', 'aeronautics_bundled') if extra else (),
                               movement=False, aftermath=True, workspace_limit_gib=WORKSPACE_HARD_LIMIT_GIB)
        if not (snapshot / 'scene.json').is_file():
            raise RuntimeError('The game finished without a published native snapshot')
        return case
    finally:
        performance.monitor = original_monitor
        for key, value in saved.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value


def capture_small(jar, snapshot, extra, settings):
    import world_materials
    check_global_stop(ROOT / 'validations/performance-scene_replay/result')
    prepare(jar, snapshot, extra)
    validation = ROOT / 'validations/performance-scene_replay'
    world_materials.VALIDATION = validation
    world_materials.RESULT = validation / 'result'
    world_materials.CLIENT = validation / 'instance/1.21.1-neoforge/small'
    keys = ('RADIANCE_SCENE_CAPTURE', 'RADIANCE_SCENE_CAPTURE_MAX_MIB')
    saved = {key: os.environ.get(key) for key in keys}
    try:
        os.environ['RADIANCE_SCENE_CAPTURE'] = str(snapshot.resolve())
        os.environ['RADIANCE_SCENE_CAPTURE_MAX_MIB'] = '4096'
        world_materials.run('media', 'replay-small', jar, extra, settings_archive=settings)
        if not (snapshot / 'scene.json').is_file():
            raise RuntimeError('Small fixture completed without a published snapshot')
    finally:
        for key, value in saved.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    prepare_parser = commands.add_parser('prepare')
    prepare_parser.add_argument('--jar', type=Path, required=True)
    prepare_parser.add_argument('--snapshot', type=Path, required=True)
    prepare_parser.add_argument('--extra', type=Path)
    run = commands.add_parser('replay')
    run.add_argument('--core', type=Path, default=NATIVE / 'build/runtime/scene_replay_core.dll')
    run.add_argument('--snapshot', type=Path, required=True)
    run.add_argument('--plan', type=Path, required=True)
    run.add_argument('--output', type=Path, required=True)
    analyze = commands.add_parser('report')
    analyze.add_argument('--output', type=Path, required=True)
    capture = commands.add_parser('capture-city')
    capture.add_argument('--jar', type=Path, required=True)
    capture.add_argument('--snapshot', type=Path, required=True)
    capture.add_argument('--extra', type=Path)
    capture.add_argument('--fixture', type=Path, required=True)
    capture.add_argument('--settings', type=Path, required=True)
    small = commands.add_parser('capture-small')
    small.add_argument('--jar', type=Path, required=True)
    small.add_argument('--snapshot', type=Path, required=True)
    small.add_argument('--extra', type=Path)
    small.add_argument('--settings', type=Path, required=True)
    args = parser.parse_args()
    if args.command == 'prepare':
        prepare(args.jar, args.snapshot, args.extra)
    elif args.command == 'replay':
        replay(args.core, args.snapshot, args.plan, args.output)
    elif args.command == 'capture-city':
        capture_city(args.jar, args.snapshot, args.extra, args.fixture, args.settings)
    elif args.command == 'capture-small':
        capture_small(args.jar, args.snapshot, args.extra, args.settings)
    else:
        report(args.output)


if __name__ == '__main__':
    main()
