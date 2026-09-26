package com.aks.waybill.ui;

import com.aks.waybill.service.CompanyService;
import com.aks.waybill.service.SettingsService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;

/** Separate Shipper and Consignee company masters. */
public final class CompaniesView extends AppView {
    private final TabPane tabs = new TabPane();

    public CompaniesView() {
        super("Companies", "Shippers / Consignors and Consignees / Receivers are maintained as separate company masters.");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("wasp-company-tabs");
        Tab shippers = new Tab("Shippers / Consignors"); shippers.setContent(new CompanyMasterTab(CompanyService.CompanyType.SHIPPER));
        Tab consignees = new Tab("Consignees / Receivers"); consignees.setContent(new CompanyMasterTab(CompanyService.CompanyType.CONSIGNEE));
        tabs.getTabs().addAll(shippers, consignees);
        VBox.setVgrow(tabs, Priority.ALWAYS); getChildren().add(tabs);
    }

    private static final class CompanyMasterTab extends VBox {
        private final CompanyService.CompanyType type;
        private final int pageSize = SettingsService.getPageSize();
        private final TextField searchField = new TextField();
        private final TableView<CompanyService.CompanyRecord> table = new TableView<>();
        private final Label pageInfo = new Label();
        private final Label message = new Label();
        private int currentPage;
        private long totalRows;

        CompanyMasterTab(CompanyService.CompanyType type) {
            this.type = type; setSpacing(16); setPadding(new Insets(0, 0, 20, 0)); build(); loadPage(0);
        }

        private void build() {
            HBox toolbar = new HBox(10); toolbar.setAlignment(Pos.CENTER_LEFT);
            searchField.setPromptText("Search company, contact, phone or email"); InputLimits.maxLength(searchField, 400); searchField.getStyleClass().add("settings-field"); HBox.setHgrow(searchField, Priority.ALWAYS); searchField.setOnAction(e -> loadPage(0));
            Button search = button("Search", "secondary-button"); search.setOnAction(e -> loadPage(0));
            Button clear = button("Clear", "secondary-button"); clear.setOnAction(e -> { searchField.clear(); loadPage(0); });
            Button add = button("＋ Add Company", "primary-button"); add.setOnAction(e -> openCompanyDialog(null));
            Button edit = button("Edit", "secondary-button"); edit.setOnAction(e -> editSelected());
            Button toggle = button("Activate / Deactivate", "secondary-button"); toggle.setOnAction(e -> toggleSelected());
            Button delete = button("Delete", "secondary-button"); delete.setVisible(com.aks.waybill.security.SessionContext.isAdmin()); delete.setManaged(com.aks.waybill.security.SessionContext.isAdmin()); delete.setOnAction(e -> deleteSelected());
            toolbar.getChildren().addAll(searchField, search, clear, add, edit, toggle, delete);

            table.setPlaceholder(new Label("No companies found.")); table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN); fitTableHeight(table, 0, pageSize, 42);
            table.getColumns().setAll(column("Company Name", 0, 220), column("Contact Person", 1, 160), column("Phone", 2, 130), column("Email", 3, 190), column("Address", 4, 260), statusColumn());
            table.setRowFactory(tv -> { TableRow<CompanyService.CompanyRecord> row = new TableRow<>(); row.setOnMouseClicked(event -> { if (event.getClickCount() == 2 && !row.isEmpty()) openCompanyDialog(row.getItem()); }); return row; });
            HBox pager = new HBox(10); pager.setAlignment(Pos.CENTER_RIGHT);
            Button first = button("« First", "secondary-button"), previous = button("‹ Previous", "secondary-button"), next = button("Next ›", "secondary-button"), last = button("Last »", "secondary-button");
            first.setOnAction(e -> loadPage(0)); previous.setOnAction(e -> loadPage(currentPage - 1)); next.setOnAction(e -> loadPage(currentPage + 1)); last.setOnAction(e -> loadPage(totalPages() - 1)); pager.getChildren().addAll(pageInfo, first, previous, next, last);
            message.getStyleClass().add("form-message"); getChildren().addAll(toolbar, table, message, pager);
        }

