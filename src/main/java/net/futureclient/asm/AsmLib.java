package net.futureclient.asm;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class AsmLib {

    public static final String VERSION = "0.1";

    public static final Logger LOGGER = LogManager.getLogger("asmlib");

    private AsmLib() {}

    public static void init() {
        LOGGER.log(Level.INFO, "AsmLib version {} initialised.", VERSION);
    }
}
