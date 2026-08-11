"""Real normal-packet actions correlated to completed scene revisions, not JNI return."""
import json
import re
import zipfile

from profile_quality import distribution
from takeover_experiment import write


def parse_trace(text):
    before_window, text = text.split('TRACE_WINDOW\n', 1)
    baseline = re.findall(r'\bdropped=(\d+)', before_window)
    if len(baseline) != 1:
        raise RuntimeError('Missing explicit pre-window trace drop baseline')
    drops = re.findall(r'\bdropped=(\d+)', text)
    if not drops or any(int(value) != int(baseline[0]) for value in drops):
        raise RuntimeError('Interaction window lost native trace events')
    events, brackets = [], []
    bracket = None
    for line in text.splitlines():
        if line.startswith('JAVA_BRACKET '):
            bracket = tuple(map(int, line.split()[1:]))
        elif line.startswith('clock_ns='):
            clock = int(line.split(';')[0].split('=')[1])
            if bracket is None:
                raise RuntimeError('Unbracketed native clock')
            brackets.append((bracket[0] - clock, bracket[1] - clock))
            bracket = None
        elif re.match(r'^\d+,', line):
            clock, stage, slot, revision, frame, owner = line.split(',')
            events.append((int(clock), stage, int(slot), int(revision), int(frame), int(owner)))
    # The clock sample occurs inside this JNI interval. Use the tightest observed
    # bracket, keeping its uncertainty, never subtract two unrelated clock epochs.
    low, high = min(brackets, key=lambda pair: pair[1] - pair[0])
    return events, (low, high), int(baseline[0])


