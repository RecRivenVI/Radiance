package com.radiance.audit;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileReportTest {
    @TempDir Path output;

    @Test
    void retainsLongFramesAndChecksPartitionContinuityAndCpuQuantum() throws Exception {
        var writer = new ProfileReport(output, "test fixture");
        writer.offer(
                "10,400000000,31250000,-1,true,false,none,1920,1080,32,0,0,false\n",
                "10,java,FRAME_OTHER,400000000,400000000\n");
        writer.offer(
                "11,20000000,15625000,450000000,true,false,none,1920,1080,32,0,0,false\n",
                "11,java,FRAME_OTHER,20000000,5000000\n11,java,ENTITIES,15000000,15000000\n");
        writer.finish();
        writer.run();
        var status = Files.readString(output.resolve("STATUS.txt"));
        assertTrue(status.contains("valid-cpu-sample=true"));
        assertTrue(status.contains("thread-cpu-positive-quantum-gcd-ns=15625000"));
        assertTrue(Files.readString(output.resolve("frames.csv")).contains("450000000"));
    }

    @Test
    void incompletePartitionAndDroppedNativeRecordsInvalidateSample() throws Exception {
        var writer = new ProfileReport(output, "test fixture");
        writer.offer(
                "10,100,0,-1,true,false,none,1920,1080,32,0,0,false\n",
                "10,java,FRAME_OTHER,100,60\n0,9,dropped,1,0\n");
        writer.offer(
                "12,100,0,100,true,false,none,1920,1080,32,0,0,false\n",
                "12,java,FRAME_OTHER,100,100\n");
        writer.finish();
        writer.run();
        var status = Files.readString(output.resolve("STATUS.txt"));
        assertTrue(status.contains("valid-cpu-sample=false"));
        assertTrue(status.contains("frame-token-gaps=1"));
        assertTrue(status.contains("java-partition-errors=1"));
        assertTrue(status.contains("dropped-native-events=1"));
    }

    @Test
    void malformedNativeRecordFailsInsteadOfSilentlyOmittingIt() throws Exception {
        var writer = new ProfileReport(output, "test fixture");
        writer.offer("", "broken,event\n");
        writer.finish();
        writer.run();
        assertTrue(Files.readString(output.resolve("STATUS.txt")).contains("finished=false"));
    }

    @Test
    void missingEntireLastBatchIsNotACompleteCapture() throws Exception {
        var writer = new ProfileReport(output, "test fixture");
        writer.offer(
                "1,100,0,-1,true,false,none,1920,1080,32,0,0,false\n",
                "1,java,FRAME_OTHER,100,100\n");
        writer.finish(2);
        writer.run();
        assertTrue(
                Files.readString(output.resolve("STATUS.txt")).contains("valid-cpu-sample=false"));
    }

    @Test
    void workCountersAreNotReportedAsDurations() throws Exception {
        var writer = new ProfileReport(output, "test fixture", 100);
        writer.offer(
                "", "99,5,old,3,108\n100,5,entity.format.entity,400,14400\n100,0,pack,100,80\n");
        writer.finish();
        writer.run();
        assertEquals(
                "frame,stage,count,bytes\n100,entity.format.entity,400,14400\n",
                Files.readString(output.resolve("counters.csv")).replace("\r\n", "\n"));
        assertFalse(
                Files.readString(output.resolve("timings.csv")).contains("entity.format.entity"));
        assertFalse(
                Files.readString(output.resolve("summary.csv")).contains("entity.format.entity"));
    }

    @Test
    void multipleNativeCallsUseRecordedFrameDenominatorWithoutCountingWorkers() throws Exception {
        var writer = new ProfileReport(output, "test fixture");
        writer.offer(
                "",
                "1,java,FRAME_OTHER,10000000,10000000\n2,java,FRAME_OTHER,10000000,10000000\n"
                        + "1,0,pack,1000000,1000000\n1,0,pack,1000000,1000000\n"
                        + "1,0,pack,1000000,1000000\n0,1,worker,9000000,9000000\n");
        writer.finish();
        writer.run();
        var report = Files.readString(output.resolve("REPORT.md"));
        assertTrue(report.contains("| pack | 3.000 | 1.000 | 1.500 | n/a |"));
        assertTrue(report.contains("| worker | 9.000 | 9.000 | n/a | n/a |"));
    }

    @Test
    void writesRealPartitionAndKeepsWorkerAndGpuSeparate() throws Exception {
        var writer = new ProfileReport(output, "test fixture");
        writer.offer(
                "",
                "1,java,FRAME_OTHER,100,30\n1,java,ENTITIES,70,70\n0,1,chunk,500,500\n1,4,gpu,800,0\n");
        writer.finish();
        writer.run();
        String summary = Files.readString(output.resolve("summary.csv"));
        assertTrue(
                summary.contains(
                        "java,FRAME_OTHER,self,1,0.000030,0.000030,0.000030,0.000030,0.000030,30.000"));
        assertTrue(
                summary.contains(
                        "java,ENTITIES,self,1,0.000070,0.000070,0.000070,0.000070,0.000070,70.000"));
        assertTrue(
                summary.contains("1,chunk,self,1,0.000500,0.000500,0.000500,0.000500,0.000500,"));
    }

    @Test
    void fullWriterQueueDropsInsteadOfBlockingProducer() throws Exception {
        var writer = new ProfileReport(output, "test fixture");
        for (int i = 0; i < 300; i++) writer.offer("", "");
        writer.finish();
        writer.run();
        assertTrue(
                Files.readString(output.resolve("STATUS.txt"))
                        .contains("dropped-writer-batches=44"));
    }

    @Test
    void lateGpuCompletionFromPreviousCaptureIsOmitted() throws Exception {
        var writer = new ProfileReport(output, "test fixture", 100);
        writer.offer("", "99,4,old-gpu,800,0\n100,4,new-gpu,900,0\n");
        writer.finish();
        writer.run();
        var csv = Files.readString(output.resolve("timings.csv"));
        assertFalse(csv.contains("old-gpu"));
        assertTrue(csv.contains("new-gpu"));
    }
}
