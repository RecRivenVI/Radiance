"""Bounded JFR CPU attribution. Stack hit shares are sample shares, not elapsed milliseconds."""
import argparse
from collections import Counter, defaultdict
import json
import re
import subprocess


EVENTS = ','.join(('jdk.ExecutionSample', 'jdk.NativeMethodSample', 'jdk.ObjectAllocationSample',
                   'jdk.JavaMonitorEnter', 'jdk.ThreadPark', 'jdk.GCPhasePause', 'jdk.ThreadCPULoad',
                   'jdk.GarbageCollection'))


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


def summarize(events):
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
            if name == 'Render thread':
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
    args = parser.parse_args()
    # Deep allocation stacks make this CLI export enormous. Only execution attribution
    # needs caller chains; allocation/wait ranking uses its documented leaf method.
    samples = ('jdk.ExecutionSample', 'jdk.NativeMethodSample')
    events = []
    others = tuple(e for e in EVENTS.split(',') if e not in samples
                   and not (args.skip_allocation and e == 'jdk.ObjectAllocationSample'))
    for names, depth in ((samples, '128'), (others, '5')):
        output = subprocess.check_output([args.jfr_tool, 'print', '--json', '--stack-depth', depth,
                                          '--events', ','.join(names), args.recording], timeout=60)
        events.extend(json.loads(output)['recording']['events'])
    result = summarize(events)
    result['excludedEvents'] = ['jdk.ObjectAllocationSample'] if args.skip_allocation else []
    with open(args.output, 'w', encoding='utf-8') as file:
        json.dump(result, file, indent=2)
        file.write('\n')
