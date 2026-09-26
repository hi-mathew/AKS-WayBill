# W.A.S.P. - Waybill Automation & Shipping Platform

W.A.S.P. is a JavaFX desktop application for creating, managing and reporting transportation waybills.

## Technology

- Java 21
- JavaFX 21.0.6
- Maven 3.9.x
- SQLite
- Apache POI for Word and Excel output
- OpenPDF for PDF output

## Features

- Waybill creation, editing, viewing and copying
- Configurable waybill numbering
- Company, Carrier and Location master data
- Company selection with automatic contact/address/phone/email population
- Saved Waybills search, filtering, pagination and Excel export
- PDF and Microsoft Word report generation
- Configurable Terms & Conditions with clause reordering
- Configurable list page sizes
- User Management with roles, enable/disable and password reset
- Admin-only Audit Log
- SQLite database backup and restore
- Dashboard with recent waybills and application statistics
- About screen and application information

## Company behavior

- Shipper and Consignee use the same `company` master table.
- Existing companies can be searched by company name, contact, phone, or email.
- Selecting an existing company automatically populates contact, address, phone, and email.
- A new company name can be typed directly into a waybill and its other fields remain editable.
- When a waybill is saved, a newly entered company is added to the company master when it does not already exist.
- The Companies screen supports search, add, edit, activate/deactivate, pagination and double-click edit.

## Reports and exports

- Saved Waybills can export the currently filtered results to Excel.
- Excel output includes W.A.S.P. styling, filters, borders, wrapped text, freeze panes and print configuration.
- View Waybill and Saved Waybills support PDF and Word report generation.
- Generated filenames are based on the waybill number and the user chooses the save location.

## Database

The application uses SQLite. No external database server is required.

During IntelliJ/Maven development, the database is stored in the project/application data directory. Packaged installations store mutable production data under `%LOCALAPPDATA%\AKS-Waybill`.

## Running in IntelliJ IDEA

Use the Maven tool window and run:

`Plugins -> javafx -> javafx:run`

The project requires JDK 21.

Alternatively, run the normal Maven lifecycle from the project root:

```text
mvn clean install
```

## Windows EXE Packaging

The production distribution is a Windows EXE installer created with JDK 21 `jpackage`. The installer bundles a Java runtime, application JARs and JavaFX dependencies, so end users do not need Java, Maven or JavaFX installed separately.

### Packaging prerequisites

1. JDK 21 with `java`, `javac` and `jpackage` on PATH.
2. Maven on PATH, or run the equivalent Maven build from IntelliJ IDEA.
3. WiX Toolset 3.x installed and discoverable by `jpackage` for Windows EXE generation.
4. A Windows 10/11 build machine.

### Build the installer

From the project root:

```powershell
powershell -ExecutionPolicy Bypass -File .\packaging\package-windows.ps1
```

Or run:

```text
packaging\package-windows.bat
```

The installer is generated under:

```text
target\installer\W.A.S.P-1.4.0.exe
```

### Persistent production data

Packaged applications store mutable data under:

```text
%LOCALAPPDATA%\AKS-Waybill\
    waybill.db
    backups\
```

The database is therefore separate from the installed application files and can survive application upgrades.

Development data remains separate under the project-local `data` directory.

### Upgrade behavior

Use the same `--win-upgrade-uuid` for future releases. Increase the Maven/jpackage application version for each release while keeping the upgrade UUID unchanged so Windows recognizes the installer as an upgrade.

Do not ship a pre-populated `waybill.db` inside the installer.

### First launch

If `%LOCALAPPDATA%\AKS-Waybill\waybill.db` does not exist, SQLite creates it and the application's normal database initialization creates the required tables and initial data.


## Technical Logging

W.A.S.P. writes technical logs to `logs\wasp.log` under the persistent application data directory. The default logging level is `INFO`.

The logging level can be changed without rebuilding the application by editing:

`%LOCALAPPDATA%\W.A.S.P-Data\logging.properties`

The file is created automatically on first startup with:

```properties
level=INFO
consoleLevel=INFO
```

Supported values are `SEVERE`/`ERROR`, `WARNING`, `INFO`, `DEBUG`/`FINE`, `TRACE`/`FINEST`, `ALL`, and `OFF`. `consoleLevel` controls console output separately. Restart W.A.S.P. after changing the file.
