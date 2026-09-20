package com.aks.waybill.ui;

import com.aks.waybill.service.CompanyService;
import com.aks.waybill.service.WaybillNumberService;
import com.aks.waybill.service.WaybillService;
import com.aks.waybill.security.SessionContext;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Reusable New/Edit/View waybill form. */
public class WaybillFormView extends AppView {

    public enum Mode { NEW, EDIT, VIEW }

    private final Mode mode;
    private final long waybillId;
    private final Runnable onSaved;
    private final Runnable onCancel;
    private final Runnable onCreateNew;
    private final Consumer<Long> onViewSaved;

    private final Label numberLabel = new Label();
    private final DatePicker waybillDate = new DatePicker(LocalDate.now());
    private final DatePicker estimatedDelivery = new DatePicker();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final CompanySelector shipperSelector = new CompanySelector(this::populateShipper, this::refreshCompanySelectors);
    private final TextField shipperContact = field("Contact person", InputLimits.CONTACT_PERSON);
    private final TextArea shipperAddress = area("Address", 2);
    private final TextField shipperPhone = field("Phone number", InputLimits.PHONE);
    private final TextField shipperEmail = field("Email address", InputLimits.EMAIL);

    private final CompanySelector consigneeSelector = new CompanySelector(this::populateConsignee, this::refreshCompanySelectors);
    private final TextField consigneeContact = field("Contact person", InputLimits.CONTACT_PERSON);
    private final TextArea consigneeAddress = area("Address", 2);
    private final TextField consigneePhone = field("Phone number", InputLimits.PHONE);
    private final TextField consigneeEmail = field("Email address", InputLimits.EMAIL);

    private final TextField carrier = field("Carrier name", InputLimits.CARRIER);
    private final TextField driver = field("Driver name", InputLimits.DRIVER);
    private final TextField vehicle = field("Vehicle / Trailer No.", InputLimits.VEHICLE);
    private final TextField origin = field("Origin / Loading point", InputLimits.LOCATION);
    private final TextField destination = field("Destination / Unloading point", InputLimits.LOCATION);

    private final TextArea specialInstructions = area("Special instructions / handling", 3);
    private final CheckBox hazardous = new CheckBox("Yes — hazardous materials");
    private final TextArea remarks = area("Remarks", 3);

    private final TableView<ItemRow> itemsTable = new TableView<>();
    private final Label message = new Label();

    private final Button saveButton = new Button("Save Changes");
    private final Button cancelButton = new Button("Cancel");
    private final Button addItemButton = new Button("＋ Add Item");
    private final Button removeItemButton = new Button("Remove Selected");

    public WaybillFormView(Mode mode, long waybillId, Runnable onSaved, Runnable onCancel, Runnable onCreateNew) {
        this(mode, waybillId, onSaved, onCancel, onCreateNew, id -> {});
    }

    public WaybillFormView(Mode mode, long waybillId, Runnable onSaved, Runnable onCancel,
                           Runnable onCreateNew, Consumer<Long> onViewSaved) {
        super(titleFor(mode), subtitleFor(mode));
        this.mode = mode;
        this.waybillId = waybillId;
        this.onSaved = onSaved == null ? () -> {} : onSaved;
        this.onCancel = onCancel == null ? () -> {} : onCancel;
        this.onCreateNew = onCreateNew == null ? () -> {} : onCreateNew;
        this.onViewSaved = onViewSaved == null ? id -> {} : onViewSaved;

        numberLabel.getStyleClass().add("waybill-number");
        configureDatePicker(estimatedDelivery);
        build();
        applyMode();

        if (mode == Mode.NEW) {
            refreshPreview();
            waybillDate.valueProperty().addListener((obs, oldValue, newValue) -> refreshPreview());
        } else {
            loadExisting();
        }
    }

    private static String titleFor(Mode mode) {
        return switch (mode) {
            case NEW -> "New Waybill";
            case EDIT -> "Edit Waybill";
            case VIEW -> "View Waybill";
        };
    }

