"""Bounded JFR CPU attribution. Stack hit shares are sample shares, not elapsed milliseconds."""
import argparse
from collections import Counter, defaultdict
import json
import re
import subprocess
import datetime


EVENTS = ','.join(('jdk.ExecutionSample', 'jdk.NativeMethodSample', 'jdk.ObjectAllocationSample',
                   'jdk.JavaMonitorEnter', 'jdk.ThreadPark', 'jdk.GCPhasePause', 'jdk.ThreadCPULoad',
                   'jdk.GarbageCollection', 'radiance.RenderFrame', 'jdk.ClassLoad', 'jdk.ClassDefine',
                   'jdk.Compilation', 'jdk.SafepointBegin', 'jdk.SafepointStateSynchronization', 'jdk.ExecuteVMOperation'))


def duration_seconds(value):
    if isinstance(value, (int, float)):
        return value
    match = re.fullmatch(r'PT(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?', value)
    if not match:
        raise ValueError('Unknown JFR duration representation: ' + str(value))
    hours, minutes, seconds = (float(part or 0) for part in match.groups())
    return hours * 3600 + minutes * 60 + seconds


def method_name(frame):
    method = frame.get('method') or {}
    return str((method.get('type') or {}).get('name', '?')).replace('/', '.') + '.' + method.get('name', '?')


def attribute_frames(events, threshold_ms=40):
    """Join JFR's own clock and frame identities; overlapping event durations stay separate."""
    def start(value):
        return datetime.datetime.fromisoformat(value['startTime'].replace('Z', '+00:00')).timestamp()

    def identity(thread):
        return thread.get('javaThreadId'), thread.get('osThreadId')

    result = []
    for event in events:
        value = event['values']
        if event['type'] != 'radiance.RenderFrame':
            continue
        measured = value.get('measuredWallNs', 0)
        wall_ms = duration_seconds(measured) * 1000 if isinstance(measured, str) else measured / 1e6
        if wall_ms < threshold_ms:
            continue
        begin = start(value)
        end = begin + duration_seconds(value.get('duration', 0))
        owner = identity(value.get('eventThread') or {})
        samples, related, compilations = Counter(), [], []
        for item in events:
            kind, data = item['type'], item['values']
            if kind == 'radiance.RenderFrame' or 'startTime' not in data:
                continue
            at = start(data)
            if kind in ('jdk.ExecutionSample', 'jdk.NativeMethodSample'):
                if begin <= at < end and identity(data.get('sampledThread') or {}) == owner:
                    frames = (data.get('stackTrace') or {}).get('frames', [])
                    stack = tuple(method_name(f) for f in frames[:12])
                    samples[stack] += 1
                continue
            duration = duration_seconds(data.get('duration', 0))
            overlap = max(0, min(end, at + duration) - max(begin, at))
            if not overlap:
                continue
            same_thread = identity(data.get('eventThread') or {}) == owner
            if not same_thread and kind not in ('jdk.GCPhasePause', 'jdk.Compilation', 'jdk.SafepointBegin',
                                                'jdk.SafepointStateSynchronization', 'jdk.ExecuteVMOperation'):
                continue
            cls = data.get('loadedClass') or data.get('definedClass') or {}
            method = data.get('method') or {}
            target = compilations if kind == 'jdk.Compilation' else related
            target.append(dict(event=kind, durationMs=duration * 1000, overlapMs=overlap * 1000,
                               renderThread=same_thread, className=cls.get('name'),
                               method=method.get('name'), operation=data.get('operation')))
        result.append(dict(frame=value['frameToken'], startTime=value['startTime'], wallMs=wall_ms,
                           renderSamples=sum(samples.values()),
                           topSampleStacks=[dict(samples=count, stack=list(stack)) for stack, count in samples.most_common(8)],
                           overlappingEvents=sorted(related, key=lambda e: -e['overlapMs'])[:24],
                           overlappingCompilations=sorted(compilations, key=lambda e: -e['overlapMs'])[:8]))
    return sorted(result, key=lambda row: -row['wallMs'])


