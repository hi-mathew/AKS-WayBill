package com.aks.waybill.ui;

import com.aks.waybill.config.AppPaths;

import com.aks.waybill.service.WaybillService;
import com.aks.waybill.service.SettingsService;
import com.aks.waybill.service.ExcelExportService;
import com.aks.waybill.security.SessionContext;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Searchable, paginated saved-waybill register. */
public final class SavedWaybillsView extends AppView {
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a");
    private int pageSize = SettingsService.getPageSize();

    private final Runnable onBack;
    private final java.util.function.LongConsumer onView;
    private final java.util.function.LongConsumer onEdit;
    private final TextField searchField = new TextField();
    { InputLimits.maxLength(searchField, 400); }
    private final DatePicker fromDate = new QuickDatePicker();
    private final DatePicker toDate = new QuickDatePicker();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final TableView<WaybillService.WaybillListRow> table = new TableView<>();
    private final PaginationControl pagination = new PaginationControl();
    private int currentPage = 0;
    private int totalPages = 1;
    private String sortKey = "created";
    private boolean sortAscending = false;

    public SavedWaybillsView() { this(() -> {}, id -> {}, id -> {}); }

    public SavedWaybillsView(Runnable onBack, java.util.function.LongConsumer onView, java.util.function.LongConsumer onEdit) {
        super("Saved Waybills", "Search, review and manage previously created transportation waybills.");
        this.onBack = onBack == null ? () -> {} : onBack;
        this.onView = onView == null ? id -> {} : onView;
        this.onEdit = onEdit == null ? id -> {} : onEdit;
        VBox content = buildContent();
        getChildren().add(content);
        VBox.setVgrow(content, Priority.ALWAYS);
        loadPage(0);
    }

