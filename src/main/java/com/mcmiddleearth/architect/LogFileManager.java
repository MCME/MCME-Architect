package com.mcmiddleearth.architect;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Attaches a rotating file handler to Architect's plugin logger and keeps it off the server log. */
public final class LogFileManager {
    private LogFileManager() {}
    private static FileHandler handler;

    public static void install(JavaPlugin plugin) {
        Logger logger = plugin.getLogger();
        if (!plugin.getConfig().getBoolean("logging.file.enabled", true)) return;
        boolean alsoServerLog = plugin.getConfig().getBoolean("logging.file.alsoServerLog", false);
        int maxSizeMB     = plugin.getConfig().getInt("logging.file.maxSizeMB", 10);
        int maxFiles      = Math.max(1, plugin.getConfig().getInt("logging.file.maxFiles", 5));
        int retentionDays = plugin.getConfig().getInt("logging.file.retentionDays", 14);
        try {
            File logDir = new File(plugin.getDataFolder(), "logs");
            if (!logDir.exists() && !logDir.mkdirs()) {
                logger.warning("Could not create log directory " + logDir + "; Architect file logging disabled.");
                return;
            }
            pruneOldLogs(logDir, retentionDays);
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String pattern = new File(logDir, "architect_" + ts + "_%g.log").getAbsolutePath();
            handler = new FileHandler(pattern, maxSizeMB * 1024 * 1024, maxFiles, true);
            handler.setFormatter(new SingleLineFormatter());
            handler.setLevel(Level.ALL);
            logger.setLevel(Level.ALL);
            logger.addHandler(handler);
            logger.setUseParentHandlers(alsoServerLog);
            logger.info("Architect file logging started -> " + pattern);
        } catch (IOException | SecurityException ex) {
            logger.setUseParentHandlers(true);
            logger.log(Level.WARNING, "Could not start Architect file logging; using the server log instead.", ex);
        }
    }

    public static void uninstall(JavaPlugin plugin) {
        if (handler != null) {
            handler.flush();
            handler.close();
            plugin.getLogger().removeHandler(handler);
            handler = null;
        }
    }

    /** Flush pending records to disk (used by tests and before reads). */
    public static void flush() { if (handler != null) handler.flush(); }

    private static void pruneOldLogs(File dir, int retentionDays) {
        long cutoff = System.currentTimeMillis() - retentionDays * 24L * 60 * 60 * 1000;
        File[] files = dir.listFiles((d, n) -> n.startsWith("architect_") && n.endsWith(".log"));
        if (files != null) for (File f : files) if (f.lastModified() < cutoff) f.delete();
    }

    private static final class SingleLineFormatter extends Formatter {
        private final SimpleDateFormat time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        @Override public String format(LogRecord r) {
            StringBuilder sb = new StringBuilder(128)
                    .append('[').append(time.format(new Date(r.getMillis()))).append("] [")
                    .append(r.getLevel()).append("] ").append(formatMessage(r)).append(System.lineSeparator());
            if (r.getThrown() != null) {
                StringWriter sw = new StringWriter();
                r.getThrown().printStackTrace(new PrintWriter(sw));
                sb.append(sw);
            }
            return sb.toString();
        }
    }
}
