package com.radiance.audit.mixin;

import com.radiance.audit.ExperimentAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Isolated test input only: use the real renderer with controlled opposite roll residuals. */
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.spring.SpringRenderer", remap = false)
public abstract class SpringTwistFixtureMixin {
    @ModifyArg(
            method = "renderSafe",
            at = @At(value = "INVOKE", target = "Lorg/joml/Matrix3d;rotateY(D)Lorg/joml/Matrix3d;"),
            index = 0,
            remap = false)
    private double audit$controlledStep(double sourceAngle) {
        String input = ExperimentAccess.getenv("RADIANCE_SPRING_TWIST_STEP");
        if (input == null) return sourceAngle;
        double degrees = Double.parseDouble(input);
        // Source residual is bounded by 45 degrees and has at least five spans.
        if (!Double.isFinite(degrees) || Math.abs(degrees) > 9.0)
            throw new IllegalArgumentException("Isolated spring twist step outside source range");
        return Math.toRadians(degrees);
    }
}
