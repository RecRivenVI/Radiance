package com.radiance.platform.neoforge;

import com.radiance.Radiance;
import com.radiance.client.RadianceClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value = Radiance.MOD_ID, dist = Dist.CLIENT)
public final class RadianceNeoForge {

    public RadianceNeoForge() {
        RadianceClient.initialize();
    }
}
