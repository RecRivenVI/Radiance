import tempfile
import unittest
from pathlib import Path
from profile_quality import analyze, distribution


class ProfileQualityTest(unittest.TestCase):
    def test_long_intervals_are_not_trimmed(self):
        self.assertEqual(distribution([20, 21, 900])['max'], 900)
        self.assertEqual(distribution([20, 21, 900])['mean'], 941 / 3)

    def test_invalid_status_is_rejected_before_any_aggregation(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            (root / 'STATUS.txt').write_text('finished=true\nvalid-cpu-sample=false\n')
            with self.assertRaisesRegex(ValueError, 'invalid CPU capture'):
                analyze(root)

    def test_multiple_calls_are_per_frame_and_missing_gpu_is_not_zero(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            (root / 'STATUS.txt').write_text('finished=true\nvalid-cpu-sample=true\nrecorded-frames=2\n')
            (root / 'frames.csv').write_text('frame,wall_ns,start_interval_ns,failed,world,width,height,focused\n1,20000000,-1,false,true,1920,1080,false\n2,30000000,900000000,false,true,1920,1080,false\n')
            (root / 'timings.csv').write_text('frame,domain,stage,inclusive_ns,self_ns\n1,0,pack,1000000,1000000\n1,0,pack,2000000,2000000\n1,4,gpu,6000000,0\n')
            (root / 'counters.csv').write_text('frame,stage,count,bytes\n1,upload,2,400\n')
            result = analyze(root)
            self.assertEqual(result['intervalMs']['max'], 900)
            self.assertEqual(result['stagesMs']['0/pack']['mean'], 1.5)
            self.assertEqual(result['stagesMs']['4/gpu']['mean'], 6)
            self.assertEqual(result['stagesMs']['4/gpu']['samples'], 1)
            self.assertEqual(result['workPerFrame']['upload']['logicalBytes'], 200)
            path = root / 'frames.csv'
            path.write_text(path.read_text().replace('false,true', 'false,false'))
            with self.assertRaisesRegex(ValueError, 'missing world'):
                analyze(root)
            self.assertEqual(2, analyze(root, allow_absent_world=True)['frameCount'])
            path.write_text(path.read_text().replace('false,false', 'true,false'))
            with self.assertRaisesRegex(ValueError, 'Failed frame'):
                analyze(root, allow_absent_world=True)


if __name__ == '__main__':
    unittest.main()
