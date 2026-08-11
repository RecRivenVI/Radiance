import csv
import datetime
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from renderer_campaign import analyze_lifecycle


class LifecycleAlignmentTest(unittest.TestCase):
    def test_phase_alignment_uses_epoch_and_excludes_crossing_long_frame(self):
        with tempfile.TemporaryDirectory() as tmp:
            case = Path(tmp)
            phases = ['title-before','loading','steady','flying','post-route-steady','entities-still',
                      'entities-moving','after-remove','title-after','reenter-loading','reenter-steady','flushing']
            epoch = 1_700_000_000
            rows = [dict(phase=p,time=datetime.datetime.fromtimestamp(epoch+i*100,datetime.timezone.utc).isoformat(),heapUsed=100)
                    for i,p in enumerate(phases)]
            (case/'lifecycle.jsonl').write_text('\n'.join(json.dumps(r) for r in rows))
            (case/'noise.jsonl').write_text('\n'.join(json.dumps(dict(time=r['time'],gpu=[100,1000,50],processMemory={'privateBytes':100})) for r in rows))
            directory = case/'profiles/one'; directory.mkdir(parents=True)
            with (directory/'frames.csv').open('w',newline='') as out:
                fields = ['width','height','render_distance','sdk_generated_fps','start_epoch_ms','wall_ns','start_interval_ns','render_thread_cpu_ns']
                writer = csv.DictWriter(out,fieldnames=fields); writer.writeheader()
                for offset,wall in ((1,10_000_000),(300.1,50_000_000),(399.9,500_000_000)):
                    writer.writerow(dict(zip(fields,[1920,1080,32,0,int((epoch+offset)*1000),wall,wall,1_000_000])))
            with patch('renderer_campaign.profile_quality.analyze',return_value={'status':{'finished':'true'}}):
                analyze_lifecycle(case,1920,1080,32)
            report=json.loads((case/'lifecycle-analysis.json').read_text())
            flying=next(r for r in report['phases'] if r['phase']=='flying')
            self.assertEqual(1,flying['intervalMs']['samples'])
            self.assertEqual(50,flying['intervalMs']['max'])
            self.assertTrue(all(p['seconds']>=0 for p in report['phases']))
            self.assertGreater(report['frameCoverage']['uncoveredTailSeconds'],600)
            (case/'gpu-throughput-window.json').write_text(json.dumps(dict(start=epoch+300,end=epoch+301)))
            with patch('renderer_campaign.profile_quality.analyze',return_value={'status':{'finished':'true'}}):
                analyze_lifecycle(case,1920,1080,32)
            updated=json.loads((case/'lifecycle-analysis.json').read_text())
            flying=next(r for r in updated['phases'] if r['phase']=='flying')
            self.assertEqual(1,flying['externalProfilerFramesExcluded'])
            self.assertEqual(0,flying['intervalMs'].get('samples',0))


if __name__ == '__main__':
    unittest.main()
