package com.radiance.client.render;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Supplemental wiring guard; actual rendering is exercised by the isolated Catnip smoke. */
class PonderDefaultContractTest {
    @Test void activeConfigurationCannotInstallArchivedPtHooks() throws Exception {
        try (var stream = getClass().getResourceAsStream("/radiance.mixins.json")) {
            assertNotNull(stream);
            var client = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("client");
            var names = new java.util.HashSet<String>();
            client.forEach(value -> names.add(value.getAsString()));
            assertFalse(names.contains("compatibility.ponder.PonderScenePathTracingMixins"));
            assertFalse(names.contains("compatibility.ponder.PonderUILifecycleMixins"));
            assertTrue(names.contains("compatibility.catnip.CatnipRasterScreenMixins"));
            assertTrue(names.contains("compatibility.ponder.PonderUIRenderHelperMixins"));
        }
    }
}
