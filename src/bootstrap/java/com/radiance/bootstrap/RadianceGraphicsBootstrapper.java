package com.radiance.bootstrap;

import java.util.Locale;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Selects the Radiance provider when NeoForge still has its fresh default.
 */
public final class RadianceGraphicsBootstrapper implements GraphicsBootstrapper {
    private static final Logger LOGGER = LoggerFactory.getLogger("RadianceBootstrap");

    @Override
    public String name() {
        return "radiance";
    }

    @Override
    public void bootstrap(String[] arguments) {
        String launchTarget = option(arguments, "--launchTarget");
        if (!isClientLaunchTarget(launchTarget)) {
            return;
        }
        if (!FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL)) {
            LOGGER.info("Radiance early window remains disabled by earlyWindowControl=false");
            return;
        }
        String configured = FMLConfig.getConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER);
        if ("fmlearlywindow".equals(configured)) {
            FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER, "radiance");
            LOGGER.info("Selected Radiance for the fresh NeoForge early-window default");
        }
    }

    static boolean isClientLaunchTarget(String launchTarget) {
        return launchTarget != null && launchTarget.toLowerCase(Locale.ROOT).contains("client");
    }

    private static String option(String[] arguments, String key) {
        for (int i = 0; i + 1 < arguments.length; i++) {
            if (key.equals(arguments[i])) return arguments[i + 1];
        }
        return null;
    }
}
