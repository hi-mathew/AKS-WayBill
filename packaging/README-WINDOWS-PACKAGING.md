# AKS Waybill - Windows Packaging

## Recommended distribution

The production distribution is a Windows EXE installer created with JDK 21 `jpackage`.
The installer bundles a Java runtime, application JARs and JavaFX dependencies. End users do not need Java, JDK, Maven or JavaFX installed separately.

## Prerequisites on the build machine

1. JDK 21 with `java`, `javac` and `jpackage` on PATH.
2. Maven on PATH, or run the equivalent Maven command from IntelliJ.
3. WiX Toolset 3.x installed and discoverable by `jpackage` for Windows EXE generation.
4. Run from a Windows 10/11 build machine.

## Build

From the project root, run:

```powershell
powershell -ExecutionPolicy Bypass -File .\packaging\package-windows.ps1
```

Or double-click `packaging\package-windows.bat`.

The installer will be generated under:

```text
target\installer\AKS Waybill-1.3.0.exe
```

## Persistent data location

When running from the packaged EXE, mutable data is stored under:

```text
%LOCALAPPDATA%\AKS-Waybill\
    waybill.db
    backups\
```

The application itself is installed separately (normally under the selected Windows application directory). The database is therefore not stored under `Program Files` and should survive application upgrades.

During IntelliJ/Maven development, the existing project-local location remains:

```text
<project>\data\waybill.db
```

This keeps development and production data separate.

## Upgrade behavior

The same `--win-upgrade-uuid` is used for future releases. Increase the Maven/jpackage version for each release while keeping the UUID unchanged so Windows can recognize the installer as an upgrade.

Do not ship a pre-populated `waybill.db` inside the installer.

## First launch

If `%LOCALAPPDATA%\AKS-Waybill\waybill.db` does not exist, SQLite creates it and the application's normal database initialization creates the required tables and initial data.
