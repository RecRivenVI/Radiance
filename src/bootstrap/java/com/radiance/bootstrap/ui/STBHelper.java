/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.radiance.bootstrap.ui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.Objects;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryUtil;

final class STBHelper {
    private static final String RESOURCE_ROOT = "com/radiance/bootstrap/ui/";

    private STBHelper() {}

    static ByteBuffer readFromClasspath(final String name, int initialCapacity) {
        ByteBuffer buf;
        try (var channel = Channels.newChannel(Objects.requireNonNull(
                STBHelper.class.getClassLoader().getResourceAsStream(RESOURCE_ROOT + name),
                "The resource " + name + " cannot be found"))) {
            buf = BufferUtils.createByteBuffer(initialCapacity);
            while (true) {
                var readbytes = channel.read(buf);
                if (readbytes == -1) break;
                if (buf.remaining() == 0) {
                    var newBuf = BufferUtils.createByteBuffer(buf.capacity() * 3 / 2);
                    buf.flip();
                    newBuf.put(buf);
                    buf = newBuf;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        buf.flip();
        return MemoryUtil.memSlice(buf);
    }

    static int[] loadTextureFromClasspath(String file, int size, int textureKey, DrawSink sink) {
        int[] width = new int[1];
        int[] height = new int[1];
        int[] channels = new int[1];
        ByteBuffer image = STBImage.stbi_load_from_memory(readFromClasspath(file, size), width, height, channels, 4);
        if (image == null) throw new IllegalStateException("Unable to decode " + file + ": " + STBImage.stbi_failure_reason());
        try {
            // FML 4.0.43 leaves image textures at OpenGL's default GL_REPEAT wrap mode.
            sink.texture(textureKey, width[0], height[0], image, true, false);
        } finally {
            STBImage.stbi_image_free(image);
        }
        return new int[] { width[0], height[0] };
    }
}
