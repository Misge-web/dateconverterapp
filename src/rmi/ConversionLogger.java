package rmi;

import java.io.IOException;
import java.util.logging.*;

/**
 * Centralised logging for the Date Converter server.
 *
 * Writes to both the console and a rolling file (logs/server.log).
 * Uses java.util.logging — no external dependencies.
 *
 * Usage:
 *   ConversionLogger.get().info("message");
 *   ConversionLogger.get().warning("bad input: " + detail);
 */
public final class ConversionLogger {

    private static final Logger LOGGER = Logger.getLogger("DateConverter");
    private static volatile boolean initialised = false;

    private ConversionLogger() {}

    /** Returns the shared Logger, initialising handlers on first call. */
    public static Logger get() {
        if (!initialised) init();
        return LOGGER;
    }

    private static synchronized void init() {
        if (initialised) return;

        LOGGER.setUseParentHandlers(false);   // suppress root handler
        LOGGER.setLevel(Level.ALL);

        // ── Console handler ──────────────────────────────────────────────────
        ConsoleHandler console = new ConsoleHandler();
        console.setLevel(Level.ALL);
        console.setFormatter(new SimpleFormatter() {
            private static final String FMT = "[%1$tF %1$tT] [%2$-7s] %3$s%n";
            @Override
            public synchronized String format(LogRecord r) {
                return String.format(FMT,
                        r.getMillis(), r.getLevel().getLocalizedName(),
                        r.getMessage());
            }
        });
        LOGGER.addHandler(console);

        // ── File handler (logs/server.log, 1 MB, 3 rotating files) ──────────
        try {
            java.io.File logDir = new java.io.File("logs");
            if (!logDir.exists()) logDir.mkdirs();

            FileHandler file = new FileHandler("logs/server.log",
                    1024 * 1024, 3, true);   // 1 MB, 3 files, append
            file.setLevel(Level.ALL);
            file.setFormatter(new SimpleFormatter() {
                private static final String FMT = "[%1$tF %1$tT] [%2$-7s] %3$s%n";
                @Override
                public synchronized String format(LogRecord r) {
                    return String.format(FMT,
                            r.getMillis(), r.getLevel().getLocalizedName(),
                            r.getMessage());
                }
            });
            LOGGER.addHandler(file);
        } catch (IOException e) {
            LOGGER.warning("Could not open log file: " + e.getMessage());
        }

        initialised = true;
    }
}
