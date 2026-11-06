"""Independent, fail-closed memory protection for the owned Windows GPU test process."""
from collections import deque
from contextlib import contextmanager
import ctypes as c
import datetime
from functools import cache
import hashlib
import json
import os
from pathlib import Path
import queue
import re
import subprocess
import threading
import time


GIB = 1024**3
FLOORS = {'availableCommit': 24 * GIB, 'availablePhysical': 20 * GIB}
SAMPLE_SECONDS = .25
SAMPLE_TIMEOUT = 1.25
EXIT_TIMEOUT = 2
STOP_NAMES = {'SYSTEM_MEMORY_STOP.json', 'DEVICE_LOST.json', 'STOP.json'}
GUARDED_VALIDATIONS = ('performance-scene_replay', 'performance-vanilla', 'conformance-renderer',
                       'conformance-entity_takeover', 'conformance-takeover')
RECOVERED_EVENT = 'execution151/ser-ext-diag-3-r32-1440-20261109-084221'
RECOVERED_MARKERS = {
    'execution151/STOP.json': 'a8975d58e11698277b1c932c1b8f722de22ed705aaf672f9a7b3873cf943274f',
    f'{RECOVERED_EVENT}/SYSTEM_MEMORY_STOP.json': '06d9204a3546e7d55caa839b31310d00e725ac82d12181eb2fdfc0c164ab5c27',
}
# Each user-authorized recovery names one retained event; a registration only clears its exact markers.
RECOVERIES = {
    RECOVERED_EVENT: RECOVERED_MARKERS,
    'tradeoff/city-ser': {
        'tradeoff/STOP.json': '7b6cf3fde85b1d5722c505f669ee31116ef96dac6d75c436f34aa297ec658531',
        'tradeoff/city-ser-host/DEVICE_LOST.json': '7b6cf3fde85b1d5722c505f669ee31116ef96dac6d75c436f34aa297ec658531',
    },
    'tradeoff/city-serc': {
        'tradeoff/city-serc-host/DEVICE_LOST.json': 'd93cc51119eabf8d917c8c583cf5b2c9c29a2db077fc023b1faff1ccec3ab581',
    },
    'architecture-20261112-j1/city/j1-city-a2-r32-1440-20261112-014144': {
        'architecture-20261112-j1/city/j1-city-a2-r32-1440-20261112-014144/SYSTEM_MEMORY_STOP.json': 'b0c6f57a5546601817e298f00711981ef4bb537a660b7232751967005a9c39d2',
    },
}


@cache
def performance_reader():
    class PerformanceInformation(c.Structure):
        _fields_ = [('cb', c.c_ulong)] + [(key, c.c_size_t) for key in (
            'CommitTotal', 'CommitLimit', 'CommitPeak', 'PhysicalTotal', 'PhysicalAvailable',
            'SystemCache', 'KernelTotal', 'KernelPaged', 'KernelNonpaged', 'PageSize')
        ] + [(key, c.c_ulong) for key in ('HandleCount', 'ProcessCount', 'ThreadCount')]

    read = c.WinDLL('psapi', use_last_error=True).GetPerformanceInfo
    read.argtypes = [c.POINTER(PerformanceInformation), c.c_ulong]
    return read, PerformanceInformation


def system_memory():
    read, structure = performance_reader()
    info = structure()
    if not read(c.byref(info), c.sizeof(info)):
        raise c.WinError(c.get_last_error())
    return dict(availableCommit=(info.CommitLimit - info.CommitTotal) * info.PageSize,
                committed=info.CommitTotal * info.PageSize, totalCommit=info.CommitLimit * info.PageSize,
                availablePhysical=info.PhysicalAvailable * info.PageSize, totalPhysical=info.PhysicalTotal * info.PageSize,
                kernelPaged=info.KernelPaged * info.PageSize, kernelNonpaged=info.KernelNonpaged * info.PageSize)


def check_global_stop(root, result):
    """New output paths still share the scene replay campaign's stop boundary."""
    campaign = (root / 'validations/performance-scene_replay/result').resolve()
    approved = set()
    for recovery in filter(None, os.environ.get('RADIANCE_MEMORY_RECOVERY', '').split(os.pathsep)):
        registration = json.loads(Path(recovery).read_text(encoding='utf8'))
        markers = RECOVERIES.get(registration['event'])
        if markers is None or registration['markers'] != markers:
            raise RuntimeError('Recovery must identify an authorized event and its exact markers')
        for relative, expected in markers.items():
            path = campaign / relative
            if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
                raise RuntimeError(f'Retained stop differs from the recovery registration: {path}')
            approved.add(path)
    areas = [(root / 'validations' / name / 'result').resolve() for name in GUARDED_VALIDATIONS]
    result = result.resolve()
    if not any(result.is_relative_to(area) for area in areas):
        areas.append(result)
    for area in areas:
        for name in STOP_NAMES:
            for path in area.rglob(name):
                if path in approved:
                    continue
                if name != 'STOP.json' or json.loads(path.read_bytes()).get('deviceLost'):
                    raise RuntimeError(f'Global stop retained: {path}')
    return approved


