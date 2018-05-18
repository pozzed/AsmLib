package net.futureclient.asm;

import net.futureclient.asm.config.ConfigManager;
import net.futureclient.asm.transformer.wrapper.LaunchWrapperTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class AsmLib {

    public static final Logger LOGGER = LogManager.getLogger("asmlib");
    private static final String VERSION = "0.1";

    private static ConfigManager configManager = new ConfigManager();

    static {
        LOGGER.info("AsmLib loaded by: " + LaunchWrapperTransformer.class.getClassLoader().getClass().getName());
    }

    private AsmLib() {}

    public static ConfigManager getConfigManager() {
        return configManager;
    }
}
