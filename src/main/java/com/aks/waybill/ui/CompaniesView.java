package com.aks.waybill.ui;

import com.aks.waybill.service.CompanyService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;

/** Company master-data screen shared by shipper and consignee selection. */
public final class CompaniesView extends AppView {
    private static final int PAGE_SIZE = 20;

    private final TextField searchField = new TextField();
    { InputLimits.maxLength(searchField, 400); }
    private final TableView<CompanyService.CompanyRecord> table = new TableView<>();
    private final Label pageInfo = new Label();
    private final Label message = new Label();
    private int currentPage;
    private long totalRows;

    public CompaniesView() {
        super("Companies", "Manage the single company master used by both Shipper / Consignor and Consignee / Receiver.");
        build();
        loadPage(0);
    }

    private void build() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(0, 0, 20, 0));
        content.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(content, Priority.ALWAYS);

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        searchField.setPromptText("Search company, contact, phone or email");
        searchField.getStyleClass().add("settings-field");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.setOnAction(e -> loadPage(0));

        Button search = new Button("Search"); search.getStyleClass().add("secondary-button"); search.setOnAction(e -> loadPage(0));
        Button clear = new Button("Clear"); clear.getStyleClass().add("secondary-button"); clear.setOnAction(e -> { searchField.clear(); loadPage(0); });
        Button add = new Button("＋ Add Company"); add.getStyleClass().add("primary-button"); add.setOnAction(e -> openCompanyDialog(null));
        Button edit = new Button("Edit"); edit.getStyleClass().add("secondary-button"); edit.setOnAction(e -> editSelected());
        Button toggle = new Button("Activate / Deactivate"); toggle.getStyleClass().add("secondary-button"); toggle.setOnAction(e -> toggleSelected());
        toolbar.getChildren().addAll(searchField, search, clear, add, edit, toggle);

        table.setPlaceholder(new Label("No companies found."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(520);
        table.getColumns().setAll(
                column("Company Name", 0, 220),
                column("Contact Person", 1, 160),
                column("Phone", 2, 130),
                column("Email", 3, 190),
                column("Address", 4, 260),
                statusColumn()
        );
        table.setRowFactory(tv -> {
            TableRow<CompanyService.CompanyRecord> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) openCompanyDialog(row.getItem());
            });
            return row;
        });
        VBox.setVgrow(table, Priority.ALWAYS);

        HBox pager = new HBox(10);
        pager.setAlignment(Pos.CENTER_RIGHT);
        Button first = new Button("« First");
        Button previous = new Button("‹ Previous");
        Button next = new Button("Next ›");
        Button last = new Button("Last »");
        for (Button b : new Button[]{first, previous, next, last}) b.getStyleClass().add("secondary-button");
        first.setOnAction(e -> loadPage(0));
        previous.setOnAction(e -> loadPage(currentPage - 1));
        next.setOnAction(e -> loadPage(currentPage + 1));
        last.setOnAction(e -> {
            int pages = totalPages();
            loadPage(pages - 1);
        });
        pager.getChildren().addAll(pageInfo, first, previous, next, last);

        message.getStyleClass().add("form-message");
        content.getChildren().addAll(toolbar, table, message, pager);
        getChildren().add(content);
    }

    private TableColumn<CompanyService.CompanyRecord, String> column(String title, int index, double width) {
        TableColumn<CompanyService.CompanyRecord, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(switch (index) {
            case 0 -> safe(data.getValue().companyName());
            case 1 -> safe(data.getValue().contactPerson());
            case 2 -> safe(data.getValue().phoneNumber());
            case 3 -> safe(data.getValue().emailAddress());
            default -> safe(data.getValue().address());
        }));
        return column;
    }

    private TableColumn<CompanyService.CompanyRecord, String> statusColumn() {
        TableColumn<CompanyService.CompanyRecord, String> column = new TableColumn<>("Status");
        column.setPrefWidth(90);
        column.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().active() ? "Active" : "Inactive"));
        return column;
    }

    private void loadPage(int requestedPage) {
        try {
            CompanyService.CompanyPage page = CompanyService.findPage(searchField.getText(), requestedPage, PAGE_SIZE);
            currentPage = page.page();
            totalRows = page.totalRows();
            table.setItems(FXCollections.observableArrayList(page.rows()));
            int pages = totalPages();
            pageInfo.setText("Page " + (currentPage + 1) + " of " + pages + "   •   " + totalRows + " companies");
            clearMessage();
        } catch (RuntimeException ex) {
            showError(ex.getMessage() == null ? "Unable to load companies." : ex.getMessage());
        }
    }

    private int totalPages() { return (int) Math.max(1, (totalRows + PAGE_SIZE - 1) / PAGE_SIZE); }

    private void editSelected() {
        CompanyService.CompanyRecord selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { showError("Select a company first."); return; }
        openCompanyDialog(selected);
    }

    private void toggleSelected() {
        CompanyService.CompanyRecord selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { showError("Select a company first."); return; }
        try {
            CompanyService.setActive(selected.id(), !selected.active());
            loadPage(currentPage);
        } catch (RuntimeException ex) {
            showError(ex.getMessage() == null ? "Unable to update company status." : ex.getMessage());
        }
    }

    private void openCompanyDialog(CompanyService.CompanyRecord existing) {
        Dialog<CompanyService.CompanyRecord> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add Company" : "Edit Company");
        dialog.setHeaderText(existing == null ? "Add a company to the master data" : "Update company master data");
        dialog.initModality(Modality.APPLICATION_MODAL);

        TextField name = field("Company name *", existing == null ? "" : existing.companyName(), InputLimits.COMPANY_NAME);
        TextField contact = field("Contact person", existing == null ? "" : existing.contactPerson(), InputLimits.CONTACT_PERSON);
        TextArea address = new TextArea(safe(existing == null ? null : existing.address()));
        InputLimits.maxLength(address, InputLimits.ADDRESS);
        address.setPromptText("Address"); address.setPrefRowCount(3); address.setWrapText(true); address.getStyleClass().add("settings-field");
        TextField phone = field("Phone number", existing == null ? "" : existing.phoneNumber(), InputLimits.PHONE);
        TextField email = field("Email address", existing == null ? "" : existing.emailAddress(), InputLimits.EMAIL);
        CheckBox active = new CheckBox("Active company"); active.setSelected(existing == null || existing.active());

        VBox form = new VBox(10,
                labeled("Company Name", name), labeled("Contact Person", contact), labeled("Address", address),
                labeled("Phone Number", phone), labeled("Email Address", email), active);
        form.setPrefWidth(500);
        dialog.getDialogPane().setContent(form);
        ButtonType save = new ButtonType(existing == null ? "Create Company" : "Save Changes", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        dialog.setResultConverter(button -> {
            if (button != save) return null;
            try {
                if (existing == null) return CompanyService.create(name.getText(), contact.getText(), address.getText(), phone.getText(), email.getText());
                return CompanyService.update(existing.id(), name.getText(), contact.getText(), address.getText(), phone.getText(), email.getText(), active.isSelected());
            } catch (RuntimeException ex) {
                showError(ex.getMessage() == null ? "Unable to save company." : ex.getMessage());
                return null;
            }
        });
        dialog.showAndWait().ifPresent(saved -> loadPage(currentPage));
    }

    private void clearMessage() { message.setText(""); message.getStyleClass().remove("error-message"); }
    private void showError(String text) { message.getStyleClass().add("error-message"); message.setText(text); }
    private static TextField field(String prompt, String value, int maxLength) { TextField field = new TextField(value); InputLimits.maxLength(field, maxLength); field.setPromptText(prompt); field.getStyleClass().add("settings-field"); return field; }
    private static VBox labeled(String label, Control control) { VBox box = new VBox(5, new Label(label), control); control.setMaxWidth(Double.MAX_VALUE); return box; }
    private static String safe(String value) { return value == null ? "" : value; }
}
