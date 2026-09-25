package com.aks.waybill.ui;

import com.aks.waybill.service.WaybillService;
import com.aks.waybill.service.SettingsService;
import com.aks.waybill.service.ExcelExportService;
import com.aks.waybill.security.SessionContext;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Searchable, paginated saved-waybill register. */
public final class SavedWaybillsView extends AppView {
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private int pageSize = SettingsService.getPageSize();

    private final Runnable onBack;
    private final java.util.function.LongConsumer onView;
    private final java.util.function.LongConsumer onEdit;
    private final TextField searchField = new TextField();
    { InputLimits.maxLength(searchField, 400); }
    private final DatePicker fromDate = new DatePicker();
    private final DatePicker toDate = new DatePicker();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final TableView<WaybillService.WaybillListRow> table = new TableView<>();
    private final Label pageInfo = new Label();
    private final Label resultInfo = new Label();
    private final Button previousButton = new Button("‹");
    private final Button nextButton = new Button("›");
    private final HBox pageButtons = new HBox(5);
    private int currentPage = 0;
    private int totalPages = 1;

    public SavedWaybillsView() { this(() -> {}, id -> {}, id -> {}); }

    public SavedWaybillsView(Runnable onBack, java.util.function.LongConsumer onView, java.util.function.LongConsumer onEdit) {
        super("Saved Waybills", "Search, review and manage previously created transportation waybills.");
        this.onBack = onBack == null ? () -> {} : onBack;
        this.onView = onView == null ? id -> {} : onView;
        this.onEdit = onEdit == null ? id -> {} : onEdit;
        getChildren().add(buildContent());
        loadPage(0);
    }

