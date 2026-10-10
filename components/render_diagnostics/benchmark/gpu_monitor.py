"""Read-only installed-driver NVML sampler. No CUDA context, clock changes or SDK bundle.

Memory is a device-wide WDDM observation, not process-exclusive/allocator memory.
https://docs.nvidia.com/deploy/nvml-api/api/group__nvmlDeviceQueries.html
"""
import argparse
import csv
import ctypes as c
import datetime
import json
import os
from pathlib import Path
import time


class Memory(c.Structure):
    _fields_=[('total',c.c_ulonglong),('free',c.c_ulonglong),('used',c.c_ulonglong)]


class Utilization(c.Structure):
    _fields_=[('gpu',c.c_uint),('memory',c.c_uint)]


class Reader:
    def __init__(self):
        # Do not search the game directory for a DLL with the same name.
        self.api=c.CDLL(str(Path(os.environ['WINDIR'])/'System32/nvml.dll'))
        def bind(name,args):
            fn=getattr(self.api,name);fn.argtypes=args;fn.restype=c.c_int;return fn
        self.init=bind('nvmlInit_v2',[])
        self.shutdown=bind('nvmlShutdown',[])
        self.handle_fn=bind('nvmlDeviceGetHandleByIndex_v2',[c.c_uint,c.POINTER(c.c_void_p)])
        self.memory=bind('nvmlDeviceGetMemoryInfo',[c.c_void_p,c.POINTER(Memory)])
        self.util=bind('nvmlDeviceGetUtilizationRates',[c.c_void_p,c.POINTER(Utilization)])
        self.clock=bind('nvmlDeviceGetClockInfo',[c.c_void_p,c.c_uint,c.POINTER(c.c_uint)])
        self.check(self.init(),'nvmlInit_v2');self.handle=c.c_void_p()
        try:self.check(self.handle_fn(0,c.byref(self.handle)),'nvmlDeviceGetHandleByIndex_v2')
        except BaseException:self.shutdown();raise

    @staticmethod
    def check(code,operation):
        if code:raise RuntimeError(f'{operation}: NVML error {code}')

    def sample(self):
        mem=Memory();util=Utilization();graphics=c.c_uint();memory=c.c_uint()
        self.check(self.memory(self.handle,c.byref(mem)),'nvmlDeviceGetMemoryInfo')
        self.check(self.util(self.handle,c.byref(util)),'nvmlDeviceGetUtilizationRates')
        self.check(self.clock(self.handle,0,c.byref(graphics)),'nvmlDeviceGetClockInfo(graphics)')
        self.check(self.clock(self.handle,2,c.byref(memory)),'nvmlDeviceGetClockInfo(memory)')
        if not 0<=mem.used<=mem.total or not 0<=util.gpu<=100:raise RuntimeError('Invalid NVML sample')
        return [mem.used//(1024*1024),mem.total//(1024*1024),util.gpu,graphics.value,memory.value]

    def close(self):self.shutdown()


def collect(reader,stream,stopped,duration,now=time.monotonic,sleep=time.sleep):
    writer=csv.writer(stream)
    writer.writerow(['timestamp',' memory.used [MiB]',' memory.total [MiB]',' utilization.gpu [%]',' clocks.current.graphics [MHz]',' clocks.current.memory [MHz]'])
    deadline=now()+duration;count=0
    while now()<deadline and not stopped():
        writer.writerow([datetime.datetime.now().astimezone().isoformat(),*reader.sample()])
        stream.flush();count+=1;sleep(1)
    return count


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path)
    parser.add_argument('--stop-file',type=Path);parser.add_argument('--duration',type=int,default=300)
    args=parser.parse_args();reader=None;valid_output=False
    try:
        if args.output is not None:
            root=Path(__file__).resolve().parents[3]/'run'
            if not args.output.resolve().is_relative_to(root.resolve()) or args.stop_file is None or args.stop_file.resolve().parent!=args.output.resolve().parent:
                raise ValueError('An isolated repository output and stop file are required')
            if not 1<=args.duration<=7600:raise ValueError('Invalid bounded duration')
            valid_output=True
        reader=Reader()
        if args.output is None:print(json.dumps(reader.sample()))
        else:
            with args.output.open('x',newline='',encoding='utf-8') as stream:
                collect(reader,stream,args.stop_file.exists,args.duration)
    except Exception as error:
        if valid_output:
            try:
                with args.output.with_suffix('.error.json').open('x') as report:
                    json.dump({'error':str(error)},report)
            except FileExistsError:pass
        raise
    finally:
        if reader is not None:reader.close()