    private static String subtitleFor(Mode mode) {
        return switch (mode) {
            case NEW -> "Create a transportation waybill in the same logical order as the official waybill document.";
            case EDIT -> "Update the transportation waybill using the same layout as the New Waybill screen.";
            case VIEW -> "Read-only view of the transportation waybill using the same layout as the entry screen.";
        };
    }

    private void build() {
        VBox page = new VBox(18);
        page.setPadding(new Insets(4, 0, 30, 0));
        page.getChildren().addAll(
                headerCard(),
                partiesCard(),
                carrierTransitCard(),
                itemsCard(),
                handlingCard(),
                buttons()
        );
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("content-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);
    }

    private VBox headerCard() {
        VBox card = card();
        HBox header = new HBox(20);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox number = new VBox(5, label("WAYBILL NO.", "field-label"), numberLabel);
        VBox date = new VBox(6, label("Date", "field-label"), waybillDate);
        date.setPrefWidth(220);
        waybillDate.setMaxWidth(Double.MAX_VALUE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(number, spacer, date);
        card.getChildren().add(header);
        return card;
    }

    private VBox partiesCard() {
        VBox outer = card();
        outer.getChildren().add(heading("1. SHIPPER / CONSIGNOR (ORIGIN) & 2. CONSIGNEE / RECEIVER (DESTINATION)"));

        GridPane grid = new GridPane();
        grid.setHgap(22);
        grid.setVgap(0);
        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(50);
        left.setHgrow(Priority.ALWAYS);
        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(50);
        right.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(left, right);

        grid.add(partyPanel("1. SHIPPER / CONSIGNOR (ORIGIN)", shipperSelector, shipperContact, shipperAddress, shipperPhone, shipperEmail), 0, 0);
        grid.add(partyPanel("2. CONSIGNEE / RECEIVER (DESTINATION)", consigneeSelector, consigneeContact, consigneeAddress, consigneePhone, consigneeEmail), 1, 0);
        outer.getChildren().add(grid);
        return outer;
    }

    private VBox partyPanel(String title, CompanySelector selector, TextField contact, TextArea address, TextField phone, TextField email) {
        VBox panel = new VBox(10);
        panel.getStyleClass().add("party-panel");
        panel.getChildren().add(heading(title));
        panel.getChildren().addAll(
                labeledControl("Company Name", selector),
                labeledControl("Contact Person", contact),
                labeledControl("Address", address),
                labeledControl("Phone Number", phone),
                labeledControl("Email Address", email)
        );
        return panel;
    }

    private VBox carrierTransitCard() {
        VBox card = card();
        card.getChildren().add(heading("3. CARRIER & TRANSIT DETAILS"));
        GridPane grid = grid2();
        addField(grid, 0, 0, "Carrier Name", carrier);
        addField(grid, 1, 0, "Origin / Loading Point", origin);
        addField(grid, 0, 1, "Driver Name", driver);
        addField(grid, 1, 1, "Destination / Unloading Point", destination);
        addField(grid, 0, 2, "Vehicle / Trailer No.", vehicle);
        addField(grid, 1, 2, "Estimated Delivery Date", estimatedDelivery);
        card.getChildren().add(grid);
        return card;
    }

    private VBox itemsCard() {
        VBox card = card();
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = heading("ITEM DESCRIPTION OF GOODS");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        addItemButton.getStyleClass().add("secondary-button");
        removeItemButton.getStyleClass().add("secondary-button");
        addItemButton.setOnAction(event -> {
            itemsTable.getItems().add(new ItemRow());
            itemsTable.getSelectionModel().selectLast();
        });
        removeItemButton.setOnAction(event -> {
            int index = itemsTable.getSelectionModel().getSelectedIndex();
            if (index >= 0) {
                itemsTable.getItems().remove(index);
                itemsTable.refresh();
                updateItemsTableHeight();
            }
        });

        header.getChildren().addAll(title, spacer, addItemButton, removeItemButton);

        TableColumn<ItemRow, String> no = new TableColumn<>("#");
        no.setPrefWidth(50);
        no.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(itemsTable.getItems().indexOf(data.getValue()) + 1)));
        no.setEditable(false);

        itemsTable.getColumns().setAll(
                no,
                editableColumn("Description of Goods", 0, 250, false, InputLimits.ITEM_DESCRIPTION),
                editableColumn("Package Type", 1, 150, false, InputLimits.PACKAGE_TYPE),
                editableColumn("Qty", 2, 90, true, 12),
                editableColumn("Weight (kg)", 3, 110, true, 15),
                editableColumn("Volume (m³)", 4, 110, true, 15)
        );
        itemsTable.setItems(FXCollections.observableArrayList());
        itemsTable.setEditable(mode != Mode.VIEW);
        itemsTable.setMinHeight(72);
        itemsTable.setMaxHeight(300);
        itemsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        itemsTable.setPlaceholder(new Label("No items. Click Add Item to add cargo details."));
        itemsTable.getItems().addListener((javafx.collections.ListChangeListener<ItemRow>) change -> updateItemsTableHeight());
        updateItemsTableHeight();
        card.getChildren().addAll(header, itemsTable);
        return card;
    }

