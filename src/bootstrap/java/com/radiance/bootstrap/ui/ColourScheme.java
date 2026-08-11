/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.radiance.bootstrap.ui;

/** Colours used by the NeoForge 4.0.43 early-display screen. */
public enum ColourScheme {
    RED(new Colour(239, 50, 61), new Colour(255, 255, 255)),
    BLACK(new Colour(0, 0, 0), new Colour(255, 255, 255));

    private final Colour background;
    private final Colour foreground;

    ColourScheme(final Colour background, final Colour foreground) {
        this.background = background;
        this.foreground = foreground;
    }

    public Colour background() {
        return background;
    }

    public Colour foreground() {
        return foreground;
    }

    public record Colour(int red, int green, int blue) {
        public int packedint(int a) {
            return ((a & 0xff) << 24) | ((blue & 0xff) << 16) | ((green & 0xff) << 8) | (red & 0xff);
        }
    }
}
