package com.radiance.mixin_related;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SourceCompatibilityTest {
    @Test
    void aReviewedAdditiveInterfaceRequiresTheExactActualClassFingerprint() {
        String activity = "example.mixins.json:AdditiveAccessor from mod example";
        String digest = "4ba21b44859748e48364c7df927b317c897b96710394c121f944c3887b17ab32";
        com.radiance.api.render.ReviewedTransformations.register(
                activity, "example/Accessor.class", digest);
        assertTrue(com.radiance.api.render.ReviewedTransformations.matches(activity, digest));
        assertFalse(
                com.radiance.api.render.ReviewedTransformations.matches(
                        activity, "0" + digest.substring(1)));
        assertFalse(
                com.radiance.api.render.ReviewedTransformations.matches(
                        "other.json:ModelPartAccessor", digest));
        assertFalse(com.radiance.api.render.ReviewedTransformations.matches(activity, "missing"));
    }

    @Test
    void actualForeignMixinAndNonMixinTransformsRejectWithoutAListOfInstalledMods() {
        assertNull(SourceCompatibility.rejection("REASON", new String[] {"classloading"}));
        assertNull(
                SourceCompatibility.rejection(
                        "PLUGIN",
                        new String[] {
                            "mixin", "APP", "radiance.mixins.json:ModelPartPersistentMixins"
                        }));
        assertNull(SourceCompatibility.rejection("PLUGIN", new String[] {"mixin", "A"}));
        assertNotNull(
                SourceCompatibility.rejection(
                        "PLUGIN",
                        new String[] {"mixin", "APP", "unregistered.json:ModelMutation"}));
        assertNotNull(
                SourceCompatibility.rejection("TRANSFORMER", new String[] {"foreign", "shape"}));
        assertNotNull(SourceCompatibility.rejection("PLUGIN", new String[] {"foreign"}));
        assertNotNull(SourceCompatibility.rejection("PLUGIN", new String[0]));
    }
}
