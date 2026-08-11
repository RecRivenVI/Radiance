/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.radiance.bootstrap.ui;

import java.nio.ByteBuffer;

/**
 * Receives RGBA8 texture uploads while a loading scene is initialized. The pixel buffer is valid
 * for the duration of the call; a sink which needs it later must copy it before returning.
 */
@FunctionalInterface
public interface DrawSink {
    void texture(int key, int width, int height, ByteBuffer rgba, boolean linear, boolean clampToEdge);
}