def analyze_window(actions, text, log):
    events, offset, dropped_before = parse_trace(text)
    completions = {frame: clock for clock, stage, _, _, frame, _ in events if stage == 'frame-complete'}
    dirties = []
    for match in re.finditer(r'CHUNK_UPDATE event=DIRTY value=(\d+) action=(\d+); id=(\d+); java_ns=(\d+); origin=(?:Mutable)?BlockPos\{x=(-?\d+), y=(-?\d+), z=(-?\d+)\}', log):
        revision, action, slot, clock, x, y, z = map(int, match.groups())
        dirties.append(dict(revision=revision, action=action, slot=slot, clock=clock, origin=[x,y,z]))
    action_marks = [(int(m[1]), int(m[2])) for m in re.finditer(
        r'CHUNK_UPDATE event=ACTION value=(\d+) java_ns=(\d+)', log)]
    empty = [(tuple(map(int,m.groups()[:7])),m[8]) for m in re.finditer(
        r'CHUNK_UPDATE event=EMPTY_CONFIRMED value=(\d+) action=(\d+); id=(\d+); java_ns=(\d+); '
        r'origin=(?:Mutable)?BlockPos\{x=(-?\d+), y=(-?\d+), z=(-?\d+)\}; removed=(true|false)',log)]
    starts = [m for m in re.finditer(r'CHUNK_BENCH city-begin scenario=\w+ target=BlockPos\{x=(-?\d+), y=(-?\d+), z=(-?\d+)\} java_ns=(\d+)',log)
              if int(m[4]) <= actions[0]['java_ns']]
    target = [int(v)//16*16 for v in max(starts,key=lambda m:int(m[4])).groups()[:3]] if starts else None
    rows = []
    window_end = max(int(value) for value in re.findall(r'JAVA_BRACKET \d+ (\d+)',text)) + 1
    state_events = [(m[1], int(m[2]), int(m[3])) for m in re.finditer(
        r'CHUNK_BENCH (client-state|server-state) operation=(\d+) java_ns=(\d+)',log)]
    for index, operation in enumerate(actions):
        start = operation['java_ns']
        end = actions[index+1]['java_ns'] if index+1 < len(actions) else window_end
        action_ids = {identity for identity, clock in action_marks if start <= clock < end}
        dirty_by_slot = {row['slot']:row for row in dirties if row['action'] in action_ids and start <= row['clock'] < end}
        dirty = list(dirty_by_slot.values())
        chains = []
        for row in dirty:
            uses = [(clock, stage, frame, owner) for clock, stage, slot, revision, frame, owner in events
                    if stage in ('tlas-record','tlas-absent') and slot == row['slot']
                    and revision == row['revision'] and clock + offset[1] >= start and frame in completions]
            if not uses:
                confirmed = [fields for fields,removed in empty if removed=='false'
                             and fields[0]==row['revision'] and fields[2]==row['slot']
                             and start <= fields[3] < end]
                if confirmed:
                    java_empty = max(fields[3] for fields in confirmed)
                    submitted = [(stamp,frame) for stamp,stage,_,_,frame,_ in events
                                 if stage=='frame-submit' and stamp+offset[0]>=java_empty and frame in completions]
                    if submitted:
                        _,frame=min(submitted)
                        chains.append(dict(**row,owner=0,frame=frame,present=False,unchangedEmpty=True,
                            completedLatencyMs=[(completions[frame]+off-start)/1e6 for off in offset],nativeStages={}))
                continue
            clock, stage, frame, owner = min(uses)
            finished = completions[frame]
            if finished < clock:
                raise RuntimeError('Scene completion preceded its recording')
            stages = {name:stamp for stamp,name,slot,revision,_,generation in events
                      if slot == row['slot'] and revision == row['revision'] and generation == owner
                      and stamp <= clock}
            if stage == 'tlas-record' and not (
                    stages.get('native-enqueue',2**63) <= stages.get('build-prepare',-1)
                    <= stages.get('build-submit',-1) <= stages.get('gpu-complete',-1)
                    <= stages.get('publish',-1) <= clock):
                raise RuntimeError('Nonempty revision lacked a completed build/publication chain')
            chains.append(dict(**row, owner=owner, frame=frame, present=stage=='tlas-record',
                completedLatencyMs=[(finished+off-start)/1e6 for off in offset],
                stagesMs={
                    'actionToNativeEnqueue':[(stages.get('native-enqueue',clock)+off-start)/1e6 for off in offset],
                    'nativeQueueToPrepare':(stages.get('build-prepare',clock)-stages.get('native-enqueue',clock))/1e6,
                    'prepareToSubmit':(stages.get('build-submit',clock)-stages.get('build-prepare',clock))/1e6,
                    'submitToGpuCompletionObserved':(stages.get('gpu-complete',clock)-stages.get('build-submit',clock))/1e6,
                    'gpuCompletionToPublish':(stages.get('publish',clock)-stages.get('gpu-complete',clock))/1e6,
                    'sceneRecordToFrameCompletionObserved':(finished-clock)/1e6,
                } if stage=='tlas-record' else {},
                nativeStages=stages))
        latency = [max(row['completedLatencyMs'][side] for row in chains) for side in (0,1)] if chains else None
        target_chains = [row for row in chains if row['origin']==target] if target else chains
        target_latency = [max(row['completedLatencyMs'][side] for row in target_chains) for side in (0,1)] if target_chains else None
        rows.append(dict(**operation, dirtySections=len(dirty), completedSections=len(chains),
            stateLatencyMs={kind:min((at-start)/1e6 for event,number,at in state_events
                                  if event==kind and number==operation['operation'] and start<=at<end)
                            for kind in ('client-state','server-state')
                            if any(event==kind and number==operation['operation'] and start<=at<end for event,number,at in state_events)},
            actionIds=sorted(action_ids), completedSceneLatencyMs=latency, chains=chains,
            targetOrigin=target, targetCompletedSceneLatencyMs=target_latency,
            incomplete=len(chains)!=len(dirty) or not dirty))
    observed = [sum(row['completedSceneLatencyMs'])/2 for row in rows if not row['incomplete']]
    return dict(actions=rows, observedCompletedLatencyMs=distribution(observed),
        allTargetOperationsObserved=all(r['targetCompletedSceneLatencyMs'] is not None for r in rows),
        targetCompletedLatencyMs=distribution([sum(r['targetCompletedSceneLatencyMs'])/2 for r in rows if r['targetCompletedSceneLatencyMs']]),
        missingTargetOperations=[r['operation'] for r in rows if not r['targetCompletedSceneLatencyMs']],
        incompleteOperations=[row['operation'] for row in rows if row['incomplete']],
        clockOffsetNs=offset, clockBracketWidthNs=offset[1]-offset[0], preWindowDropped=dropped_before,
        scope='Normal creative placement/dig packets. Endpoint is a GPU-completed scene frame, not display scanout. '
              'Latency covers every observed dirty dependency; missing dependencies remain censored, not discarded. '
              'Native trace instrumentation scans section slots; these windows are not formal FPS comparisons.')


def report(case):
    if (case/'logs/latest.log').is_file():
        log = (case/'logs/latest.log').read_text(encoding='utf8', errors='replace')
    else:
        with zipfile.ZipFile(case/'raw-evidence.zip') as raw:
            log = raw.read('logs/latest.log').decode('utf8', errors='replace')
    windows = {}
    for directory in sorted(case.glob('interaction-*')):
        actions = [json.loads(line) for line in (directory/'actions.jsonl').read_text().splitlines()]
        if len(actions) != 12:
            raise RuntimeError('Incomplete bounded interaction script')
        windows[directory.name] = analyze_window(actions, (directory/'trace.txt').read_text(), log)
    if len(windows) != 2:
        raise RuntimeError('Both idle and loading interaction windows are required')
    write(case/'chunk-interactions-analysis.json', windows)
    return windows
