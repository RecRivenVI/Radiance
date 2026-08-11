/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.radiance.bootstrap.ui;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class FrameBuilder {
    static final int VERTEX_BYTES = 20;
    static final int BATCH_BYTES = 16;

    private ByteBuffer vertices = direct(4096);
    private ByteBuffer batches = direct(256);
    private int batchFirstVertex;
    private int role;
    private int textureKey;
    private boolean building;

    void begin(int role, int textureKey) {
        if (building) throw new IllegalStateException("Already building a batch");
        this.role = role;
        this.textureKey = textureKey;
        this.batchFirstVertex = vertices.position() / VERTEX_BYTES;
        this.building = true;
    }

    void quad(float x0, float x1, float y0, float y1, float u0, float u1, float v0, float v1, int colour) {
        if (!building) throw new IllegalStateException("No active batch");
        vertex(x0, y0, u0, v0, colour);
        vertex(x1, y0, u1, v0, colour);
        vertex(x0, y1, u0, v1, colour);
        vertex(x1, y0, u1, v0, colour);
        vertex(x1, y1, u1, v1, colour);
        vertex(x0, y1, u0, v1, colour);
    }

    void end() {
        if (!building) throw new IllegalStateException("No active batch");
        int count = vertices.position() / VERTEX_BYTES - batchFirstVertex;
        if (count != 0) {
            ensureBatch(BATCH_BYTES);
            batches.putInt(batchFirstVertex).putInt(count).putInt(role).putInt(textureKey);
        }
        building = false;
    }

    LoadingScene.Frame finish(int width, int height, int backgroundAbgr) {
        if (building) throw new IllegalStateException("Unfinished batch");
        vertices.flip();
        batches.flip();
        return new LoadingScene.Frame(copy(vertices), copy(batches), batches.remaining() / BATCH_BYTES, width, height, backgroundAbgr);
    }

    private void vertex(float x, float y, float u, float v, int colour) {
        ensureVertex(VERTEX_BYTES);
        vertices.putFloat(x).putFloat(y).putFloat(u).putFloat(v).putInt(colour);
    }

    private void ensureVertex(int bytes) {
        if (vertices.remaining() < bytes) vertices = grow(vertices, bytes);
    }

    private void ensureBatch(int bytes) {
        if (batches.remaining() < bytes) batches = grow(batches, bytes);
    }

    private static ByteBuffer grow(ByteBuffer old, int required) {
        ByteBuffer replacement = direct(Math.max(old.capacity() * 2, old.position() + required));
        old.flip();
        replacement.put(old);
        return replacement;
    }

    private static ByteBuffer copy(ByteBuffer source) {
        ByteBuffer result = direct(source.remaining());
        result.put(source.duplicate()).flip();
        return result.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
    }

    private static ByteBuffer direct(int size) {
        return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
    }
}
