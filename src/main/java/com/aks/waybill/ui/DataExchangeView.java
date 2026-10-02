package com.aks.waybill.ui;

import com.aks.waybill.service.DataExchangeService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-facing Data Exchange export screen. Import/consolidation is intentionally a later milestone. */
public final class DataExchangeView extends AppView {
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm:ss a").withZone(ZoneId.systemDefault());

    private final Label installationLabel = new Label();
    private final Label lastExportLabel = new Label();
    private final Label messageLabel = new Label();
    private final Button fullExportButton = button("Full Export", "primary-button");
    private final Button incrementalExportButton = button("Incremental Export", "secondary-button");
    private final ProgressIndicator progress = new ProgressIndicator();
    private final TableView<DataExchangeService.ExportHistory> historyTable = new TableView<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wasp-data-exchange");
        t.setDaemon(true);
        return t;
    });

    public DataExchangeView() {
        super("Data Exchange", "Export W.A.S.P. business records for controlled consolidation into the organization's central database.");
        getStyleClass().add("data-exchange-view");
        VBox overview = overviewCard();
        VBox export = exportCard();
        VBox history = historyCard();
        VBox body = new VBox(16, overview, export, history);
        body.setFillWidth(true);
        VBox.setVgrow(history, Priority.ALWAYS);
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("content-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);
        loadState();
    }

    private VBox overviewCard() {
        VBox card = card();
        Label title = title("Local W.A.S.P. Installation");
        Label description = description("Each W.A.S.P. installation has a stable Installation ID. Export packages use this identity together with Global IDs so records from different laptops can be consolidated safely.");
        installationLabel.getStyleClass().add("settings-note");
        installationLabel.setWrapText(true);
        lastExportLabel.getStyleClass().add("settings-note");
        lastExportLabel.setWrapText(true);
        card.getChildren().addAll(title, description, installationLabel, lastExportLabel);
        return card;
    }

    private VBox exportCard() {
        VBox card = card();
        Label title = title("Create Data Exchange Package");
        Label description = description("A Full Export is used for the first transfer from this database. After a successful Full Export, Incremental Export includes records created or changed since the previous successful export.");

        fullExportButton.setOnAction(e -> chooseAndExport(DataExchangeService.ExportType.FULL));
        incrementalExportButton.setOnAction(e -> chooseAndExport(DataExchangeService.ExportType.INCREMENTAL));
        progress.setPrefSize(28, 28);
        progress.setVisible(false);
        progress.setManaged(false);
        messageLabel.setWrapText(true);
        messageLabel.getStyleClass().add("settings-message");

        HBox actions = new HBox(10, fullExportButton, incrementalExportButton, progress);
        actions.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().addAll(title, description, actions, messageLabel);
        return card;
    }

    private VBox historyCard() {
        VBox card = card();
        Label title = title("Export History");
        Label description = description("Only successfully completed exports are used as the starting point for the next Incremental Export. Failed exports do not advance the incremental checkpoint.");

        TableColumn<DataExchangeService.ExportHistory, String> date = column("Completed", 175, h -> format(h.completedAt()));
        TableColumn<DataExchangeService.ExportHistory, String> type = column("Type", 110, h -> h.type().name());
        TableColumn<DataExchangeService.ExportHistory, String> records = column("Records", 90, h -> String.format("%,d", h.recordCount()));
        TableColumn<DataExchangeService.ExportHistory, String> status = column("Status", 110, DataExchangeService.ExportHistory::status);
        TableColumn<DataExchangeService.ExportHistory, String> packageColumn = column("Package", 420, h -> h.packagePath() == null ? "—" : h.packagePath());
        historyTable.getColumns().setAll(date, type, records, status, packageColumn);
        historyTable.setPlaceholder(new Label("No Data Exchange exports have been created yet."));
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        historyTable.setPrefHeight(220);
        VBox.setVgrow(historyTable, Priority.ALWAYS);
        card.getChildren().addAll(title, description, historyTable);
        return card;
    }

    private <T> TableColumn<DataExchangeService.ExportHistory, T> column(String heading, double width,
                                                                           java.util.function.Function<DataExchangeService.ExportHistory, T> mapper) {
        TableColumn<DataExchangeService.ExportHistory, T> column = new TableColumn<>(heading);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new javafx.beans.property.SimpleObjectProperty<>(mapper.apply(cell.getValue())));
        return column;
    }

    private void loadState() {
        try {
            installationLabel.setText("Installation ID: " + com.aks.waybill.db.Database.getDataExchangeInstallationId());
            List<DataExchangeService.ExportHistory> rows = DataExchangeService.history();
            historyTable.setItems(FXCollections.observableArrayList(rows));
            DataExchangeService.ExportHistory latest = rows.stream().filter(h -> "COMPLETED".equals(h.status())).findFirst().orElse(null);
            lastExportLabel.setText(latest == null
                    ? "Last successful export: None"
                    : "Last successful export: " + latest.type().name() + " on " + format(latest.completedAt()) + " (" + String.format("%,d", latest.recordCount()) + " records)");
            incrementalExportButton.setDisable(!DataExchangeService.hasSuccessfulFullExport());
        } catch (RuntimeException e) {
            setMessage("Unable to load Data Exchange information: " + safe(e), true);
        }
    }

    private void chooseAndExport(DataExchangeService.ExportType type) {
        if (progress.isVisible()) return;
        Window owner = getScene() == null ? null : getScene().getWindow();
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(type == DataExchangeService.ExportType.FULL ? "Select Folder for Full Export" : "Select Folder for Incremental Export");
        Path defaultDir = Paths.get(System.getProperty("user.home"), "Documents", "W.A.S.P-DataExchange");
        try { java.nio.file.Files.createDirectories(defaultDir); chooser.setInitialDirectory(defaultDir.toFile()); } catch (Exception ignored) {}
        java.io.File selected = chooser.showDialog(owner);
        if (selected == null) return;

        fullExportButton.setDisable(true);
        incrementalExportButton.setDisable(true);
        progress.setVisible(true);
        progress.setManaged(true);
        setMessage(type == DataExchangeService.ExportType.FULL ? "Creating Full Export package…" : "Creating Incremental Export package…", false);

        executor.submit(() -> {
            try {
                DataExchangeService.ExportResult result = DataExchangeService.export(type, selected.toPath());
                Platform.runLater(() -> {
                    setMessage(type.name().charAt(0) + type.name().substring(1).toLowerCase() + " Export completed successfully.\n" +
                            "Records: " + String.format("%,d", result.recordCount()) + "\n" +
                            "Package: " + result.packagePath() + "\n" +
                            "SHA-256: " + result.packageHash(), false);
                    progress.setVisible(false);
                    progress.setManaged(false);
                    loadState();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setMessage(safe(e), true);
                    progress.setVisible(false);
                    progress.setManaged(false);
                    loadState();
                });
            }
        });
    }

    private void setMessage(String message, boolean error) {
        messageLabel.setText(message);
        messageLabel.getStyleClass().removeAll("success-message", "error-message");
        messageLabel.getStyleClass().add(error ? "error-message" : "success-message");
    }

    private static VBox card() {
        VBox box = new VBox(10);
        box.getStyleClass().add("settings-card");
        box.setPadding(new Insets(16));
        return box;
    }

    private static Label title(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("settings-card-title");
        return label;
    }

    private static Label description(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("settings-card-description");
        label.setWrapText(true);
        return label;
    }

    private static Button button(String text, String style) {
        Button button = new Button(text);
        button.getStyleClass().add(style);
        return button;
    }

    private static String format(String value) {
        if (value == null || value.isBlank()) return "—";
        try { return DISPLAY.format(Instant.parse(value)); } catch (Exception e) { return value; }
    }

    private static String safe(Throwable e) { return e.getMessage() == null ? "Data Exchange export failed." : e.getMessage(); }
}
