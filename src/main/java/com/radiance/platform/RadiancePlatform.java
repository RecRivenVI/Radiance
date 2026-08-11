package com.radiance.platform;

import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;

public final class RadiancePlatform {

    private RadiancePlatform() {
    }

    public static Path gameDirectory() {
        return FMLPaths.GAMEDIR.get();
    }
}