def device_lost(log):
    return 'VK_ERROR_DEVICE_LOST' in log or re.search(r'\bVkResult\s*=\s*-4\b', log) is not None


class Pressure:
    def __init__(self):
        self.samples = deque(maxlen=33)

    def observe(self, now, values):
        self.samples.append(dict(time=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                                 monotonic=now, **values))
        for key, floor in FLOORS.items():
            if values[key] <= floor:
                return f'{key} absolute floor'
            if len(self.samples) < 5 or values[key] > 40 * GIB:
                continue
            recent = list(self.samples)[-5:]
            span = now - recent[0]['monotonic']
            losses = [a[key] - b[key] for a, b in zip(recent, recent[1:])]
            rate = sum(losses) / max(span, SAMPLE_SECONDS)
            if all(loss > 0 for loss in losses) and rate >= 3 * GIB and values[key] - 3 * rate <= floor:
                return f'{key} sustained decline'
        return None


@contextmanager
def guarded_process(case, args, *, read=system_memory, **popen_options):
    """The control thread has no GPU calls, filesystem I/O or diagnostic waits before termination."""
    stopped = threading.Event()
    ready = threading.Event()
    readings = queue.Queue(maxsize=1)
    launch_lock = threading.Lock()
    pressure = Pressure()
    process = None
    failure = None

    def sample():
        while not stopped.is_set():
            try:
                reading = read()
            except Exception as error:
                reading = error
            try:
                readings.put(reading, timeout=SAMPLE_TIMEOUT)
            except queue.Full:
                return
            if isinstance(reading, Exception):
                return
            stopped.wait(SAMPLE_SECONDS)

    def control():
        nonlocal failure
        try:
            while not stopped.is_set():
                reading = readings.get(timeout=SAMPLE_TIMEOUT)
                if isinstance(reading, Exception):
                    raise reading
                reason = pressure.observe(time.monotonic(), reading)
                if reason:
                    raise RuntimeError(reason)
                ready.set()
        except queue.Empty:
            stop_owned('memory sampler heartbeat expired')
        except BaseException as error:
            stop_owned(str(error) or type(error).__name__)

    def stop_owned(reason):
        nonlocal failure
        with launch_lock:
            if stopped.is_set() or (process is not None and process.poll() is not None):
                return
            failure = dict(reason=reason, automaticRetry=False, pid=process.pid if process else None,
                           floors=FLOORS, samples=list(pressure.samples))
            ready.set()
            if process is not None:
                # On Windows Popen.kill() uses the handle returned when this child was created.
                try:
                    process.kill()
                    process.wait(timeout=EXIT_TIMEOUT)
                except (OSError, subprocess.TimeoutExpired) as error:
                    failure['terminationError'] = repr(error)
        with (case / 'SYSTEM_MEMORY_STOP.json').open('x', encoding='utf8') as report:
            json.dump(failure, report, indent=2)
            report.write('\n')

    threading.Thread(target=sample, name='radiance-memory-sample', daemon=True).start()
    controller = threading.Thread(target=control, name='radiance-memory-control', daemon=True)
    controller.start()
    try:
        if not ready.wait(SAMPLE_TIMEOUT + .5):
            raise RuntimeError('Memory controller failed to arm; no process launched')
        with launch_lock:
            if failure:
                raise RuntimeError(failure['reason'])
            process = subprocess.Popen(args, **popen_options)
        yield process
    finally:
        stopped.set()
        controller.join(timeout=SAMPLE_TIMEOUT + EXIT_TIMEOUT + .5)
        if process is not None and process.poll() is None:
            process.kill()
            process.wait(timeout=EXIT_TIMEOUT)
        if failure:
            raise RuntimeError(f'Memory protection stopped the run: {failure["reason"]}')


def physical_reserve(path, planned_bytes):
    import shutil

    if shutil.disk_usage(Path(path)).free < planned_bytes + 12 * GIB:
        raise RuntimeError('Insufficient physical disk reserve for the isolated run')
