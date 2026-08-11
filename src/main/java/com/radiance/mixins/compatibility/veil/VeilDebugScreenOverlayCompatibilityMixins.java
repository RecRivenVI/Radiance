package com.radiance.mixins.compatibility.veil;

import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Applies Radiance's mixin plugin compatibility pass after Veil augments the F3 overlay.
 */
@Mixin(DebugScreenOverlay.class)
public abstract class VeilDebugScreenOverlayCompatibilityMixins {
}
