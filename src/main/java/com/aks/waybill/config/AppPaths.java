package com.aks.waybill.config;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Centralized paths for application binaries and persistent user data. */
public final class AppPaths {
    private static final String APP_NAME = "AKS-Waybill";

    private AppPaths() {
    }

    /**
     * Returns the directory containing the running application/launcher.
     * This is used for application resources only; packaged builds do not
     * store mutable database data here.
     */
    public static Path applicationDirectory() {
        String jpackageAppPath = System.getProperty("jpackage.app-path");
        if (jpackageAppPath != null && !jpackageAppPath.isBlank()) {
            Path launcher = Paths.get(jpackageAppPath).toAbsolutePath().normalize();
            if (Files.isRegularFile(launcher)) {
                return launcher.getParent();
            }
        }

        try {
            Path codeSource = Paths.get(
                    AppPaths.class.getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            ).toAbsolutePath().normalize();

            if (Files.isRegularFile(codeSource)) {
                return codeSource.getParent();
            }

            // Maven/IntelliJ development: .../<project>/target/classes
            if (codeSource.endsWith(Paths.get("target", "classes"))) {
                Path targetDirectory = codeSource.getParent();
                if (targetDirectory != null && targetDirectory.getFileName() != null
                        && "target".equalsIgnoreCase(targetDirectory.getFileName().toString())
                        && targetDirectory.getParent() != null) {
                    return targetDirectory.getParent();
                }
            }

            return codeSource;
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Unable to determine application directory", e);
        }
    }

    /** True when the application is running from a jpackage-generated launcher. */
    public static boolean isPackaged() {
        String path = System.getProperty("jpackage.app-path");
        return path != null && !path.isBlank();
    }

    /**
     * Persistent user-data directory. Packaged Windows installations use
     * %LOCALAPPDATA%\AKS-Waybill so database files are never written under
     * Program Files. During IntelliJ/Maven development we retain the existing
     * project-local data directory for convenience.
     */
    public static Path dataDirectory() {
        if (!isPackaged()) {
            return applicationDirectory().resolve("data");
        }

        String localAppData = System.getenv("LOCALAPPDATA");
        Path root;
        if (localAppData != null && !localAppData.isBlank()) {
            root = Paths.get(localAppData);
        } else {
            // Defensive fallback for unusual Windows environments.
            root = Paths.get(System.getProperty("user.home"), "AppData", "Local");
        }
        return root.resolve(APP_NAME);
    }

    public static Path backupDirectory() {
        return dataDirectory().resolve("backups");
    }

    public static Path databaseFile() {
        return dataDirectory().resolve("waybill.db");
    }

    public static String jdbcUrl() {
        return "jdbc:sqlite:" + databaseFile();
    }
}
