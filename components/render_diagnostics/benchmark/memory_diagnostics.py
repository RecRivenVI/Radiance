"""Bounded attribution, independent from both the safety controller and GPU sampling."""
from contextlib import contextmanager
import ctypes as c
import datetime
import json
import subprocess
import time

from memory_guard import GIB, system_memory


def timestamp():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


@contextmanager
def process_memory(pid):
    class Counters(c.Structure):
        _fields_ = [('cb', c.c_ulong), ('PageFaultCount', c.c_ulong)] + [
            (key, c.c_size_t) for key in ('PeakWorkingSetSize', 'WorkingSetSize', 'QuotaPeakPagedPoolUsage',
                'QuotaPagedPoolUsage', 'QuotaPeakNonPagedPoolUsage', 'QuotaNonPagedPoolUsage', 'PagefileUsage',
                'PeakPagefileUsage', 'PrivateUsage')]

    kernel = c.WinDLL('kernel32', use_last_error=True)
    kernel.OpenProcess.argtypes = [c.c_ulong, c.c_int, c.c_ulong]
    kernel.OpenProcess.restype = c.c_void_p
    kernel.CloseHandle.argtypes = [c.c_void_p]
    handle = kernel.OpenProcess(0x410, False, pid)
    if not handle:
        raise c.WinError(c.get_last_error())
    get = c.WinDLL('psapi', use_last_error=True).GetProcessMemoryInfo
    get.argtypes = [c.c_void_p, c.POINTER(Counters), c.c_ulong]

    def read():
        values = Counters()
        values.cb = c.sizeof(values)
        if get(handle, c.byref(values), values.cb):
            return dict(workingSet=values.WorkingSetSize, peakWorkingSet=values.PeakWorkingSetSize,
                        privateBytes=values.PrivateUsage, peakCommit=values.PeakPagefileUsage)
        return None

    try:
        yield read
    finally:
        kernel.CloseHandle(handle)


def largest_private_owners():
    query = ("$rows=@(Get-CimInstance Win32_PerfFormattedData_PerfProc_Process | "
             "Where-Object {$_.IDProcess -gt 0} | Sort-Object PrivateBytes -Descending | "
             "Select-Object -First 12 Name,IDProcess,PrivateBytes); ConvertTo-Json -InputObject $rows -Compress")
    result = subprocess.check_output(['powershell', '-NoProfile', '-Command', query], text=True,
                                     timeout=3, creationflags=subprocess.CREATE_NO_WINDOW)
    return json.loads(result)


def observe(case, stop, pid):
    surveyed = False
    try:
        with process_memory(pid) as read_process, (case / 'memory.jsonl').open('x', encoding='utf8') as stream:
            for _ in range(1200):
                if stop.is_set():
                    break
                system = system_memory()
                stream.write(json.dumps(dict(time=timestamp(), processMemory=read_process(), systemMemory=system)) + '\n')
                stream.flush()
                if not surveyed and min(system['availableCommit'], system['availablePhysical']) < 44 * GIB:
                    surveyed = True
                    try:
                        owners = largest_private_owners()
                        summary = dict(time=timestamp(), otherProcesses=[row for row in owners if row['IDProcess'] != pid])
                    except (OSError, ValueError, subprocess.SubprocessError) as error:
                        summary = dict(time=timestamp(), error=str(error))
                    (case / 'memory-owners.json').write_text(json.dumps(summary, indent=2) + '\n', encoding='utf8')
                stop.wait(1)
    except (OSError, ValueError) as error:
        (case / 'memory-observer-error.json').write_text(json.dumps(dict(error=str(error))) + '\n', encoding='utf8')


def diagnostic_environment(env, level):
    """Use explicit levels for paired runs, without inheriting heavyweight flags."""
    if level not in ('basic', 'resource', 'source'):
        raise ValueError('GPU diagnostics level must be basic, resource or source')
    env.update(MCVR_DEVICE_LOSS_TRACE='1', MCVR_LOCAL_AFTERMATH='0' if level == 'basic' else '1',
               MCVR_AFTERMATH_RESOURCE_ONLY='1' if level == 'resource' else '0',
               MCVR_SHADER_SOURCE_DEBUG='1' if level == 'source' else '0',
               MCVR_GPU_SHADER_EXPORT='0' if level == 'basic' else '1')
    return level != 'basic'


def execution_modes(env):
    return {name: env.get(name, '0') for name in (
        'RADIANCE_SER_MODE', 'RADIANCE_CPU_TOPOLOGY_WORKERS', 'RADIANCE_NATIVE_FRAME_WORKER')}