    private VBox handlingCard() {
        VBox card = card();
        card.getChildren().add(heading("SPECIAL INSTRUCTIONS / HANDLING"));
        specialInstructions.setMaxWidth(Double.MAX_VALUE);
        card.getChildren().add(specialInstructions);

        HBox hazardousRow = new HBox(12);
        hazardousRow.setAlignment(Pos.CENTER_LEFT);
        hazardousRow.getChildren().addAll(label("Hazardous Materials", "field-label"), hazardous);
        card.getChildren().add(hazardousRow);
        card.getChildren().add(label("*(Subject to the applicable hazardous and restricted goods terms.)", "card-description"));
        card.getChildren().add(label("Remarks", "field-label"));
        card.getChildren().add(remarks);
        return card;
    }

    private HBox buttons() {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_RIGHT);
        message.getStyleClass().add("form-message");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        if (mode == Mode.NEW) {
            Button clear = new Button("Clear");
            clear.getStyleClass().add("secondary-button");
            clear.setOnAction(event -> clearForm());

            Button saveAndView = new Button("Save & View Saved Waybills");
            saveAndView.getStyleClass().add("secondary-button");
            saveAndView.setOnAction(event -> saveNew(true));

            Button saveAndCreate = new Button("Save & Create New");
            saveAndCreate.getStyleClass().add("primary-button");
            saveAndCreate.setOnAction(event -> saveNew(false));

            box.getChildren().addAll(message, spacer, clear, saveAndView, saveAndCreate);
        } else if (mode == Mode.EDIT) {
            cancelButton.getStyleClass().add("secondary-button");
            cancelButton.setOnAction(event -> onCancel.run());
            saveButton.getStyleClass().add("primary-button");
            saveButton.setOnAction(event -> updateExisting());
            box.getChildren().addAll(message, spacer, cancelButton, saveButton);
        } else {
            Button back = new Button("Back to Saved Waybills");
            back.getStyleClass().add("secondary-button");
            back.setOnAction(event -> onCancel.run());
            Button pdf = new Button("Generate PDF");
            pdf.getStyleClass().add("secondary-button");
            pdf.setOnAction(event -> WaybillReportActions.generatePdf(getScene() == null ? null : getScene().getWindow(), waybillId));
            Button word = new Button("Generate Word");
            word.getStyleClass().add("secondary-button");
            word.setOnAction(event -> WaybillReportActions.generateWord(getScene() == null ? null : getScene().getWindow(), waybillId));
            box.getChildren().addAll(message, spacer, pdf, word, back);
        }
        return box;
    }

    private void applyMode() {
        boolean editable = mode != Mode.VIEW;
        setEditable(waybillDate, editable);
        setEditable(estimatedDelivery, editable);

        Control[] controls = {
                carrier, driver, vehicle, origin, destination,
                specialInstructions, remarks
        };
        for (Control control : controls) control.setDisable(!editable);
        hazardous.setDisable(!editable);
        addItemButton.setDisable(!editable);
        removeItemButton.setDisable(!editable);
        shipperSelector.setReadOnly(!editable);
        consigneeSelector.setReadOnly(!editable);
        updatePartyDetailsState();

        if (mode == Mode.VIEW) itemsTable.setEditable(false);
    }

    private void updatePartyDetailsState() {
        boolean formEditable = mode != Mode.VIEW;
        setPartyDetailsEditable(shipperSelector.getSelectedCompany() == null && formEditable,
                shipperContact, shipperAddress, shipperPhone, shipperEmail);
        setPartyDetailsEditable(consigneeSelector.getSelectedCompany() == null && formEditable,
                consigneeContact, consigneeAddress, consigneePhone, consigneeEmail);
    }

    private static void setPartyDetailsEditable(boolean editable, TextField contact, TextArea address, TextField phone, TextField email) {
        contact.setDisable(!editable);
        address.setDisable(!editable);
        phone.setDisable(!editable);
        email.setDisable(!editable);
    }

    private static void setEditable(DatePicker picker, boolean editable) {
        picker.setDisable(!editable);
    }

    private static void configureDatePicker(DatePicker picker) {
        picker.setEditable(true);
        picker.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(LocalDate value) {
                return value == null ? "" : DATE_FORMAT.format(value);
            }

            @Override
            public LocalDate fromString(String text) {
                if (text == null || text.trim().isEmpty()) return null;
                return LocalDate.parse(text.trim(), DATE_FORMAT);
            }
        });
    }

    private void loadExisting() {
        try {
            WaybillService.WaybillDetails details = WaybillService.findById(waybillId);
            if (details == null) {
                showError("The selected waybill could not be found.");
                return;
            }

            numberLabel.setText(details.waybillNumber());
            waybillDate.setValue(details.waybillDate());
            estimatedDelivery.setValue(details.estimatedDeliveryDate());

            shipperSelector.selectCompanyByName(details.shipper() == null ? "" : details.shipper().companyName());
            consigneeSelector.selectCompanyByName(details.consignee() == null ? "" : details.consignee().companyName());

            carrier.setText(safe(details.carrierName()));
            driver.setText(safe(details.driverName()));
            vehicle.setText(safe(details.vehicleTrailerNo()));
            origin.setText(safe(details.originLoadingPoint()));
            destination.setText(safe(details.destinationUnloadingPoint()));
            specialInstructions.setText(safe(details.specialInstructions()));
            hazardous.setSelected(details.hazardousMaterials());
            remarks.setText(safe(details.remarks()));

            itemsTable.getItems().clear();
            for (WaybillService.WaybillItemData item : details.items()) {
                ItemRow row = new ItemRow();
                row.description.set(safe(item.description()));
                row.packageType.set(safe(item.packageType()));
                row.quantity.set(numberText(item.quantity()));
                row.weight.set(numberText(item.weightKg()));
                row.volume.set(numberText(item.volumeM3()));
                itemsTable.getItems().add(row);
            }
            updateItemsTableHeight();
        } catch (RuntimeException exception) {
            showError(exception.getMessage() == null ? "Unable to load the waybill." : exception.getMessage());
        }
    }

    private void saveNew(boolean viewSavedWaybills) {
        clearMessage();
        WaybillService.SavedWaybill saved = persistNew();
        if (saved == null) return;
        if (viewSavedWaybills) {
            onSaved.run();
        } else {
            showSaveSuccessDialog(saved);
        }
    }

    private void showSaveSuccessDialog(WaybillService.SavedWaybill saved) {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Waybill Saved");
        dialog.setHeaderText("Waybill saved successfully");
        dialog.setContentText("Waybill " + saved.waybillNumber() + " saved successfully.");
        if (getScene() != null && getScene().getWindow() != null) {
            dialog.initOwner(getScene().getWindow());
        }

        ButtonType view = new ButtonType("View Waybill", ButtonBar.ButtonData.OK_DONE);
        ButtonType pdf = new ButtonType("Generate PDF", ButtonBar.ButtonData.OTHER);
        ButtonType word = new ButtonType("Generate Word", ButtonBar.ButtonData.OTHER);
        ButtonType createNew = new ButtonType("Create New", ButtonBar.ButtonData.OTHER);
        dialog.getButtonTypes().setAll(view, pdf, word, createNew, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(result -> {
            if (result == view) {
                onViewSaved.accept(saved.id());
            } else if (result == pdf) {
                WaybillReportActions.generatePdf(getScene() == null ? null : getScene().getWindow(), saved.id());
            } else if (result == word) {
                WaybillReportActions.generateWord(getScene() == null ? null : getScene().getWindow(), saved.id());
            } else if (result == createNew) {
                clearForm();
                onCreateNew.run();
            }
        });
    }

    private WaybillService.SavedWaybill persistNew() {
        WaybillService.WaybillData data = collectAndValidate();
        if (data == null) return null;
        try {
            return WaybillService.save(data);
        } catch (RuntimeException exception) {
            showError(exception.getMessage() == null ? "Unable to save the waybill." : exception.getMessage());
            return null;
        }
    }

    private void updateExisting() {
        clearMessage();
        WaybillService.WaybillData data = collectAndValidate();
        if (data == null) return;
        saveButton.setDisable(true);
        try {
            WaybillService.update(waybillId, data);
            showSuccess("Waybill " + numberLabel.getText() + " updated successfully.");
            onSaved.run();
        } catch (RuntimeException exception) {
            showError(exception.getMessage() == null ? "Unable to update the waybill." : exception.getMessage());
        } finally {
            saveButton.setDisable(false);
        }
    }

    private WaybillService.WaybillData collectAndValidate() {
        LocalDate date = waybillDate.getValue();
        if (date == null) { showError("Waybill date is required."); return null; }
        LocalDate delivery;
        try {
            String deliveryText = estimatedDelivery.getEditor().getText().trim();
            if (deliveryText.isEmpty()) {
                delivery = null;
            } else {
                delivery = LocalDate.parse(deliveryText, DATE_FORMAT);
                estimatedDelivery.setValue(delivery);
            }
        } catch (DateTimeParseException exception) {
            showError("Estimated delivery date must be a valid date in dd-MM-yyyy format.");
            estimatedDelivery.requestFocus();
            return null;
        }

        String shipperName = shipperSelector.getCompanyName();
        String consigneeName = consigneeSelector.getCompanyName();
        if (shipperName.isBlank()) { showError("Shipper / Consignor company name is required."); return null; }
        if (consigneeName.isBlank()) { showError("Consignee / Receiver company name is required."); return null; }

        List<WaybillService.WaybillItemData> rows = new ArrayList<>();
        for (ItemRow row : itemsTable.getItems()) {
            if (row.isBlank()) continue;
            if (row.description.get().isBlank()) { showError("Each item must have a description of goods."); return null; }
            try {
                rows.add(new WaybillService.WaybillItemData(
                        row.description.get(), row.packageType.get(), number(row.quantity.get()),
                        number(row.weight.get()), number(row.volume.get())));
            } catch (NumberFormatException exception) {
                showError("Quantity, weight and volume must be valid non-negative numbers.");
                return null;
            }
        }
        if (rows.isEmpty()) { showError("Add at least one item to the waybill."); return null; }

        return new WaybillService.WaybillData(
                date,
                company(shipperSelector, shipperContact, shipperAddress, shipperPhone, shipperEmail),
                company(consigneeSelector, consigneeContact, consigneeAddress, consigneePhone, consigneeEmail),
                carrier.getText(), driver.getText(), vehicle.getText(), origin.getText(), destination.getText(),
                delivery, specialInstructions.getText(), hazardous.isSelected(), remarks.getText(), SessionContext.requireUserId(), rows);
    }

    private void clearForm() {
        waybillDate.setValue(LocalDate.now());
        estimatedDelivery.setValue(null);
        shipperSelector.clearSelection();
        consigneeSelector.clearSelection();
        for (TextField field : new TextField[]{shipperContact, shipperPhone, shipperEmail, consigneeContact, consigneePhone, consigneeEmail, carrier, driver, vehicle, origin, destination}) field.clear();
        for (TextArea area : new TextArea[]{shipperAddress, consigneeAddress, specialInstructions, remarks}) area.clear();
        hazardous.setSelected(false);
        itemsTable.getItems().clear();
        updateItemsTableHeight();
        refreshPreview();
    }

    private void refreshPreview() {
        try {
            numberLabel.setText(WaybillNumberService.preview(waybillDate.getValue() == null ? LocalDate.now() : waybillDate.getValue()));
        } catch (RuntimeException exception) {
            numberLabel.setText("Unable to generate preview");
        }
    }

    private void updateItemsTableHeight() {
        int count = itemsTable.getItems().size();
        double desired = 36 + Math.max(1, count) * 36 + 4;
        itemsTable.setPrefHeight(Math.min(300, Math.max(72, desired)));
    }

    private static WaybillService.CompanyData company(CompanySelector selector, TextField contact, TextArea address, TextField phone, TextField email) {
        CompanyService.CompanyRecord selected = selector.getSelectedCompany();
        if (selected != null) {
            return new WaybillService.CompanyData(selected.companyName(), selected.contactPerson(), selected.address(), selected.phoneNumber(), selected.emailAddress());
        }
        return new WaybillService.CompanyData(selector.getCompanyName(), contact.getText(), address.getText(), phone.getText(), email.getText());
    }

    private void refreshCompanySelectors() {
        shipperSelector.refreshCompanies();
        consigneeSelector.refreshCompanies();
    }

    private void populateShipper(CompanyService.CompanyRecord company) {
        populateCompany(company, shipperContact, shipperAddress, shipperPhone, shipperEmail);
        updatePartyDetailsState();
    }

    private void populateConsignee(CompanyService.CompanyRecord company) {
        populateCompany(company, consigneeContact, consigneeAddress, consigneePhone, consigneeEmail);
        updatePartyDetailsState();
    }

    private static void populateCompany(CompanyService.CompanyRecord data, TextField contact, TextArea address, TextField phone, TextField email) {
        if (data == null) {
            contact.clear();
            address.clear();
            phone.clear();
            email.clear();
            return;
        }
        contact.setText(safe(data.contactPerson()));
        address.setText(safe(data.address()));
        phone.setText(safe(data.phoneNumber()));
        email.setText(safe(data.emailAddress()));
    }

    private static String numberText(Double value) { return value == null ? "" : String.valueOf(value); }
    private static Double number(String value) {
        if (value == null || value.isBlank()) return null;
        double result = Double.parseDouble(value.trim());
        if (result < 0) throw new NumberFormatException("Negative value");
        return result;
    }
    private static String safe(String value) { return value == null ? "" : value; }

    private void clearMessage() { message.setText(""); message.getStyleClass().removeAll("error-message", "success-message"); }
    private void showError(String text) { message.getStyleClass().remove("success-message"); message.getStyleClass().add("error-message"); message.setText(text); }
    private void showSuccess(String text) { message.getStyleClass().remove("error-message"); message.getStyleClass().add("success-message"); message.setText(text); }

    private static VBox card() { VBox box = new VBox(14); box.getStyleClass().add("settings-card"); box.setPadding(new Insets(20)); return box; }
    private static Label heading(String text) { Label label = new Label(text); label.getStyleClass().add("section-heading"); label.setWrapText(true); return label; }
    private static Label label(String text, String css) { Label label = new Label(text); label.getStyleClass().add(css); return label; }
    private static TextField field(String prompt, int maxLength) { TextField field = new TextField(); field.setPromptText(prompt); InputLimits.maxLength(field, maxLength); field.getStyleClass().add("settings-field"); return field; }
    private static TextArea area(String prompt, int rows) { TextArea area = new TextArea(); area.setPromptText(prompt); area.setPrefRowCount(rows); area.setWrapText(true); area.getStyleClass().add("settings-field"); return area; }
    private static VBox labeledControl(String labelText, Region control) { VBox box = new VBox(5, label(labelText, "field-label"), control); control.setMaxWidth(Double.MAX_VALUE); return box; }
    private static GridPane grid2() { GridPane grid = new GridPane(); grid.setHgap(18); grid.setVgap(14); ColumnConstraints left = new ColumnConstraints(); left.setPercentWidth(50); left.setHgrow(Priority.ALWAYS); ColumnConstraints right = new ColumnConstraints(); right.setPercentWidth(50); right.setHgrow(Priority.ALWAYS); grid.getColumnConstraints().addAll(left, right); return grid; }
    private static void addField(GridPane grid, int col, int row, String labelText, Region control) { VBox box = labeledControl(labelText, control); GridPane.setHgrow(box, Priority.ALWAYS); grid.add(box, col, row); }

    private static TableColumn<ItemRow, String> editableColumn(String title, int index, double width, boolean numeric, int maxLength) {
        TableColumn<ItemRow, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(data -> data.getValue().property(index));
        column.setCellFactory(ignored -> new PersistentTextCell(numeric, maxLength));
        column.setOnEditCommit(event -> event.getRowValue().set(index, event.getNewValue()));
        return column;
    }

    private static final class PersistentTextCell extends TableCell<ItemRow, String> {
        private final boolean numeric;
        private final int maxLength;
        private TextField editor;
        private PersistentTextCell(boolean numeric, int maxLength) { this.numeric = numeric; this.maxLength = maxLength; }
        @Override public void startEdit() { if (isEmpty()) return; super.startEdit(); createEditor(); editor.setText(getItem() == null ? "" : getItem()); setText(null); setGraphic(editor); editor.requestFocus(); editor.selectAll(); }
        private void createEditor() { editor = new TextField(); editor.setMaxWidth(Double.MAX_VALUE); if (numeric) InputLimits.numeric(editor, maxLength, 3); else InputLimits.maxLength(editor, maxLength); editor.setOnAction(event -> commitEditorValue()); editor.focusedProperty().addListener((obs, oldFocused, focused) -> { if (!focused && isEditing()) commitEditorValue(); }); }
        private void commitEditorValue() { if (!isEditing() || editor == null) return; commitEdit(editor.getText() == null ? "" : editor.getText()); }
        @Override public void cancelEdit() { super.cancelEdit(); editor = null; setText(getItem()); setGraphic(null); }
        @Override protected void updateItem(String item, boolean empty) { super.updateItem(item, empty); if (empty) { setText(null); setGraphic(null); } else if (isEditing() && editor != null) { editor.setText(item == null ? "" : item); setText(null); setGraphic(editor); } else { setText(item); setGraphic(null); } }
    }

    private static final class ItemRow {
        final StringProperty description = new SimpleStringProperty("");
        final StringProperty packageType = new SimpleStringProperty("");
        final StringProperty quantity = new SimpleStringProperty("");
        final StringProperty weight = new SimpleStringProperty("");
        final StringProperty volume = new SimpleStringProperty("");
        StringProperty property(int index) { return switch (index) { case 0 -> description; case 1 -> packageType; case 2 -> quantity; case 3 -> weight; default -> volume; }; }
        void set(int index, String value) { property(index).set(value == null ? "" : value); }
        boolean isBlank() { return description.get().isBlank() && packageType.get().isBlank() && quantity.get().isBlank() && weight.get().isBlank() && volume.get().isBlank(); }
    }
}