def summarize(events, render_thread='Render thread'):
    counts = Counter()
    leaves = defaultdict(Counter)
    inclusive = defaultdict(Counter)
    allocation = Counter()
    allocation_baselines = []
    allocation_threads = set()
    waits = defaultdict(lambda: [0, 0.0, 0.0])
    cpu = defaultdict(list)
    cpu_baselines = []
    cpu_threads = set()
    pauses = []
    render_groups, entity_leaves = Counter(), Counter()
    truncated, depth = Counter(), Counter()
    for event in events:
        kind, value = event['type'], event['values']
        counts[kind] += 1
        thread = value.get('sampledThread') or value.get('eventThread') or {}
        name = thread.get('javaName') or thread.get('osName') or '<no-thread>'
        frames = (value.get('stackTrace') or {}).get('frames', [])
        stack = [method_name(frame) for frame in frames]
        if kind in ('jdk.ExecutionSample', 'jdk.NativeMethodSample'):
            truncated[name] += int((value.get('stackTrace') or {}).get('truncated', False))
            depth[name] = max(depth[name], len(stack))
            leaves[name][stack[0] if stack else '<no-stack>'] += 1
            # A method appears at most once per sampled stack; recursive stacks
            # do not create additional samples. Inclusive method rows overlap.
            inclusive[name].update(set(stack))
            if name == render_thread:
                groups = (
                    ('native-submit', 'com.radiance.client.proxy.vulkan.RendererProxy.submitCommandNative'),
                    ('native-entity-build', 'com.radiance.client.proxy.world.EntityProxy.build'),
                    ('native-acquire', 'com.radiance.client.proxy.vulkan.RendererProxy.acquireContextNative'),
                    ('entity-render', 'com.radiance.client.proxy.world.EntityProxy.queueEntitiesBuild'),
                    ('block-entity-render', 'com.radiance.client.proxy.world.EntityProxy.queueBlockEntitiesRebuild'),
                    ('client-tick', 'net.minecraft.client.Minecraft.tick'),
                )
                group = next((label for label, prefix in groups if any(method.startswith(prefix) for method in stack)), 'other')
                render_groups[group] += 1
                if group == 'entity-render':
                    entity_leaves[stack[0] if stack else '<no-stack>'] += 1
        elif kind == 'jdk.ObjectAllocationSample':
            identity = (thread.get('javaThreadId'), thread.get('osThreadId'))
            if identity in allocation_threads:
                pass
            else:
                # HotSpot 21 computes this weight from the thread's previous
                # allocation sample, possibly before Recording.start(). Keep
                # the boundary event, but do not attribute its lifetime delta
                # to the first class encountered inside this window.
                allocation_threads.add(identity)
                allocation_baselines.append(dict(thread=name, identity=identity, startTime=value.get('startTime'), weightBytes=value.get('weight', 0)))
                continue
            cls = (value.get('objectClass') or {}).get('name', '?')
            allocation[(cls, stack[0] if stack else '<no-stack>')] += value.get('weight', 0)
        elif kind in ('jdk.JavaMonitorEnter', 'jdk.ThreadPark'):
            seconds = duration_seconds(value.get('duration', 0))
            row = waits[(name, kind, stack[0] if stack else '<no-stack>')]
            row[0] += 1
            row[1] += seconds
            row[2] = max(row[2], seconds)
        elif kind == 'jdk.ThreadCPULoad':
            identity = (thread.get('javaThreadId'), thread.get('osThreadId'))
            if identity not in cpu_threads:
                cpu_threads.add(identity)
                cpu_baselines.append(dict(thread=name, identity=identity, startTime=value.get('startTime'), reportedLoad=value.get('user', 0) + value.get('system', 0)))
                continue
            cpu[name].append(value.get('user', 0) + value.get('system', 0))
        elif kind == 'jdk.GCPhasePause':
            pauses.append(duration_seconds(value.get('duration', 0)) * 1000)
    return dict(eventCounts=dict(counts),
                longFrameAttribution=attribute_frames(events),
                renderSampleGroups=dict(render_groups), entityRenderLeaves=entity_leaves.most_common(30),
                sampledThreads={name: dict(samples=sum(rows.values()), topLeaves=rows.most_common(25),
                                          truncatedStacks=truncated[name], maximumExportedDepth=depth[name],
                                          inclusiveMethods= inclusive[name].most_common(40)) for name, rows in leaves.items()},
                allocationBoundarySamples=allocation_baselines,
                allocationSamplesByClassAndLeaf=[dict(objectClass=k[0], leaf=k[1], sampleWeightBytes=v) for k, v in allocation.most_common(30)],
                waitEvents=[dict(thread=k[0], event=k[1], leaf=k[2], events=v[0], totalSeconds=v[1], maxSeconds=v[2]) for k, v in sorted(waits.items(), key=lambda item: -item[1][1])[:40]],
                threadCpuLoad={name: dict(samples=len(data), meanReportedLoad=sum(data) / len(data), maxReportedLoad=max(data)) for name, data in cpu.items()},
                threadCpuLoadBoundarySamples=cpu_baselines,
                gcPauseMs=dict(samples=len(pauses), total=sum(pauses), maximum=max(pauses, default=0)),
                limitations=['JFR is an explicitly intrusive attribution window, not formal frame timing.',
                             'Execution/native sample shares are not wall-time percentages; inclusive methods overlap.',
                             'Native method samples identify Java JNI boundaries, not full C++/driver stacks.',
                             'AllocationSample weights estimate Java heap allocation; exclude native buffers and PCIe traffic.',
                             'First allocation sample per thread is boundary-ambiguous and retained separately, excluded from class ranking; no exact full-window allocation rate is claimed.',
                             'HotSpot 21 ThreadCPULoad is divided by active logical processor count; reported load is not single-core utilization.',
                             'First CPU-load delta per thread is retained separately because its preceding counter baseline can predate this recording.',
                             'Wait events include only configured threshold events and do not cover every native wait.'])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('recording')
    parser.add_argument('output')
    parser.add_argument('--jfr-tool', default='jfr')
    parser.add_argument('--skip-allocation', action='store_true', help='CPU/GC/wait diagnosis without unused allocation exports')
    parser.add_argument('--render-exporter', help='Compiled RenderJfrExport class directory; filter before expanding metadata')
    parser.add_argument('--java-tool', default='java')
    parser.add_argument('--thread-pattern', default='', help='Additional worker thread names in compact export')
    args = parser.parse_args()
    # Deep allocation stacks make this CLI export enormous. Only execution attribution
    # needs caller chains; allocation/wait ranking uses its documented leaf method.
    samples = ('jdk.ExecutionSample', 'jdk.NativeMethodSample')
    events = []
    others = tuple(e for e in EVENTS.split(',') if e not in samples
                   and not (args.skip_allocation and e == 'jdk.ObjectAllocationSample'))
    if args.render_exporter:
        output = subprocess.check_output([args.java_tool, '-cp', args.render_exporter,
                                          'com.radiance.audit.tools.RenderJfrExport', args.recording,
                                          args.thread_pattern or '(?!)'], timeout=60)
        events = json.loads(output)['recording']['events']
    else:
        for names, depth in ((samples, '128'), (others, '5')):
            output = subprocess.check_output([args.jfr_tool, 'print', '--json', '--stack-depth', depth,
                                              '--events', ','.join(names), args.recording], timeout=60)
            events.extend(json.loads(output)['recording']['events'])
    result = summarize(events)
    result['excludedEvents'] = ['jdk.ObjectAllocationSample'] if args.skip_allocation else []
    result['exportScope'] = ('Render-thread, selected worker stacks and related global events; worker pattern=' + args.thread_pattern) if args.render_exporter else 'Configured event types from all threads'
    with open(args.output, 'w', encoding='utf-8') as file:
        json.dump(result, file, indent=2)
        file.write('\n')
