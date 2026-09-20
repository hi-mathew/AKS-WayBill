package com.aks.waybill.config;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppPaths {

    private AppPaths() {
    }

    /**
     * Returns the directory containing the running application.
     *
     * For a jpackage Windows application, jpackage.app-path points to the
     * actual launcher (.exe), so the database is kept beside the executable.
     * During development, the code source is used; when running from
     * target/classes, the project root is used so the database is not hidden
     * inside the compiled classes directory.
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

    public static Path dataDirectory() {
        return applicationDirectory().resolve("data");
    }

    public static Path databaseFile() {
        return dataDirectory().resolve("waybill.db");
    }

    public static String jdbcUrl() {
        return "jdbc:sqlite:" + databaseFile();
    }
}
