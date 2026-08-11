package com.radiance.client.texture;

import static org.junit.jupiter.api.Assertions.*;

import com.radiance.client.UnsafeManager;
import com.radiance.mixins.vanilla_resource_tracker.FontManagerOwnershipMixins;
import java.lang.reflect.Field;
import java.util.HashMap;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

class FontLookupLifecycleTest {
    @Test
    void realLookupCannotReturnRetiredGenerationAtTheSameFontName() throws Exception {
        var id = ResourceLocation.withDefaultNamespace("default");
        var old = new FontSet(null, id);
        var fresh = new FontSet(null, id);
        var missing = new FontSet(null, ResourceLocation.withDefaultNamespace("missing"));
        var map = new HashMap<ResourceLocation, FontSet>();
        map.put(id, fresh);
        var manager = UnsafeManager.INSTANCE.allocateInstance(FontManager.class);
        set(manager, "fontSets", map);
        set(manager, "missingFontSet", missing);
        set(manager, "lastFontSetCache", old);
        assertSame(
                old, lookup(manager, id), "Original lookup reproduces a hit on the retired owner");
        applyHook(manager, id);
        assertSame(fresh, lookup(manager, id));
        applyHook(manager, id);
        assertSame(
                fresh,
                field(manager, "lastFontSetCache").get(manager),
                "A live cache is preserved");
        assertSame(fresh, lookup(manager, id));
        map.remove(id);
        applyHook(manager, id);
        assertSame(
                missing, lookup(manager, id), "Removal resolves the normal missing-font fallback");
        map.put(id, fresh);
        applyHook(manager, id);
        assertSame(fresh, lookup(manager, id), "A later registration remains reachable");
    }

    @Test
    void anotherFontAndAnEmptyCacheKeepVanillaLookupSemantics() throws Exception {
        var id = ResourceLocation.withDefaultNamespace("fixture");
        var other = ResourceLocation.withDefaultNamespace("other");
        var fresh = new FontSet(null, id);
        var previous = new FontSet(null, other);
        var map = new HashMap<ResourceLocation, FontSet>();
        map.put(id, fresh);
        var manager = UnsafeManager.INSTANCE.allocateInstance(FontManager.class);
        set(manager, "fontSets", map);
        set(manager, "missingFontSet", previous);
        set(manager, "lastFontSetCache", previous);
        applyHook(manager, id);
        assertSame(previous, field(manager, "lastFontSetCache").get(manager));
        assertSame(fresh, lookup(manager, id));
        set(manager, "lastFontSetCache", null);
        applyHook(manager, id);
        assertSame(fresh, lookup(manager, id));
    }

    private static FontSet lookup(FontManager manager, ResourceLocation id) throws Exception {
        var method =
                FontManager.class.getDeclaredMethod("getFontSetCached", ResourceLocation.class);
        method.setAccessible(true);
        return (FontSet) method.invoke(manager, id);
    }

    private static void applyHook(FontManager manager, ResourceLocation id) throws Exception {
        // Unit JVM has no Mixin transformer. Execute the production HEAD hook against the
        // actual manager fields, then continue into Minecraft's unmodified lookup body.
        var hook = new FontManagerOwnershipMixins();
        for (var name : new String[] {"fontSets", "missingFontSet", "lastFontSetCache"})
            set(hook, name, field(manager, name).get(manager));
        var method =
                FontManagerOwnershipMixins.class.getDeclaredMethod(
                        "radiance$validateFontOwner",
                        ResourceLocation.class,
                        CallbackInfoReturnable.class);
        method.setAccessible(true);
        method.invoke(hook, id, new CallbackInfoReturnable<FontSet>("lookup", false));
        set(manager, "lastFontSetCache", field(hook, "lastFontSetCache").get(hook));
    }

    private static Field field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void set(Object object, String name, Object value) throws Exception {
        field(object, name).set(object, value);
    }
}
