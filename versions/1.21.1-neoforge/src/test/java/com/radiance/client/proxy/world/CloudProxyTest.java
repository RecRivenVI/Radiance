package com.radiance.client.proxy.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CloudProxyTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        Bootstrap.bootStrap();
    }

    @Test
    void materialKeepsAuthoredRgbAndSourceCoverageWithoutRasterShading() {
        for (int rgb : new int[] {0, 0xffffff, 0x345678, 0xae2049})
            for (int alpha : new int[] {0, 63, 127, 255}) {
                int value = CloudProxy.materialColor((alpha << 24) | rgb);
                assertEquals(rgb, value & 0x00ffffff);
                assertEquals(Math.round(alpha * .8F), value >>> 24);
            }
    }
}