    private VBox buildContent() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(4, 0, 30, 0));

        VBox filterCard = new VBox(12);
        filterCard.getStyleClass().add("settings-card");
        filterCard.setPadding(new Insets(18));
        Label filterTitle = new Label("Search & Filters"); filterTitle.getStyleClass().add("section-heading");
        searchField.getStyleClass().add("list-filter-control");
        searchField.setPromptText("Waybill no., shipper, consignee or carrier");
        searchField.setOnAction(event -> loadPage(0));
        fromDate.getStyleClass().add("list-filter-control");
        fromDate.setPromptText("Waybill date from"); fromDate.setPrefWidth(150);
        toDate.getStyleClass().add("list-filter-control");
        toDate.setPromptText("Waybill date to"); toDate.setPrefWidth(150);
        statusFilter.getStyleClass().add("list-filter-control");
        statusFilter.getItems().setAll("All", "DRAFT", "FINAL");
        statusFilter.setValue("All");
        statusFilter.setPrefWidth(130);
        Button search = button("Search", "primary-button"); search.getStyleClass().add("list-filter-action"); search.setOnAction(event -> loadPage(0));
        Button clear = button("Clear", "secondary-button"); clear.getStyleClass().add("list-filter-action"); clear.setOnAction(event -> { searchField.clear(); fromDate.setValue(null); toDate.setValue(null); statusFilter.setValue("All"); loadPage(0); });
        VBox searchBox = labeled("Search", searchField); VBox fromBox = labeled("Waybill Date From", fromDate); VBox toBox = labeled("Waybill Date To", toDate); VBox statusBox = labeled("Status", statusFilter);
        HBox searchRow = new HBox(12, searchBox, fromBox, toBox, statusBox, search, clear); searchRow.setAlignment(Pos.BOTTOM_LEFT); HBox.setHgrow(searchBox, Priority.ALWAYS); searchField.setMaxWidth(Double.MAX_VALUE);
        filterCard.getChildren().addAll(filterTitle, searchRow);

        TableColumn<WaybillService.WaybillListRow,String> number = column("Waybill No.", r -> r.waybillNumber(), 200);
        TableColumn<WaybillService.WaybillListRow,String> date = column("Date", r -> DISPLAY_DATE.format(r.waybillDate()), 100);
        TableColumn<WaybillService.WaybillListRow,String> created = column("Created On", r -> r.createdAt() == null ? "—" : DISPLAY_DATE_TIME.format(r.createdAt()), 175);
        TableColumn<WaybillService.WaybillListRow,String> shipper = columnWithTooltip("Shipper / Consignor", r -> r.shipperName(), 170);
        TableColumn<WaybillService.WaybillListRow,String> consignee = columnWithTooltip("Consignee / Receiver", r -> r.consigneeName(), 170);
        TableColumn<WaybillService.WaybillListRow,String> carrier = columnWithTooltip("Carrier", r -> r.carrierName(), 150);
        TableColumn<WaybillService.WaybillListRow,String> status = column("Status", r -> r.status(), 85);

        TableColumn<WaybillService.WaybillListRow, Void> actions = new TableColumn<>("Actions");
        actions.setPrefWidth(255);
        actions.setMinWidth(255);
        actions.setMaxWidth(255);
        actions.setCellFactory(column -> new TableCell<>() {
            private final Button viewButton = iconButton("/com/aks/waybill/images/view-icon.png", "View Waybill");
            private final Button editButton = iconButton("/com/aks/waybill/images/edit-icon.png", "Edit Waybill");
            private final Button copyButton = new Button("Copy");
            private final MenuButton documentButton = new MenuButton();
            private final HBox box = new HBox(5, viewButton, editButton, copyButton, documentButton);

            {
                box.setAlignment(Pos.CENTER);
                viewButton.setPrefSize(36, 32);
                editButton.setPrefSize(36, 32);
                copyButton.setPrefHeight(32); copyButton.getStyleClass().add("secondary-button"); copyButton.setTooltip(new Tooltip("Duplicate Waybill"));
                documentButton.setPrefSize(36, 32);
                ImageView downloadIcon = new ImageView(new Image(
                        SavedWaybillsView.class.getResourceAsStream("/com/aks/waybill/images/download-icon.png")));
                downloadIcon.setFitWidth(16);
                downloadIcon.setFitHeight(16);
                downloadIcon.setPreserveRatio(true);
                documentButton.setGraphic(downloadIcon);
                documentButton.setTooltip(new Tooltip("Generate PDF / Word"));
                documentButton.getStyleClass().add("icon-action-button");
                documentButton.setStyle("-fx-padding: 4px;");
                viewButton.getStyleClass().add("secondary-button");
                editButton.getStyleClass().add("secondary-button");
                documentButton.getStyleClass().add("secondary-button");
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }

                WaybillService.WaybillListRow row = getTableView().getItems().get(getIndex());
                long waybillId = row.id();
                viewButton.setOnAction(event -> openView(waybillId));
                editButton.setOnAction(event -> openEdit(waybillId));
                copyButton.setOnAction(event -> duplicateWaybill(waybillId));
                editButton.setDisable("FINAL".equalsIgnoreCase(row.status()) && !SessionContext.isAdmin());

                MenuItem pdf = new MenuItem("Generate PDF");
                MenuItem word = new MenuItem("Generate Word");
                pdf.setOnAction(event -> WaybillReportActions.generatePdf(
                        getScene() == null ? null : getScene().getWindow(), waybillId));
                word.setOnAction(event -> WaybillReportActions.generateWord(
                        getScene() == null ? null : getScene().getWindow(), waybillId));
                MenuItem lifecycle = new MenuItem("DRAFT".equalsIgnoreCase(row.status()) ? "Mark as Final" : "Return to Draft");
                lifecycle.setOnAction(event -> {
                    try {
                        if ("DRAFT".equalsIgnoreCase(row.status())) {
                            Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Mark this waybill as Final? Normal users will no longer be able to edit it.",ButtonType.OK,ButtonType.CANCEL);
                            if(a.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK){WaybillService.finalizeWaybill(waybillId);loadPage(currentPage);}
                        } else if(SessionContext.isAdmin()) {
                            TextInputDialog d=new TextInputDialog(); d.setTitle("Return to Draft"); d.setHeaderText("Return Finalized Waybill to Draft"); d.setContentText("Reason:");
                            d.showAndWait().ifPresent(reason->{if(reason!=null&&!reason.isBlank()){try{WaybillService.revertToDraft(waybillId,reason);loadPage(currentPage);}catch(Exception ex){showError(ex.getMessage());}}});
                        }
                    } catch(Exception ex){showError(ex.getMessage());}
                });
                MenuItem delete = new MenuItem("Delete Waybill");
                delete.setVisible(SessionContext.isAdmin());
                delete.setOnAction(event -> {
                    Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Delete waybill "+row.waybillNumber()+"? This action cannot be undone unless a backup is available.",ButtonType.OK,ButtonType.CANCEL);
                    a.setTitle("Delete Waybill");
                    if(a.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK){try{WaybillService.delete(waybillId);loadPage(currentPage);}catch(Exception ex){showError(ex.getMessage());}}
                });
                documentButton.getItems().setAll(pdf, word, new SeparatorMenuItem(), lifecycle, delete);

                setGraphic(box);
            }
        });

        table.getColumns().setAll(number, date, created, shipper, consignee, carrier, status, actions);
        table.setOnSort(event -> {
            if (table.getSortOrder().isEmpty()) return;
            TableColumn<?, ?> selected = table.getSortOrder().get(0);
            sortKey = selected == number ? "number" : selected == date ? "date" : selected == shipper ? "shipper"
                    : selected == consignee ? "consignee" : selected == carrier ? "carrier" : selected == status ? "status" : selected == created ? "created" : "date";
            sortAscending = selected.getSortType() == TableColumn.SortType.ASCENDING;
            event.consume();
            loadPage(0);
        });
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No saved waybills match the current search."));
        table.setFixedCellSize(42);
        fitTableHeight(table, 0, pageSize, 42);
        table.setRowFactory(view -> {
            TableRow<WaybillService.WaybillListRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> { if (event.getClickCount() == 2 && !row.isEmpty()) openView(row.getItem().id()); });
            return row;
        });

        HBox actionsBar = new HBox(10); actionsBar.setAlignment(Pos.CENTER_RIGHT);
        Button refresh = button("Refresh", "secondary-button"); refresh.setOnAction(event -> loadPage(currentPage));
        Button export = button("Export Excel", "secondary-button"); export.setOnAction(event -> exportExcel());
        actionsBar.getChildren().addAll(export, refresh);

        pagination.setPageLoader(this::loadPage);
        root.getChildren().addAll(filterCard, table, actionsBar, pagination);
        return root;
    }

    private void duplicateWaybill(long id) {
        Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,"Create a new waybill by copying this waybill's operational details? The new waybill will use today's date and will have blank signature/declaration fields.",ButtonType.OK,ButtonType.CANCEL);
        confirm.setHeaderText("Duplicate Waybill"); if(confirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
        try{WaybillService.SavedWaybill saved=WaybillService.duplicate(id);new Alert(Alert.AlertType.INFORMATION,"New waybill created successfully: "+saved.waybillNumber(),ButtonType.OK).showAndWait();loadPage(0);}catch(Exception ex){showError(ex.getMessage());}
    }

    private void exportExcel() {
        Window owner = getScene() == null ? null : getScene().getWindow();
        FileChooser chooser=new FileChooser(); chooser.setTitle("Export Saved Waybills to Excel"); chooser.setInitialFileName("AKS-Waybills-"+java.time.LocalDate.now()+".xlsx"); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook (*.xlsx)","*.xlsx"));
        Path initialDir=AppPaths.defaultSaveDirectory(); if(java.nio.file.Files.isDirectory(initialDir)) chooser.setInitialDirectory(initialDir.toFile());
        java.io.File file=chooser.showSaveDialog(owner); if(file==null)return;
        if (!file.getName().toLowerCase().endsWith(".xlsx")) file = new java.io.File(file.getAbsolutePath() + ".xlsx");

        final Path output = file.toPath();
        final String search = searchField.getText();
        final LocalDate from = fromDate.getValue();
        final LocalDate to = toDate.getValue();
        final String selectedStatus = "All".equalsIgnoreCase(statusFilter.getValue()) ? null : statusFilter.getValue();

        Stage progressDialog = createExcelProgressDialog(owner);
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                var rows = WaybillService.findAllForExcelForCurrentUser(search, from, to, selectedStatus);
                ExcelExportService.export(output, rows);
                return rows.size();
            }
        };

        task.setOnSucceeded(event -> {
            dismissExcelProgressDialog(progressDialog);
            new Alert(Alert.AlertType.INFORMATION,
                    "Excel export created successfully.\nRecords exported: " + task.getValue(),
                    ButtonType.OK).showAndWait();
        });

        task.setOnFailed(event -> {
            dismissExcelProgressDialog(progressDialog);
            Throwable failure = task.getException();
            showError(failure == null || failure.getMessage() == null
                    ? "Unable to export waybills to Excel."
                    : failure.getMessage());
        });

        progressDialog.show();
        Thread worker = new Thread(task, "wasp-excel-export");
        worker.setDaemon(true);
        worker.start();
    }

    private static Stage createExcelProgressDialog(Window owner) {
        DialogPane pane = new DialogPane();
        pane.getStyleClass().add("wasp-dialog-pane");
        pane.setHeaderText("Generating Excel report");

        ProgressIndicator progress = new ProgressIndicator(ProgressIndicator.INDETERMINATE_PROGRESS);
        progress.setPrefSize(48, 48);
        progress.setMinSize(48, 48);
        progress.setMaxSize(48, 48);

        Label message = new Label("Preparing the waybill data and creating the Excel file...\nPlease wait.");
        message.setWrapText(true);
        message.setMaxWidth(360);
        message.setAlignment(Pos.CENTER);

        VBox content = new VBox(14, progress, message);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(8, 12, 12, 12));
        content.setPrefWidth(430);
        pane.setContent(content);
        pane.getButtonTypes().clear();

        StackPane circle = new StackPane();
        circle.getStyleClass().addAll("wasp-dialog-icon", "wasp-dialog-info");
        circle.setMinSize(34, 34);
        circle.setPrefSize(34, 34);
        circle.setMaxSize(34, 34);
        circle.setTranslateY(8);
        Label glyph = new Label("i");
        glyph.getStyleClass().add("wasp-dialog-icon-glyph");
        glyph.setMinSize(34, 34);
        glyph.setPrefSize(34, 34);
        glyph.setMaxSize(34, 34);
        glyph.setAlignment(Pos.CENTER);
        circle.getChildren().add(glyph);
        pane.setGraphic(circle);

        Stage stage = new Stage();
        stage.setTitle("Generating Excel");
        stage.initModality(Modality.WINDOW_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setResizable(false);
        javafx.scene.Scene scene = new javafx.scene.Scene(pane);
        if (owner != null && owner.getScene() != null) scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> event.consume());
        stage.setOnShown(event -> {
            pane.applyCss();
            stage.sizeToScene();
            if (owner != null) {
                stage.setX(owner.getX() + Math.max(0, (owner.getWidth() - stage.getWidth()) / 2));
                stage.setY(owner.getY() + Math.max(0, (owner.getHeight() - stage.getHeight()) / 2));
            }
        });
        return stage;
    }

    private static void dismissExcelProgressDialog(Stage stage) {
        if (stage == null) return;
        stage.setOnCloseRequest(null);
        if (stage.isShowing()) stage.hide();
        stage.close();
    }

    private void openView(long id) { onView.accept(id); }

    private void openEdit(long id) { onEdit.accept(id); }

    private void loadPage(int page) {
        try {
            String selectedStatus = "All".equalsIgnoreCase(statusFilter.getValue()) ? null : statusFilter.getValue();
            WaybillService.WaybillPage result = WaybillService.findPageForCurrentUser(searchField.getText(), fromDate.getValue(), toDate.getValue(), selectedStatus, sortKey, sortAscending, Math.max(0, page), pageSize);
            currentPage = result.page(); totalPages = result.totalPages(); table.getItems().setAll(result.rows());
            fitTableHeight(table, result.rows().size(), pageSize, 42);
            pagination.setPageData(currentPage, totalPages, result.totalRows(), pageSize);
        } catch (RuntimeException exception) { showError(exception.getMessage()); }
    }

    private static VBox labeled(String title, Control control) { Label label = new Label(title); label.getStyleClass().add("field-label"); VBox box = new VBox(4, label, control); return box; }
    private static Button button(String text, String css) { Button button = new Button(text); button.getStyleClass().add(css); return button; }

    private static Button iconButton(String resource, String tooltipText) {
        Button button = new Button();
        InputStream stream = SavedWaybillsView.class.getResourceAsStream(resource);
        if (stream != null) {
            ImageView icon = new ImageView(new Image(stream));
            icon.setFitWidth(16);
            icon.setFitHeight(16);
            icon.setPreserveRatio(true);
            button.setGraphic(icon);
        }
        button.setTooltip(new Tooltip(tooltipText));
        button.getStyleClass().add("icon-action-button");
        return button;
    }
    private static TableColumn<WaybillService.WaybillListRow,String> column(String title, java.util.function.Function<WaybillService.WaybillListRow,String> value, double width) {
        TableColumn<WaybillService.WaybillListRow,String> column = new TableColumn<>(title); column.setPrefWidth(width); column.setCellValueFactory(data -> new SimpleStringProperty(value.apply(data.getValue()) == null ? "" : value.apply(data.getValue()))); return column;
    }

    private static TableColumn<WaybillService.WaybillListRow,String> columnWithTooltip(String title, java.util.function.Function<WaybillService.WaybillListRow,String> value, double width) {
        TableColumn<WaybillService.WaybillListRow,String> column = column(title, value, width);
        column.setCellFactory(col -> new TableCell<>() {
                        @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    setTooltip(new Tooltip(item));
                }
            }
        });
        return column;
    }
    private void showError(String message) { Alert alert = new Alert(Alert.AlertType.ERROR); alert.setTitle("Saved Waybills"); alert.setHeaderText(null); alert.setContentText(message == null ? "Unable to load saved waybills." : message); alert.showAndWait(); }
}
