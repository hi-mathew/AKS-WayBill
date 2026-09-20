package com.aks.waybill.ui;

import com.aks.waybill.service.WaybillService;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Searchable, paginated saved-waybill register. */
public final class SavedWaybillsView extends AppView {
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final int PAGE_SIZE = 20;

    private final Runnable onBack;
    private final java.util.function.LongConsumer onView;
    private final java.util.function.LongConsumer onEdit;
    private final TextField searchField = new TextField();
    { InputLimits.maxLength(searchField, 400); }
    private final DatePicker fromDate = new DatePicker();
    private final DatePicker toDate = new DatePicker();
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
        Button search = button("Search", "primary-button"); search.setOnAction(event -> loadPage(0));
        Button clear = button("Clear", "secondary-button"); clear.setOnAction(event -> { searchField.clear(); fromDate.setValue(null); toDate.setValue(null); loadPage(0); });
        VBox searchBox = labeled("Search", searchField); VBox fromBox = labeled("From", fromDate); VBox toBox = labeled("To", toDate);
        HBox searchRow = new HBox(12, searchBox, fromBox, toBox, search, clear); searchRow.setAlignment(Pos.BOTTOM_LEFT); HBox.setHgrow(searchBox, Priority.ALWAYS); searchField.setMaxWidth(Double.MAX_VALUE);
        filterCard.getChildren().addAll(filterTitle, searchRow);

        TableColumn<WaybillService.WaybillListRow,String> number = column("Waybill No.", r -> r.waybillNumber(), 200);
        TableColumn<WaybillService.WaybillListRow,String> date = column("Date", r -> DISPLAY_DATE.format(r.waybillDate()), 100);
        TableColumn<WaybillService.WaybillListRow,String> shipper = column("Shipper / Consignor", r -> r.shipperName(), 170);
        TableColumn<WaybillService.WaybillListRow,String> consignee = column("Consignee / Receiver", r -> r.consigneeName(), 170);
        TableColumn<WaybillService.WaybillListRow,String> carrier = column("Carrier", r -> r.carrierName(), 150);

        TableColumn<WaybillService.WaybillListRow, Void> actions = new TableColumn<>("Actions");
        actions.setPrefWidth(150);
        actions.setMinWidth(150);
        actions.setMaxWidth(150);
        actions.setCellFactory(column -> new TableCell<>() {
            private final Button viewButton = iconButton("/com/aks/waybill/images/view-icon.png", "View Waybill");
            private final Button editButton = iconButton("/com/aks/waybill/images/edit-icon.png", "Edit Waybill");
            private final MenuButton documentButton = new MenuButton();
            private final HBox box = new HBox(5, viewButton, editButton, documentButton);

            {
                box.setAlignment(Pos.CENTER);
                viewButton.setPrefSize(36, 32);
                editButton.setPrefSize(36, 32);
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

                MenuItem pdf = new MenuItem("Generate PDF");
                MenuItem word = new MenuItem("Generate Word");
                pdf.setOnAction(event -> WaybillReportActions.generatePdf(
                        getScene() == null ? null : getScene().getWindow(), waybillId));
                word.setOnAction(event -> WaybillReportActions.generateWord(
                        getScene() == null ? null : getScene().getWindow(), waybillId));
                documentButton.getItems().setAll(pdf, word);

                setGraphic(box);
            }
        });

        table.getColumns().setAll(number, date, shipper, consignee, carrier, actions);
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
        actionsBar.getChildren().add(refresh);

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

    private void openView(long id) { onView.accept(id); }

    private void openEdit(long id) { onEdit.accept(id); }

    private void loadPage(int page) {
        try {
            WaybillService.WaybillPage result = WaybillService.findPageForCurrentUser(searchField.getText(), fromDate.getValue(), toDate.getValue(), Math.max(0, page), PAGE_SIZE);
            currentPage = result.page(); totalPages = result.totalPages(); table.getItems().setAll(result.rows());
            long start = result.totalRows() == 0 ? 0 : (long) currentPage * PAGE_SIZE + 1;
            long end = Math.min(result.totalRows(), (long) (currentPage + 1) * PAGE_SIZE);
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
    private void showError(String message) { Alert alert = new Alert(Alert.AlertType.ERROR); alert.setTitle("Saved Waybills"); alert.setHeaderText(null); alert.setContentText(message == null ? "Unable to load saved waybills." : message); alert.showAndWait(); }
}
