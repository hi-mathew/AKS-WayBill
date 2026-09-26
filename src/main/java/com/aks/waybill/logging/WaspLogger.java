package com.aks.waybill.logging;

import com.aks.waybill.config.AppPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.Handler;

/**
 * Central technical logger for W.A.S.P.
 *
 * AuditLogService remains responsible for business/audit history; this logger
 * records technical application events and exceptions for troubleshooting.
 */
public final class WaspLogger {
    private static final String LOGGER_NAME = "com.aks.waybill";
    private static final String CONFIG_FILE_NAME = "logging.properties";
    private static final String DEFAULT_ROOT_LEVEL = "INFO";
    private static final String DEFAULT_CONSOLE_LEVEL = "INFO";
    private static final Logger ROOT_LOGGER = Logger.getLogger(LOGGER_NAME);
    private static volatile boolean initialized;

    private WaspLogger() {
    }

    public static synchronized void initialize() {
        if (initialized) return;

        try {
            Path logDirectory = AppPaths.dataDirectory().resolve("logs");
            Files.createDirectories(logDirectory);

            ROOT_LOGGER.setUseParentHandlers(false);
            LoggingConfiguration configuration = loadConfiguration();
            ROOT_LOGGER.setLevel(configuration.rootLevel());

            for (Handler handler : ROOT_LOGGER.getHandlers()) {
                ROOT_LOGGER.removeHandler(handler);
                try {
                    handler.close();
                } catch (Exception ignored) {
                    // Do not prevent application startup because an old handler failed to close.
                }
            }

            Path logFile = logDirectory.resolve("wasp.log");
            FileHandler fileHandler = new FileHandler(logFile.toString(), 5 * 1024 * 1024, 5, true);
            fileHandler.setLevel(Level.ALL);
            fileHandler.setFormatter(new WaspFormatter());
            ROOT_LOGGER.addHandler(fileHandler);

            // Keep console logging useful during IntelliJ/Maven development,
            // but only at INFO and above. Packaged users primarily use the file log.
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(configuration.consoleLevel());
            consoleHandler.setFormatter(new WaspFormatter());
            ROOT_LOGGER.addHandler(consoleHandler);

            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                    error("Unhandled exception on thread " + thread.getName(), throwable));

            initialized = true;
            info("W.A.S.P technical logging initialized. Log directory: " + logDirectory
                    + ", level=" + configuration.rootLevel().getName()
                    + ", consoleLevel=" + configuration.consoleLevel().getName());
        } catch (IOException exception) {
            // Logging must never prevent W.A.S.P from starting. Fall back to a
            // console-only handler when the file cannot be created.
            ROOT_LOGGER.setUseParentHandlers(false);
            ROOT_LOGGER.setLevel(Level.INFO);
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(Level.ALL);
            consoleHandler.setFormatter(new WaspFormatter());
            ROOT_LOGGER.addHandler(consoleHandler);
            initialized = true;
            ROOT_LOGGER.log(Level.SEVERE, "Unable to initialize W.A.S.P file logging", exception);
        }
    }

    public static Logger get() {
        if (!initialized) initialize();
        return ROOT_LOGGER;
    }

    public static void info(String message) {
        get().log(Level.INFO, message);
    }

    public static void warning(String message) {
        get().log(Level.WARNING, message);
    }

    public static void error(String message, Throwable throwable) {
        get().log(Level.SEVERE, message, throwable);
    }

    /** Logs diagnostic information when DEBUG/FINE is enabled. */
    public static void debug(String message) {
        get().log(Level.FINE, message);
    }

    /** Logs very detailed diagnostic information when TRACE/FINEST is enabled. */
    public static void trace(String message) {
        get().log(Level.FINEST, message);
    }

    public static Path configurationFile() {
        return AppPaths.dataDirectory().resolve(CONFIG_FILE_NAME);
    }

    private static LoggingConfiguration loadConfiguration() {
        Path file = configurationFile();
        Properties properties = new Properties();
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                try (var writer = Files.newBufferedWriter(file, java.nio.charset.StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    writer.write("# W.A.S.P technical logging configuration"); writer.newLine();
                    writer.write("# Restart W.A.S.P after changing this file."); writer.newLine();
                    writer.write("# Supported levels: SEVERE/ERROR, WARNING, INFO, DEBUG/FINE, TRACE/FINEST, ALL, OFF"); writer.newLine();
                    writer.write("level=" + DEFAULT_ROOT_LEVEL); writer.newLine();
                    writer.write("consoleLevel=" + DEFAULT_CONSOLE_LEVEL); writer.newLine();
                }
            }
            try (InputStream input = Files.newInputStream(file)) { properties.load(input); }
        } catch (IOException exception) {
            return new LoggingConfiguration(Level.INFO, Level.INFO);
        }
        return new LoggingConfiguration(
                parseLevel(properties.getProperty("level"), Level.INFO, "level", file),
                parseLevel(properties.getProperty("consoleLevel"), Level.INFO, "consoleLevel", file));
    }

    private static Level parseLevel(String value, Level defaultLevel, String property, Path file) {
        if (value == null || value.isBlank()) return defaultLevel;
        String level = value.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (level) {
            case "ERROR", "SEVERE" -> Level.SEVERE;
            case "WARNING", "WARN" -> Level.WARNING;
            case "INFO" -> Level.INFO;
            case "DEBUG", "FINE" -> Level.FINE;
            case "FINER" -> Level.FINER;
            case "TRACE", "FINEST" -> Level.FINEST;
            case "ALL" -> Level.ALL;
            case "OFF" -> Level.OFF;
            default -> {
                System.err.println("Invalid W.A.S.P logging level '" + value + "' for " + property + " in " + file
                        + "; using " + defaultLevel.getName());
                yield defaultLevel;
            }
        };
    }

    private record LoggingConfiguration(Level rootLevel, Level consoleLevel) {}

    private static final class WaspFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            String logger = record.getLoggerName() == null ? "" : record.getLoggerName();
            int lastDot = logger.lastIndexOf('.');
            if (lastDot >= 0 && lastDot < logger.length() - 1) {
                logger = logger.substring(lastDot + 1);
            }

            StringBuilder output = new StringBuilder();
            output.append(java.time.LocalDateTime.now())
                    .append(" [")
                    .append(record.getLevel().getName())
                    .append("] ")
                    .append(logger)
                    .append(" - ")
                    .append(formatMessage(record))
                    .append(System.lineSeparator());

            if (record.getThrown() != null) {
                java.io.StringWriter writer = new java.io.StringWriter();
                try (java.io.PrintWriter printWriter = new java.io.PrintWriter(writer)) {
                    record.getThrown().printStackTrace(printWriter);
                }
                output.append(writer).append(System.lineSeparator());
            }
            return output.toString();
        }
    }
}
