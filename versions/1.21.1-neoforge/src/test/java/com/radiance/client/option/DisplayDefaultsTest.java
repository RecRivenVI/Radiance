package com.radiance.client.option;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;
import org.junit.jupiter.api.Test;

final class DisplayDefaultsTest {
    @Test
    void migratesInitialDefaultsWithoutChangingOtherChoices() {
        var props = new Properties();
        props.setProperty("uiWhiteNits", "200.0");
        props.setProperty("bloomStrength", "0.08");
        props.setProperty("hdrEnabled", "false");
        props.setProperty("displayWhiteNits", "240.0");
        Options.migrateDisplayDefaults(props);
        assertEquals("0", props.getProperty("uiWhiteNits"));
        assertEquals("0.03", props.getProperty("bloomStrength"));
        assertEquals("false", props.getProperty("hdrEnabled"));
        assertEquals("240.0", props.getProperty("displayWhiteNits"));
    }

    @Test
    void preservesCustomSettingsAndDoesNotRemigrateLaterEdits() {
        var props = new Properties();
        props.setProperty("uiWhiteNits", "160.0");
        props.setProperty("bloomStrength", "0.12");
        Options.migrateDisplayDefaults(props);
        assertEquals("160.0", props.getProperty("uiWhiteNits"));
        assertEquals("0.12", props.getProperty("bloomStrength"));
        props.setProperty("uiWhiteNits", "200.0");
        props.setProperty("bloomStrength", "0.08");
        Options.migrateDisplayDefaults(props);
        assertEquals("200.0", props.getProperty("uiWhiteNits"));
        assertEquals("0.08", props.getProperty("bloomStrength"));
    }
}
