/**
 * The single ownership boundary for translating Veil's OpenGL-oriented rendering contracts to
 * Radiance's Vulkan renderer.
 *
 * <p>Mixin classes only intercept Veil calls and delegate here. Radiance core and other mod
 * adapters use {@link com.radiance.compatibility.veil.VeilAdapter}; specialized adapter classes
 * own framebuffer, shader, dynamic-buffer, stage, world-geometry and vertex-array policy.</p>
 */
package com.radiance.compatibility.veil;
