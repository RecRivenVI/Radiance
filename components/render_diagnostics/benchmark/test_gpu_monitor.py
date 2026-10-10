import csv
import io
import unittest
import gpu_monitor


class MonitorTest(unittest.TestCase):
    def test_bounded_samples_are_flushed_and_stop_does_not_poll_again(self):
        class Reader:
            calls=0
            def sample(self):self.calls+=1;return [12000,16376,75,2700,11500]
        class Stream(io.StringIO):
            flushes=0
            def flush(self):self.flushes+=1
        reader=Reader();stream=Stream();clock=[0]
        gpu_monitor.collect(reader,stream,lambda:reader.calls==3,100,
            now=lambda:clock[0],sleep=lambda t:clock.__setitem__(0,clock[0]+t))
        rows=list(csv.DictReader(io.StringIO(stream.getvalue())))
        self.assertEqual(3,reader.calls);self.assertEqual(3,stream.flushes)
        self.assertEqual('12000',rows[-1][' memory.used [MiB]'])
        self.assertEqual(3,len(rows))

    def test_errors_are_not_reported_as_zero_gpu_usage(self):
        class Broken:
            def sample(self):raise RuntimeError('lost')
        stream=io.StringIO()
        with self.assertRaisesRegex(RuntimeError,'lost'):
            gpu_monitor.collect(Broken(),stream,lambda:False,1)
        self.assertEqual([],list(csv.DictReader(io.StringIO(stream.getvalue()))))

if __name__=='__main__':unittest.main()
