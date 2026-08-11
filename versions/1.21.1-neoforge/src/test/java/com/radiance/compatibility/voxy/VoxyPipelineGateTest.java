package com.radiance.compatibility.voxy;

import static org.junit.jupiter.api.Assertions.*;

import com.radiance.client.pipeline.Module;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.pipeline.config.AttributeConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class VoxyPipelineGateTest {
    @Test
    void externalAndRasterPipelinesCannotReadCompactProxyBuffers() throws Exception {
        var previous = Pipeline.INSTANCE;
        try {
            var constructor = Pipeline.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Pipeline.INSTANCE = constructor.newInstance();
            assertFalse(Pipeline.supportsVoxyGeometry());
            var module = new Module();
            module.name = "render_pipeline.module.ray_tracing.name";
            var path = new AttributeConfig();
            path.name = "render_pipeline.module.ray_tracing.attribute.shader_pack_path";
            module.attributeConfigs = List.of(path);
            Pipeline.INSTANCE.getModules().add(module);
            for (String supported :
                    List.of(
                            "",
                            "shaders/world/ray_tracing/vanilla-pt.zip",
                            "shaders/world/ray_tracing/advanced.zip")) {
                path.value = supported;
                assertTrue(Pipeline.supportsVoxyGeometry(), supported);
            }
            for (String external :
                    List.of(
                            "shaderpacks/vanilla-pt.zip",
                            "shaderpacks/advanced.zip",
                            "shaders/world/ray_tracing/custom.zip")) {
                path.value = external;
                assertFalse(Pipeline.supportsVoxyGeometry(), external);
            }
        } finally {
            Pipeline.INSTANCE = previous;
        }
    }
}