        private TableColumn<CompanyService.CompanyRecord, String> column(String title, int index, double width) {
            TableColumn<CompanyService.CompanyRecord, String> column = new TableColumn<>(title); column.setPrefWidth(width);
            column.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(switch (index) { case 0 -> safe(data.getValue().companyName()); case 1 -> safe(data.getValue().contactPerson()); case 2 -> safe(data.getValue().phoneNumber()); case 3 -> safe(data.getValue().emailAddress()); default -> safe(data.getValue().address()); })); return column;
        }
        private TableColumn<CompanyService.CompanyRecord, String> statusColumn() { TableColumn<CompanyService.CompanyRecord, String> c = new TableColumn<>("Status"); c.setPrefWidth(90); c.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().active() ? "Active" : "Inactive")); return c; }

        private void loadPage(int requestedPage) {
            try { CompanyService.CompanyPage page = CompanyService.findPage(type, searchField.getText(), requestedPage, pageSize); currentPage = page.page(); totalRows = page.totalRows(); table.setItems(FXCollections.observableArrayList(page.rows())); fitTableHeight(table, page.rows().size(), pageSize, 42); pageInfo.setText("Page " + (currentPage + 1) + " of " + totalPages() + "   •   " + totalRows + " companies"); clearMessage(); }
            catch (RuntimeException ex) { showError(ex.getMessage() == null ? "Unable to load companies." : ex.getMessage()); }
        }
        private int totalPages() { return (int) Math.max(1, (totalRows + pageSize - 1) / pageSize); }
        private void editSelected() { CompanyService.CompanyRecord selected = table.getSelectionModel().getSelectedItem(); if (selected == null) { showError("Select a company first."); return; } openCompanyDialog(selected); }
        private void toggleSelected() { CompanyService.CompanyRecord selected = table.getSelectionModel().getSelectedItem(); if (selected == null) { showError("Select a company first."); return; } try { CompanyService.setActive(type, selected.id(), !selected.active()); loadPage(currentPage); } catch (RuntimeException ex) { showError(ex.getMessage()); } }
        private void deleteSelected() {
            CompanyService.CompanyRecord selected=table.getSelectionModel().getSelectedItem();
            if(selected==null){showError("Select a company first.");return;}
            Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Delete company \""+selected.companyName()+"\"? Historical waybills store their own company details, so deleting the master will not change existing waybills.",ButtonType.OK,ButtonType.CANCEL);
            a.setTitle("Delete Company");
            if(a.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
            try{showInfo(CompanyService.deleteOrDeactivate(type,selected.id()));loadPage(currentPage);}catch(Exception ex){showError(ex.getMessage());}
        }

        private void openCompanyDialog(CompanyService.CompanyRecord existing) {
            Dialog<CompanyService.CompanyRecord> dialog = new Dialog<>(); dialog.setTitle(existing == null ? "Add Company" : "Edit Company"); dialog.setHeaderText(existing == null ? "Add a company to the " + type.displayName() + " master" : "Update " + type.displayName() + " master data"); dialog.initModality(Modality.APPLICATION_MODAL);
            TextField name = field("Company name *", existing == null ? "" : existing.companyName(), InputLimits.COMPANY_NAME);
            TextField contact = field("Contact person", existing == null ? "" : existing.contactPerson(), InputLimits.CONTACT_PERSON);
            TextArea address = new TextArea(safe(existing == null ? null : existing.address())); address.setPromptText("Address"); address.setPrefRowCount(3); address.setWrapText(true); address.getStyleClass().add("settings-field");
            TextField phone = field("Phone number", existing == null ? "" : existing.phoneNumber(), InputLimits.PHONE); TextField email = field("Email address", existing == null ? "" : existing.emailAddress(), InputLimits.EMAIL);
            CheckBox active = new CheckBox("Active company"); active.setSelected(existing == null || existing.active());
            VBox form = new VBox(10, labeled("Company Name", name), labeled("Contact Person", contact), labeled("Address", address), labeled("Phone Number", phone), labeled("Email Address", email), active); form.setPrefWidth(500); dialog.getDialogPane().setContent(form);
            ButtonType save = new ButtonType(existing == null ? "Create Company" : "Save Changes", ButtonBar.ButtonData.OK_DONE); dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
            dialog.setResultConverter(button -> { if (button != save) return null; try { return existing == null ? CompanyService.create(type, name.getText(), contact.getText(), address.getText(), phone.getText(), email.getText(), active.isSelected()) : CompanyService.update(type, existing.id(), name.getText(), contact.getText(), address.getText(), phone.getText(), email.getText(), active.isSelected()); } catch (RuntimeException ex) { showError(ex.getMessage()); return null; } });
            dialog.showAndWait().ifPresent(saved -> loadPage(currentPage));
        }
        private void clearMessage() { message.setText(""); message.getStyleClass().remove("error-message"); }
        private void showError(String text) { message.getStyleClass().add("error-message"); message.setText(text == null ? "Unable to complete operation." : text); }
        private void showInfo(String text) { message.getStyleClass().remove("error-message"); message.setText(text == null ? "Operation completed." : text); }
        private static Button button(String text, String css) { Button b = new Button(text); b.getStyleClass().add(css); return b; }
        private static TextField field(String prompt, String value, int max) { TextField f = new TextField(value); f.setPromptText(prompt); InputLimits.maxLength(f, max); f.getStyleClass().add("settings-field"); return f; }
        private static VBox labeled(String label, Control control) { VBox box = new VBox(5, new Label(label), control); control.setMaxWidth(Double.MAX_VALUE); return box; }
        private static String safe(String value) { return value == null ? "" : value; }
    }
}
