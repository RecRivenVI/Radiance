package com.radiance.client.texture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class IdentifierInputStreamTest {
    @Test
    void delegatesBulkReadsAndClose() throws IOException {
        CloseTrackingStream source = new CloseTrackingStream(new byte[] {1, 2, 3, 4});
        IdentifierInputStream stream = new IdentifierInputStream(source,
                ResourceLocation.fromNamespaceAndPath("radiance", "textures/test.png"));

        assertArrayEquals(new byte[] {1, 2, 3, 4}, stream.readAllBytes());
        assertEquals(ResourceLocation.fromNamespaceAndPath("radiance", "textures/test.png"),
            stream.getResourceId());
        stream.close();
        assertTrue(source.closed);
        assertEquals(1, source.closeCount);
    }

    @Test
    void exceptionalReadStillClosesTheOwnedResourceStreamOnce() {
        FailingStream source = new FailingStream();
        IdentifierInputStream stream = new IdentifierInputStream(source,
            ResourceLocation.fromNamespaceAndPath("radiance", "textures/failing.png"));

        assertThrows(IOException.class, () -> {
            try (stream) {
                stream.read();
            }
        });
        assertEquals(1, source.closeCount);
    }

    private static final class CloseTrackingStream extends ByteArrayInputStream {
        private boolean closed;
        private int closeCount;

        private CloseTrackingStream(byte[] data) {
            super(data);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            closeCount++;
            super.close();
        }
    }

    private static final class FailingStream extends InputStream {
        private int closeCount;

        @Override
        public int read() throws IOException {
            throw new IOException("injected read failure");
        }

        @Override
        public void close() {
            closeCount++;
        }
    }
}
