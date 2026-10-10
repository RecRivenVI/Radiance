package io.github.recrivenvi.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {
    @Test
    void writerProducesStableIndentedOutput() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("a", List.of(1L, "b\"c"));
        value.put("d", Map.of());
        value.put("e", List.of(Map.of("f", true)));
        assertEquals(
                """
                {
                    "a": [1, "b\\"c"],
                    "d": {},
                    "e": [
                        {
                            "f": true
                        }
                    ]
                }
                """,
                Json.write(value));
        assertThrows(IllegalArgumentException.class, () -> Json.write(Double.NaN));
    }
}
