package com.mcmiddleearth.architect;

import java.util.logging.Level;
import java.util.logging.Logger;

/** The single channel all Architect logging flows through (see {@link LogFileManager}). */
public final class Log {
    private Log() {}
    private static Logger log() { return ArchitectPlugin.getPluginInstance().getLogger(); }
    public static void info(String msg)                { log().info(msg); }
    public static void warn(String msg)                { log().warning(msg); }
    public static void warn(String msg, Throwable t)   { log().log(Level.WARNING, msg, t); }
    public static void error(String msg)               { log().severe(msg); }
    public static void error(String msg, Throwable t)  { log().log(Level.SEVERE, msg, t); }
    public static void debug(String msg)               { log().fine(msg); }
}
