"""Validate a complete frame capture before interpreting its CPU/GPU distributions.

No tail trimming, cross-clock subtraction or summation of overlapping domains.
"""
import argparse
import csv
import json
import math
from collections import defaultdict
from pathlib import Path


def distribution(values):
    ordered = sorted(values)
    if not ordered:
        return {"samples": 0}
    return dict(samples=len(ordered), mean=sum(ordered) / len(ordered),
                p50=ordered[math.ceil(len(ordered) * .5) - 1],
                p95=ordered[math.ceil(len(ordered) * .95) - 1],
                p99=ordered[math.ceil(len(ordered) * .99) - 1], max=ordered[-1])


def analyze(directory):
    directory = Path(directory)
    status = dict(line.split('=', 1) for line in (directory / 'STATUS.txt').read_text().splitlines() if '=' in line)
    if status.get('finished') != 'true' or status.get('valid-cpu-sample') != 'true':
        raise ValueError('Incomplete or invalid CPU capture: ' + str(directory))
    with (directory / 'frames.csv').open(newline='') as source:
        frames = list(csv.DictReader(source))
    tokens = [int(row['frame']) for row in frames]
    if not tokens or tokens != list(range(tokens[0], tokens[0] + len(tokens))):
        raise ValueError('Missing/duplicate/out-of-order frame token')
    if int(status['recorded-frames']) != len(tokens):
        raise ValueError('Frame count differs from completed writer status')
    if any(row['failed'] != 'false' or row['world'] != 'true' for row in frames):
        raise ValueError('Failed frame or missing world')
    per_frame = defaultdict(lambda: defaultdict(int))
    inclusive = defaultdict(lambda: defaultdict(int))
    work = defaultdict(lambda: [0, 0])
    invocations = defaultdict(int)
    with (directory / 'timings.csv').open(newline='') as source:
        for row in csv.DictReader(source):
            token, domain, label = int(row['frame']), row['domain'], row['stage']
            if domain in ('8', '9'):
                continue
            key = domain + '/' + label
            invocations[key] += 1
            if token in range(tokens[0], tokens[-1] + 1):
                per_frame[key][token] += int(row['self_ns'] if domain in ('java', '0', 'observer') else row['inclusive_ns'])
                inclusive[key][token] += int(row['inclusive_ns'])
    with (directory / 'counters.csv').open(newline='') as source:
        for row in csv.DictReader(source):
            if int(row['frame']) in range(tokens[0], tokens[-1] + 1):
                work[row['stage']][0] += int(row['count'])
                work[row['stage']][1] += int(row['bytes'])
    stages = {}
    for key, values in per_frame.items():
        # Only CPU partitions use all rendered frames as the denominator.
        data = [values.get(token, 0) / 1e6 for token in tokens] if key.startswith(('java/', '0/', 'observer/')) else [value / 1e6 for value in values.values()]
        stages[key] = dict(distribution(data), invocations=invocations[key], returnedFrames=len(values))
    intervals = [int(row['start_interval_ns']) / 1e6 for row in frames if int(row['start_interval_ns']) >= 0]
    return dict(directory=str(directory), status=status, frameCount=len(tokens),
                intervalMs=distribution(intervals), wallMs=distribution([int(row['wall_ns']) / 1e6 for row in frames]),
                meanIntervalFps=1000 / (sum(intervals) / len(intervals)) if intervals else None,
                dimensions=sorted(set((int(r['width']), int(r['height'])) for r in frames)),
                focus=sorted(set(r['focused'] for r in frames)),
                stagesMs=stages,
                cpuInclusiveMs={key: distribution([values.get(token, 0) / 1e6 for token in tokens]) for key, values in inclusive.items() if key.startswith(('java/', '0/', 'observer/'))},
                workPerFrame={key: dict(count=v[0] / len(tokens), logicalBytes=v[1] / len(tokens)) for key, v in work.items()},
                limitations=['CPU stages measure elapsed wall duration including waits.',
                             'Java and native drill-down overlap; GPU domains overlap each other and CPU.',
                             'No frames were trimmed. GPU missing frames are absent, not zero.',
                             'Logical payload bytes are not measured PCIe traffic.'])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    args.output.write_text(json.dumps(analyze(args.directory), indent=2) + '\n', encoding='utf-8')