    private VBox buildContent() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(4, 0, 30, 0));

        VBox filterCard = new VBox(12);
        filterCard.getStyleClass().add("settings-card");
        filterCard.setPadding(new Insets(18));
        Label filterTitle = new Label("Search & Filters"); filterTitle.getStyleClass().add("section-heading");
        searchField.setPromptText("Waybill no., shipper, consignee or carrier");
        searchField.setOnAction(event -> loadPage(0));
        fromDate.setPromptText("From date"); fromDate.setPrefWidth(150);
        toDate.setPromptText("To date"); toDate.setPrefWidth(150);
        statusFilter.getItems().setAll("All", "DRAFT", "FINAL");
        statusFilter.setValue("All");
        statusFilter.setPrefWidth(130);
        Button search = button("Search", "primary-button"); search.setOnAction(event -> loadPage(0));
        Button clear = button("Clear", "secondary-button"); clear.setOnAction(event -> { searchField.clear(); fromDate.setValue(null); toDate.setValue(null); statusFilter.setValue("All"); loadPage(0); });
        VBox searchBox = labeled("Search", searchField); VBox fromBox = labeled("From", fromDate); VBox toBox = labeled("To", toDate); VBox statusBox = labeled("Status", statusFilter);
        HBox searchRow = new HBox(12, searchBox, fromBox, toBox, statusBox, search, clear); searchRow.setAlignment(Pos.BOTTOM_LEFT); HBox.setHgrow(searchBox, Priority.ALWAYS); searchField.setMaxWidth(Double.MAX_VALUE);
        filterCard.getChildren().addAll(filterTitle, searchRow);

        TableColumn<WaybillService.WaybillListRow,String> number = column("Waybill No.", r -> r.waybillNumber(), 200);
        TableColumn<WaybillService.WaybillListRow,String> date = column("Date", r -> DISPLAY_DATE.format(r.waybillDate()), 100);
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

        table.getColumns().setAll(number, date, shipper, consignee, carrier, status, actions);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No saved waybills match the current search."));
        table.setFixedCellSize(42);
        table.setPrefHeight(600);
        table.setRowFactory(view -> {
            TableRow<WaybillService.WaybillListRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> { if (event.getClickCount() == 2 && !row.isEmpty()) openView(row.getItem().id()); });
            return row;
        });

        HBox actionsBar = new HBox(10); actionsBar.setAlignment(Pos.CENTER_RIGHT);
        Button refresh = button("Refresh", "secondary-button"); refresh.setOnAction(event -> loadPage(currentPage));
        Button export = button("Export Excel", "secondary-button"); export.setOnAction(event -> exportExcel());
        actionsBar.getChildren().addAll(export, refresh);

        HBox paging = new HBox(12); paging.setAlignment(Pos.CENTER);
        previousButton.getStyleClass().add("secondary-button"); nextButton.getStyleClass().add("secondary-button");
        previousButton.setOnAction(event -> loadPage(currentPage - 1)); nextButton.setOnAction(event -> loadPage(currentPage + 1));
        pageButtons.setAlignment(Pos.CENTER);
        paging.getChildren().addAll(previousButton, pageButtons, nextButton);

        HBox info = new HBox(15); info.setAlignment(Pos.CENTER_LEFT);
        resultInfo.getStyleClass().add("card-description"); pageInfo.getStyleClass().add("card-description");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS); info.getChildren().addAll(resultInfo, spacer, pageInfo);

        root.getChildren().addAll(filterCard, table, actionsBar, info, paging);
        VBox.setVgrow(table, Priority.ALWAYS);
        return root;
    }

    private void duplicateWaybill(long id) {
        Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,"Create a new waybill by copying this waybill's operational details? The new waybill will use today's date and will have blank signature/declaration fields.",ButtonType.OK,ButtonType.CANCEL);
        confirm.setHeaderText("Duplicate Waybill"); if(confirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
        try{WaybillService.SavedWaybill saved=WaybillService.duplicate(id);new Alert(Alert.AlertType.INFORMATION,"New waybill created successfully: "+saved.waybillNumber(),ButtonType.OK).showAndWait();loadPage(0);}catch(Exception ex){showError(ex.getMessage());}
    }

    private void exportExcel() {
        FileChooser chooser=new FileChooser(); chooser.setTitle("Export Saved Waybills to Excel"); chooser.setInitialFileName("AKS-Waybills-"+java.time.LocalDate.now()+".xlsx"); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook (*.xlsx)","*.xlsx"));
        java.io.File file=chooser.showSaveDialog(getScene()==null?null:getScene().getWindow()); if(file==null)return;
        try{String selectedStatus = "All".equalsIgnoreCase(statusFilter.getValue()) ? null : statusFilter.getValue();
        var rows=WaybillService.findAllForExcelForCurrentUser(searchField.getText(),fromDate.getValue(),toDate.getValue(),selectedStatus);ExcelExportService.export(file.toPath(),rows);new Alert(Alert.AlertType.INFORMATION,"Excel export created successfully.\nRecords exported: "+rows.size(),ButtonType.OK).showAndWait();}catch(Exception ex){showError(ex.getMessage());}
    }

    private void openView(long id) { onView.accept(id); }

    private void openEdit(long id) { onEdit.accept(id); }

    private void loadPage(int page) {
        try {
            String selectedStatus = "All".equalsIgnoreCase(statusFilter.getValue()) ? null : statusFilter.getValue();
            WaybillService.WaybillPage result = WaybillService.findPageForCurrentUser(searchField.getText(), fromDate.getValue(), toDate.getValue(), selectedStatus, Math.max(0, page), pageSize);
            currentPage = result.page(); totalPages = result.totalPages(); table.getItems().setAll(result.rows());
            long start = result.totalRows() == 0 ? 0 : (long) currentPage * pageSize + 1;
            long end = Math.min(result.totalRows(), (long) (currentPage + 1) * pageSize);
            resultInfo.setText("Showing " + start + "–" + end + " of " + result.totalRows());
            pageInfo.setText("Page " + (currentPage + 1) + " of " + totalPages);
            previousButton.setDisable(currentPage <= 0); nextButton.setDisable(currentPage >= totalPages - 1); buildPageButtons();
        } catch (RuntimeException exception) { showError(exception.getMessage()); }
    }

    private void buildPageButtons() {
        pageButtons.getChildren().clear();
        int start = Math.max(0, currentPage - 2); int end = Math.min(totalPages - 1, start + 4); start = Math.max(0, end - 4);
        for (int i = start; i <= end; i++) {
            final int pageIndex = i;
            Button pageButton = new Button(String.valueOf(i + 1));
            pageButton.getStyleClass().add(i == currentPage ? "nav-selected" : "secondary-button");
            pageButton.setOnAction(event -> loadPage(pageIndex)); pageButtons.getChildren().add(pageButton);
        }
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
