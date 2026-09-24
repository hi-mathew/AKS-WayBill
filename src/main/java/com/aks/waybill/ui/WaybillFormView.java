package com.aks.waybill.ui;

import com.aks.waybill.service.CompanyService;
import com.aks.waybill.service.WaybillNumberService;
import com.aks.waybill.service.WaybillService;
import com.aks.waybill.service.SavedDataService;
import com.aks.waybill.security.SessionContext;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Popup;

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
    private boolean dirty;
    private boolean loadingExisting;
    private boolean savedNew;
    /** Prevent master-data population from being interpreted as manual typing. */
    private boolean applyingMasterSelection;
    private String loadedStatus = "DRAFT";

    private final Label numberLabel = new Label();
    private final DatePicker waybillDate = new DatePicker(LocalDate.now());
    private final DatePicker estimatedDelivery = new DatePicker();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final String SELECT_CARRIER = "— Select saved carrier —";
    private static final String SELECT_LOCATION = "— Select saved location —";

    private final CompanySelector shipperSelector = new CompanySelector(CompanyService.CompanyType.SHIPPER, this::populateShipper, this::refreshCompanySelectors);
    private final TextField shipperContact = field("Contact person", InputLimits.CONTACT_PERSON);
    private final TextArea shipperAddress = area("Address", 2);
    private final TextField shipperPhone = field("Phone number", InputLimits.PHONE);
    private final TextField shipperEmail = field("Email address", InputLimits.EMAIL);

    private final CompanySelector consigneeSelector = new CompanySelector(CompanyService.CompanyType.CONSIGNEE, this::populateConsignee, this::refreshCompanySelectors);
    private final TextField consigneeContact = field("Contact person", InputLimits.CONTACT_PERSON);
    private final TextArea consigneeAddress = area("Address", 2);
    private final TextField consigneePhone = field("Phone number", InputLimits.PHONE);
    private final TextField consigneeEmail = field("Email address", InputLimits.EMAIL);

    private final ComboBox<String> savedCarrier = savedSelector("— Select saved carrier —");
    private final List<String> carrierMasterValues = new ArrayList<>();
    private final TextField carrier = field("Carrier name", InputLimits.CARRIER);
    private final TextField driver = field("Driver name", InputLimits.DRIVER);
    private final TextField vehicle = field("Vehicle / Trailer No.", InputLimits.VEHICLE);
    private final ComboBox<String> savedOrigin = savedSelector("— Select or type below —");
    private final List<String> locationMasterValues = new ArrayList<>();
    private final TextField origin = field("Type or edit location", InputLimits.LOCATION);
    private final ComboBox<String> savedDestination = savedSelector("— Select or type below —");
    private final TextField destination = field("Type or edit location", InputLimits.LOCATION);

    private final Popup carrierSuggestionPopup = new Popup();
    private final Popup originSuggestionPopup = new Popup();
    private final Popup destinationSuggestionPopup = new Popup();
    private final ListView<String> carrierSuggestions = suggestionList();
    private final ListView<String> originSuggestions = suggestionList();
    private final ListView<String> destinationSuggestions = suggestionList();

    private final TextArea specialInstructions = area("Special instructions / handling", 3);
    private final CheckBox hazardous = new CheckBox("Yes — hazardous materials");
    private final TextArea remarks = area("Remarks", 3);

    private final TextField shipperDeclarationName = field("Name", InputLimits.DISPLAY_NAME);
    private final DatePicker shipperDeclarationDate = new DatePicker();
    private final TextField carrierReceiptDriverName = field("Driver Name", InputLimits.DRIVER);
    private final DatePicker carrierReceiptDate = new DatePicker();
    private final TextField consigneePodReceiverName = field("Receiver Name", InputLimits.DISPLAY_NAME);
    private final DatePicker consigneePodDate = new DatePicker();

    private final TableView<ItemRow> itemsTable = new TableView<>();
    private final Label message = new Label();

    private final Button saveButton = new Button("Save Changes");
    private final Button cancelButton = new Button("Cancel");
    private final Button addItemButton = new Button("＋ Add Item");
    private final Button removeItemButton = new Button("Remove Selected");
    private final Button newCarrierButton = new Button("+ New Carrier");
    private final Button newOriginButton = new Button("+ New Location");
    private final Button newDestinationButton = new Button("+ New Location");
    private final Button finalizeButton = new Button("Mark as Final");
    private final Button revertButton = new Button("Return to Draft");

    public WaybillFormView(Mode mode, long waybillId, Runnable onSaved, Runnable onCancel, Runnable onCreateNew) {
        this(mode, waybillId, onSaved, onCancel, onCreateNew, id -> {});
    }

    public WaybillFormView(Mode mode, long waybillId, Runnable onSaved, Runnable onCancel,
                           Runnable onCreateNew, Consumer<Long> onViewSaved) {
        super(titleFor(mode), subtitleFor(mode));
        getStyleClass().add("waybill-form-view");
        setPadding(new Insets(16, 30, 24, 30));
        setSpacing(10);
        this.mode = mode;
        this.waybillId = waybillId;
        this.onSaved = onSaved == null ? () -> {} : onSaved;
        this.onCancel = onCancel == null ? () -> {} : onCancel;
        this.onCreateNew = onCreateNew == null ? () -> {} : onCreateNew;
        this.onViewSaved = onViewSaved == null ? id -> {} : onViewSaved;

        numberLabel.getStyleClass().add("waybill-number");
        configureDatePicker(estimatedDelivery);
        configureDatePicker(shipperDeclarationDate);
        configureDatePicker(carrierReceiptDate);
        configureDatePicker(consigneePodDate);
        configureSuggestionPopups();
        configureSavedSelectors();
        build();
        installDirtyTracking();
        applyMode();

        if (mode == Mode.NEW) {
            refreshPreview();
            waybillDate.valueProperty().addListener((obs, oldValue, newValue) -> refreshPreview());
        } else {
            loadingExisting = true;
            loadExisting();
            loadingExisting = false;
            dirty = false;
            applyMode();
            updateLifecycleActions();
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
        VBox page = new VBox(14);
        page.setPadding(new Insets(2, 0, 24, 0));
        page.getChildren().addAll(
                headerCard(),
                partiesCard(),
                carrierTransitCard(),
                itemsCard(),
                handlingCard(),
                declarationsCard(),
                buttons()
        );

        // Keep the primary New Waybill actions visible while the long form scrolls.
        if (mode == Mode.NEW) {
            getChildren().add(topActionBar());
        }

        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("content-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);
    }

    private VBox headerCard() {
        VBox card = card();
        card.setPadding(new Insets(12, 16, 12, 16));

        HBox header = new HBox(18);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox number = new VBox(4, label("WAYBILL NO.", "field-label"), numberLabel);
        number.setMaxWidth(460);
        VBox.setVgrow(number, Priority.NEVER);

        VBox date = new VBox(4, label("Date", "field-label"), waybillDate);
        date.setPrefWidth(210);
        date.setMaxWidth(210);
        waybillDate.setMaxWidth(Double.MAX_VALUE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(number, spacer, date);
        card.getChildren().add(header);
        return card;
    }

    private VBox partiesCard() {
        VBox outer = card();
        outer.getChildren().add(heading("1 & 2. SHIPPER / CONSIGNOR AND CONSIGNEE / RECEIVER"));

        GridPane grid = new GridPane();
        grid.setHgap(18);
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
        VBox panel = new VBox(7);
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

        configureMasterDataButtons();

        VBox carrierLabelRow = fieldLabelWithButton("Saved Carrier (optional)", newCarrierButton);
        VBox carrierBox = new VBox(5, carrierLabelRow, savedCarrier, carrier);
        VBox originLabelRow = fieldLabelWithButton("Origin / Loading Point", newOriginButton);
        VBox originBox = new VBox(5, originLabelRow, savedOrigin, origin);
        VBox driverBox = new VBox(5, label("Driver Name", "field-label"), driver);
        VBox destinationLabelRow = fieldLabelWithButton("Destination / Unloading Point", newDestinationButton);
        VBox destinationBox = new VBox(5, destinationLabelRow, savedDestination, destination);
        VBox vehicleBox = new VBox(5, label("Vehicle / Trailer No.", "field-label"), vehicle);
        VBox deliveryBox = new VBox(5, label("Estimated Delivery Date", "field-label"), estimatedDelivery);

        grid.add(carrierBox, 0, 0);
        grid.add(originBox, 1, 0);
        grid.add(driverBox, 0, 1);
        grid.add(destinationBox, 1, 1);
        grid.add(vehicleBox, 0, 2);
        grid.add(deliveryBox, 1, 2);
        card.getChildren().add(grid);
        return card;
    }

    private VBox fieldLabelWithButton(String text, Button button) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        Label fieldLabel = label(text, "field-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        button.getStyleClass().add("secondary-button");
        button.setMinHeight(28);
        button.setFocusTraversable(false);
        row.getChildren().addAll(fieldLabel, spacer, button);
        VBox wrapper = new VBox(row);
        return wrapper;
    }

    private void configureMasterDataButtons() {
        newCarrierButton.setOnAction(event -> openNewCarrierDialog());
        newOriginButton.setOnAction(event -> openNewLocationDialog(true));
        newDestinationButton.setOnAction(event -> openNewLocationDialog(false));
    }

    private void openNewCarrierDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("New Carrier");
        dialog.setHeaderText("Add a reusable carrier with driver and vehicle details.");
        dialog.initModality(Modality.APPLICATION_MODAL);

        TextField name = field("Carrier name", InputLimits.CARRIER);
        TextField driverField = field("Driver name", InputLimits.DRIVER);
        TextField vehicleField = field("Vehicle / Trailer No.", InputLimits.VEHICLE);

        VBox form = new VBox(10,
                label("Carrier Name", "field-label"), name,
                label("Driver Name", "field-label"), driverField,
                label("Vehicle / Trailer No.", "field-label"), vehicleField);
        form.setPrefWidth(520);
        dialog.getDialogPane().setContent(form);

        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == save ? save : null);

        dialog.showAndWait().ifPresent(result -> {
            if (result != save) return;
            try {
                SavedDataService.CarrierRecord record = SavedDataService.createCarrier(
                        name.getText(), driverField.getText(), vehicleField.getText());
                loadSavedCarriers();
                selectSavedCarrier(record.name(), true);
            } catch (RuntimeException ex) {
                showError(ex.getMessage() == null ? "Unable to save carrier." : ex.getMessage());
            }
        });
    }

    private void openNewLocationDialog(boolean originField) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("New Location");
        dialog.setHeaderText("Add a reusable loading or unloading location.");
        dialog.initModality(Modality.APPLICATION_MODAL);

        TextField name = field("Location name", InputLimits.LOCATION);
        VBox form = new VBox(10, label("Location Name", "field-label"), name);
        form.setPrefWidth(520);
        dialog.getDialogPane().setContent(form);

        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == save ? save : null);

        dialog.showAndWait().ifPresent(result -> {
            if (result != save) return;
            try {
                SavedDataService.LocationRecord record = SavedDataService.saveIfMissingLocation(name.getText());
                loadSavedLocations();
                if (originField) selectSavedValue(savedOrigin, record.name());
                else selectSavedValue(savedDestination, record.name());
                if (originField) origin.setText(record.name());
                else destination.setText(record.name());
            } catch (RuntimeException ex) {
                showError(ex.getMessage() == null ? "Unable to save location." : ex.getMessage());
            }
        });
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

    private VBox declarationsCard() {
        VBox card = card();
        card.getChildren().add(heading("SIGNATURES & DECLARATIONS"));

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(10);
        for (int i = 0; i < 3; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(33.333);
            cc.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(cc);
        }

        grid.add(declarationPanel("SHIPPER DECLARATION",
                "I declare that cargo details above are accurate and bound by Terms & Conditions on Page 2.",
                shipperDeclarationName, shipperDeclarationDate, "Name"), 0, 0);
        grid.add(declarationPanel("CARRIER RECEIPT",
                "Received goods in apparent good order, subject to Carrier Terms & Conditions on Page 2.",
                carrierReceiptDriverName, carrierReceiptDate, "Driver Name"), 1, 0);
        grid.add(declarationPanel("CONSIGNEE PROOF OF DELIVERY",
                "Received goods in good order and condition, except as noted.",
                consigneePodReceiverName, consigneePodDate, "Receiver Name"), 2, 0);
        card.getChildren().add(grid);
        return card;
    }

    private VBox declarationPanel(String title, String description, TextField nameField, DatePicker datePicker, String nameLabel) {
        VBox panel = new VBox(8);
        panel.getStyleClass().add("party-panel");
        Label titleLabel = label(title, "field-label");
        Label desc = label(description, "card-description");
        desc.setWrapText(true);
        panel.getChildren().addAll(titleLabel, desc, labeledControl(nameLabel, nameField), labeledControl("Date", datePicker));
        return panel;
    }

    private HBox topActionBar() {
        HBox box = new HBox(16);
        box.setAlignment(Pos.CENTER_RIGHT);
        box.getStyleClass().add("waybill-top-actions");
        box.getChildren().addAll(
                newActionButton("Clear", false, 100),
                newActionButton("Save & View Saved Waybills", false, 224),
                newActionButton("Save", true, 110)
        );
        return box;
    }

    private Button newActionButton(String text, boolean primary, double width) {
        Button button = new Button(text);
        button.getStyleClass().add(primary ? "primary-button" : "secondary-button");
        button.setMinWidth(width);
        button.setPrefWidth(width);
        button.setMaxWidth(width);
        button.setMinHeight(40);
        button.setPrefHeight(40);
        button.setMaxHeight(40);
        if ("Clear".equals(text)) {
            button.setOnAction(event -> confirmClearForm());
        } else if ("Save & View Saved Waybills".equals(text)) {
            button.setOnAction(event -> saveNew(true));
        } else {
            button.setOnAction(event -> saveNew(false));
        }
        return button;
    }

    private HBox buttons() {
        HBox box = new HBox(16);
        box.setAlignment(Pos.CENTER_RIGHT);
        box.getStyleClass().add("waybill-bottom-actions");
        message.getStyleClass().add("form-message");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        if (mode == Mode.NEW) {
            Button clear = newActionButton("Clear", false, 100);
            Button saveAndView = newActionButton("Save & View Saved Waybills", false, 224);
            Button saveAndCreate = newActionButton("Save", true, 110);
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
            finalizeButton.getStyleClass().add("primary-button");
            finalizeButton.setOnAction(event -> confirmFinalize());
            revertButton.getStyleClass().add("secondary-button");
            revertButton.setOnAction(event -> confirmRevert());
            Button pdf = new Button("Generate PDF");
            pdf.getStyleClass().add("secondary-button");
            pdf.setOnAction(event -> WaybillReportActions.generatePdf(getScene() == null ? null : getScene().getWindow(), waybillId));
            Button word = new Button("Generate Word");
            word.getStyleClass().add("secondary-button");
            word.setOnAction(event -> WaybillReportActions.generateWord(getScene() == null ? null : getScene().getWindow(), waybillId));
            box.getChildren().addAll(finalizeButton, revertButton);
            box.getChildren().addAll(pdf, word, back);
        }
        return box;
    }

    private void applyMode() {
        boolean editable = mode != Mode.VIEW && (mode != Mode.EDIT || SessionContext.isAdmin() || "DRAFT".equalsIgnoreCase(loadedStatus));
        setEditable(waybillDate, editable);
        setEditable(estimatedDelivery, editable);

        Control[] controls = {
                carrier, driver, vehicle, origin, destination,
                specialInstructions, remarks,
                shipperDeclarationName, shipperDeclarationDate, carrierReceiptDriverName, carrierReceiptDate,
                consigneePodReceiverName, consigneePodDate
        };
        for (Control control : controls) control.setDisable(!editable);
        hazardous.setDisable(!editable);
        addItemButton.setDisable(!editable);
        removeItemButton.setDisable(!editable);
        shipperSelector.setReadOnly(!editable);
        consigneeSelector.setReadOnly(!editable);
        savedCarrier.setDisable(!editable);
        savedOrigin.setDisable(!editable);
        savedDestination.setDisable(!editable);
        newCarrierButton.setDisable(!editable);
        newOriginButton.setDisable(!editable);
        newDestinationButton.setDisable(!editable);
        updatePartyDetailsState();
        if (mode == Mode.EDIT) saveButton.setDisable(!editable);

        if (mode == Mode.VIEW) itemsTable.setEditable(false);
    }

    private void updatePartyDetailsState() {
        boolean formEditable = mode != Mode.VIEW;
        setPartyDetailsEditable(formEditable, shipperContact, shipperAddress, shipperPhone, shipperEmail);
        setPartyDetailsEditable(formEditable, consigneeContact, consigneeAddress, consigneePhone, consigneeEmail);
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

            loadedStatus = details.status() == null ? "DRAFT" : details.status();
            numberLabel.setText(details.waybillNumber());
            waybillDate.setValue(details.waybillDate());
            estimatedDelivery.setValue(details.estimatedDeliveryDate());

            shipperSelector.selectCompanyByName(details.shipper() == null ? "" : details.shipper().companyName());
            consigneeSelector.selectCompanyByName(details.consignee() == null ? "" : details.consignee().companyName());
            populateCompany(details.shipper(), shipperContact, shipperAddress, shipperPhone, shipperEmail);
            populateCompany(details.consignee(), consigneeContact, consigneeAddress, consigneePhone, consigneeEmail);

            selectSavedCarrier(details.carrierName(), false);
            driver.setText(safe(details.driverName()));
            vehicle.setText(safe(details.vehicleTrailerNo()));
            origin.setText(safe(details.originLoadingPoint()));
            destination.setText(safe(details.destinationUnloadingPoint()));
            selectSavedValue(savedOrigin, details.originLoadingPoint());
            selectSavedValue(savedDestination, details.destinationUnloadingPoint());
            specialInstructions.setText(safe(details.specialInstructions()));
            hazardous.setSelected(details.hazardousMaterials());
            remarks.setText(safe(details.remarks()));
            shipperDeclarationName.setText(safe(details.shipperDeclarationName()));
            shipperDeclarationDate.setValue(details.shipperDeclarationDate());
            carrierReceiptDriverName.setText(safe(details.carrierReceiptDriverName()));
            carrierReceiptDate.setValue(details.carrierReceiptDate());
            consigneePodReceiverName.setText(safe(details.consigneePodReceiverName()));
            consigneePodDate.setValue(details.consigneePodDate());

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

    public boolean hasUnsavedChanges() { return mode != Mode.VIEW && dirty && !savedNew; }

    /** Saves the current form without opening the post-save dialog; used by exit/logout protection. */
    public boolean saveForExit() {
        if (!hasUnsavedChanges()) return true;
        try {
            if (mode == Mode.NEW) {
                WaybillService.SavedWaybill saved = persistNew();
                if (saved == null) return false;
                savedNew = true; dirty = false;
            } else if (mode == Mode.EDIT) {
                persistUpdateForExit();
            }
            return true;
        } catch (RuntimeException ex) {
            showError(ex.getMessage() == null ? "Unable to save the unsaved changes." : ex.getMessage());
            return false;
        }
    }

    private void persistUpdateForExit() {
        WaybillService.WaybillData data = collectAndValidate();
        WaybillService.update(waybillId, data);
        dirty = false;
    }

    private void installDirtyTracking() {
        List<Control> controls = List.of(waybillDate, estimatedDelivery, shipperContact, shipperAddress, shipperPhone, shipperEmail,
                consigneeContact, consigneeAddress, consigneePhone, consigneeEmail, savedCarrier, carrier, driver, vehicle, savedOrigin, origin,
                savedDestination, destination, specialInstructions, hazardous, remarks, shipperDeclarationName, shipperDeclarationDate,
                carrierReceiptDriverName, carrierReceiptDate, consigneePodReceiverName, consigneePodDate);
        for (Control control : controls) {
            if (control instanceof TextInputControl text) text.textProperty().addListener((o,a,b)->markDirty());
            else if (control instanceof DatePicker date) date.valueProperty().addListener((o,a,b)->markDirty());
            else if (control instanceof ComboBox<?> combo) combo.valueProperty().addListener((o,a,b)->markDirty());
            else if (control instanceof CheckBox check) check.selectedProperty().addListener((o,a,b)->markDirty());
        }
        shipperSelector.companyNameProperty().addListener((o,a,b)->markDirty());
        consigneeSelector.companyNameProperty().addListener((o,a,b)->markDirty());
        itemsTable.getItems().addListener((javafx.collections.ListChangeListener<ItemRow>) change -> markDirty());
    }

    private void markDirty() { if (!loadingExisting && mode != Mode.VIEW && !savedNew) dirty = true; }

    private void updateLifecycleActions() {
        if (mode != Mode.VIEW) return;
        finalizeButton.setVisible("DRAFT".equalsIgnoreCase(loadedStatus) && canCurrentUserFinalize());
        finalizeButton.setManaged(finalizeButton.isVisible());
        revertButton.setVisible("FINAL".equalsIgnoreCase(loadedStatus) && SessionContext.isAdmin());
        revertButton.setManaged(revertButton.isVisible());
    }

    public void handleShortcutSave() {
        if (mode == Mode.NEW) saveNew(false);
        else if (mode == Mode.EDIT) updateExisting();
    }

    public void handleShortcutPrint() {
        if (mode == Mode.NEW) {
            showError("Save the waybill before generating a PDF report.");
            return;
        }
        WaybillReportActions.generatePdf(getScene() == null ? null : getScene().getWindow(), waybillId);
    }

    private boolean canCurrentUserFinalize() { return SessionContext.isAdmin() || ownerOfCurrentWaybill(); }

    private boolean ownerOfCurrentWaybill() {
        try { return WaybillService.findById(waybillId) != null && WaybillService.canEdit(waybillId); } catch (RuntimeException ex) { return false; }
    }

    private void confirmFinalize() {
        Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Mark this waybill as Final? Normal users will no longer be able to edit it.",ButtonType.OK,ButtonType.CANCEL);
        a.setTitle("Finalize Waybill"); a.setHeaderText("Finalize Waybill");
        if(a.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK){try{WaybillService.finalizeWaybill(waybillId); loadedStatus="FINAL"; applyMode(); onCancel.run();}catch(Exception e){showError(e.getMessage());}}
    }

    private void confirmRevert() {
        TextInputDialog d=new TextInputDialog(); d.setTitle("Return to Draft"); d.setHeaderText("Return Finalized Waybill to Draft"); d.setContentText("Reason:");
        d.showAndWait().ifPresent(reason->{if(reason==null||reason.isBlank()){showError("A reason is required.");return;} try{WaybillService.revertToDraft(waybillId,reason);loadedStatus="DRAFT";applyMode();onCancel.run();}catch(Exception e){showError(e.getMessage());}});
    }

    private void saveNew(boolean viewSavedWaybills) {
        clearMessage();
        if (savedNew) {
            showError("This waybill has already been saved. Choose Create New to start another waybill.");
            return;
        }
        WaybillService.SavedWaybill saved = persistNew();
        if (saved == null) return;
        savedNew = true;
        dirty = false;
        if (viewSavedWaybills) onSaved.run(); else showSaveSuccessDialog(saved);
    }

    private void showSaveSuccessDialog(WaybillService.SavedWaybill saved) {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Waybill Saved");
        dialog.setHeaderText("Waybill saved successfully");
        dialog.setContentText("Waybill " + saved.waybillNumber() + " saved successfully.");
        if (getScene() != null && getScene().getWindow() != null) {
            dialog.initOwner(getScene().getWindow());
        }

        // Shared W.A.S.P dialog styling calculates a stable width from the actual
        // action labels, so this dialog does not need hard-coded dimensions.
        setInformationGraphic(dialog);

        ButtonType view = new ButtonType("View Waybill", ButtonBar.ButtonData.OK_DONE);
        ButtonType pdf = new ButtonType("Generate PDF", ButtonBar.ButtonData.OTHER);
        ButtonType word = new ButtonType("Generate Word", ButtonBar.ButtonData.OTHER);
        ButtonType createNew = new ButtonType("Create New Waybill", ButtonBar.ButtonData.OTHER);
        ButtonType close = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getButtonTypes().setAll(view, pdf, word, createNew, close);

        Button viewButton = (Button) dialog.getDialogPane().lookupButton(view);
        Button pdfButton = (Button) dialog.getDialogPane().lookupButton(pdf);
        Button wordButton = (Button) dialog.getDialogPane().lookupButton(word);
        Button createButton = (Button) dialog.getDialogPane().lookupButton(createNew);
        Button closeButton = (Button) dialog.getDialogPane().lookupButton(close);

        while (true) {
            var result = dialog.showAndWait().orElse(close);
            if (result == view) { onViewSaved.accept(saved.id()); return; }
            if (result == pdf) { WaybillReportActions.generatePdf(getScene() == null ? null : getScene().getWindow(), saved.id()); continue; }
            if (result == word) { WaybillReportActions.generateWord(getScene() == null ? null : getScene().getWindow(), saved.id()); continue; }
            if (result == createNew) { clearForm(); savedNew = false; dirty = false; onCreateNew.run(); return; }
            onSaved.run();
            return;
        }
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
            dirty = false;
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
                delivery, specialInstructions.getText(), hazardous.isSelected(), remarks.getText(),
                shipperDeclarationName.getText(), shipperDeclarationDate.getValue(),
                carrierReceiptDriverName.getText(), carrierReceiptDate.getValue(),
                consigneePodReceiverName.getText(), consigneePodDate.getValue(),
                SessionContext.requireUserId(), rows);
    }

    private void confirmClearForm() {
        Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Clear all entered information? Any unsaved changes will be lost.",ButtonType.OK,ButtonType.CANCEL);
        a.setTitle("Clear Waybill Form"); a.setHeaderText("Clear Form");
        if(getScene()!=null) a.initOwner(getScene().getWindow());
        if(a.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK) clearForm();
    }

    private void clearForm() {
        loadingExisting = true;
        savedNew = false;
        dirty = false;
        waybillDate.setValue(LocalDate.now());
        estimatedDelivery.setValue(null);
        shipperDeclarationName.clear();
        shipperDeclarationDate.setValue(null);
        carrierReceiptDriverName.clear();
        carrierReceiptDate.setValue(null);
        consigneePodReceiverName.clear();
        consigneePodDate.setValue(null);
        savedCarrier.getSelectionModel().select(SELECT_CARRIER);
        savedOrigin.getSelectionModel().select(SELECT_LOCATION);
        savedDestination.getSelectionModel().select(SELECT_LOCATION);
        shipperSelector.clearSelection();
        consigneeSelector.clearSelection();
        for (TextField field : new TextField[]{shipperContact, shipperPhone, shipperEmail, consigneeContact, consigneePhone, consigneeEmail, carrier, driver, vehicle, origin, destination}) field.clear();
        for (TextArea area : new TextArea[]{shipperAddress, consigneeAddress, specialInstructions, remarks}) area.clear();
        hazardous.setSelected(false);
        itemsTable.getItems().clear();
        updateItemsTableHeight();
        refreshPreview();
        loadingExisting = false;
        dirty = false;
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
        return new WaybillService.CompanyData(selector.getCompanyName(), contact.getText(), address.getText(), phone.getText(), email.getText());
    }

    private void refreshCompanySelectors() {
        shipperSelector.refreshCompanies();
        consigneeSelector.refreshCompanies();
    }

    /**
     * Initialise the three text-field autocomplete popups. The ListViews are
     * intentionally hosted in Popup controls rather than inside the form so
     * the suggestions can extend beyond the form card without affecting layout.
     */
    private void configureSuggestionPopups() {
        configureSuggestionPopup(carrierSuggestionPopup, carrierSuggestions);
        configureSuggestionPopup(originSuggestionPopup, originSuggestions);
        configureSuggestionPopup(destinationSuggestionPopup, destinationSuggestions);
    }

    private static void configureSuggestionPopup(Popup popup, ListView<String> suggestions) {
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.setAutoFix(true);
        popup.setConsumeAutoHidingEvents(false);
        popup.getContent().clear();
        popup.getContent().add(suggestions);
    }

    private void configureSavedSelectors() {
        loadSavedCarriers();
        loadSavedLocations();

        // Master-data ComboBoxes are selection controls only. They intentionally
        // remain non-editable; searching/typing belongs to the text field below
        // each selector, just like the Company selector.
        savedCarrier.setEditable(false);
        savedOrigin.setEditable(false);
        savedDestination.setEditable(false);

        savedCarrier.setOnAction(event -> applyCarrierSelection(savedCarrier.getSelectionModel().getSelectedItem()));
        savedOrigin.setOnAction(event -> applyLocationSelection(savedOrigin, origin));
        savedDestination.setOnAction(event -> applyLocationSelection(savedDestination, destination));

        configureTextAutocomplete(carrier, carrierMasterValues, savedCarrier, SELECT_CARRIER,
                carrierSuggestionPopup, carrierSuggestions, value -> applyCarrierSelection(value));
        configureTextAutocomplete(origin, locationMasterValues, savedOrigin, SELECT_LOCATION,
                originSuggestionPopup, originSuggestions, value -> origin.setText(value));
        configureTextAutocomplete(destination, locationMasterValues, savedDestination, SELECT_LOCATION,
                destinationSuggestionPopup, destinationSuggestions, value -> destination.setText(value));
    }

    private void applyCarrierSelection(String value) {
        // A text-field edit intentionally resets the ComboBox to the clear option.
        // JavaFX may fire the ComboBox action event for that programmatic change;
        // do not let that event clear the text the user has just typed.
        if (applyingMasterSelection) return;
        applyingMasterSelection = true;
        try {
            if (SELECT_CARRIER.equals(value) || value == null || value.isBlank()) {
                carrier.clear();
                driver.clear();
                vehicle.clear();
                return;
            }
            SavedDataService.CarrierRecord record = SavedDataService.findCarrierByName(value, true);
            if (record != null) {
                carrier.setText(safe(record.name()));
                driver.setText(safe(record.driverName()));
                vehicle.setText(safe(record.vehicleTrailerNo()));
            } else {
                carrier.setText(value);
                driver.clear();
                vehicle.clear();
            }
        } finally {
            applyingMasterSelection = false;
        }
    }

    private void applyLocationSelection(ComboBox<String> selector, TextField editor) {
        // Ignore the action generated when autocomplete deliberately moves the
        // selector back to the clear option. The edited text must be preserved.
        if (applyingMasterSelection) return;
        applyingMasterSelection = true;
        try {
            String value = selector.getSelectionModel().getSelectedItem();
            if (SELECT_LOCATION.equals(value) || value == null || value.isBlank()) editor.clear();
            else editor.setText(value);
        } finally {
            applyingMasterSelection = false;
        }
    }

    /**
     * Adds CompanySelector-style autocomplete to the actual text-entry field.
     * The master-data ComboBox remains non-editable and is used only to select a
     * saved record. Typing a modified value therefore never requires editing the
     * ComboBox itself.
     */
    private void configureTextAutocomplete(TextField editor, List<String> masterValues,
                                           ComboBox<String> savedSelector, String clearOption,
                                           Popup popup, ListView<String> suggestions, Consumer<String> onSelected) {
        editor.textProperty().addListener((obs, oldValue, newValue) -> {
            if (loadingExisting || applyingMasterSelection) return;
            String typed = newValue == null ? "" : newValue.trim();

            // Once the user changes the master value, the waybill should no longer
            // be tied to that saved record. Preserve exactly what the user typed.
            if (!typed.isEmpty() && !clearOption.equals(typed)) {
                // Selecting the clear option must not invoke its normal action
                // handler here, because that handler intentionally clears the
                // editor. Preserve the exact text the user is typing while
                // simply breaking the master-record association.
                applyingMasterSelection = true;
                try {
                    savedSelector.getSelectionModel().select(clearOption);
                } finally {
                    applyingMasterSelection = false;
                }
                if (editor == carrier) {
                    driver.clear();
                    vehicle.clear();
                }
            }

            List<String> matches = masterValues.stream()
                    .filter(v -> v != null && !v.isBlank())
                    .filter(v -> typed.isBlank() || v.toLowerCase().contains(typed.toLowerCase()))
                    .toList();
            suggestions.getItems().setAll(matches);
            suggestions.getSelectionModel().clearSelection();
            if (typed.isBlank() || matches.isEmpty() || !editor.isFocused()) {
                popup.hide();
                return;
            }
            showSuggestions(editor, popup, suggestions);
        });

        editor.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case DOWN -> {
                    if (!popup.isShowing()) {
                        String typed = editor.getText() == null ? "" : editor.getText().trim();
                        List<String> matches = masterValues.stream()
                                .filter(v -> v != null && !v.isBlank())
                                .filter(v -> typed.isBlank() || v.toLowerCase().contains(typed.toLowerCase()))
                                .toList();
                        suggestions.getItems().setAll(matches);
                        showSuggestions(editor, popup, suggestions);
                    }
                    if (!suggestions.getItems().isEmpty()) {
                        int next = suggestions.getSelectionModel().getSelectedIndex() + 1;
                        if (next >= suggestions.getItems().size()) next = 0;
                        suggestions.getSelectionModel().select(next);
                        suggestions.scrollTo(next);
                    }
                    event.consume();
                }
                case UP -> {
                    if (!popup.isShowing()) {
                        String typed = editor.getText() == null ? "" : editor.getText().trim();
                        List<String> matches = masterValues.stream()
                                .filter(v -> v != null && !v.isBlank())
                                .filter(v -> typed.isBlank() || v.toLowerCase().contains(typed.toLowerCase()))
                                .toList();
                        suggestions.getItems().setAll(matches);
                        showSuggestions(editor, popup, suggestions);
                    }
                    if (!suggestions.getItems().isEmpty()) {
                        int current = suggestions.getSelectionModel().getSelectedIndex();
                        int previous = current <= 0 ? suggestions.getItems().size() - 1 : current - 1;
                        suggestions.getSelectionModel().select(previous);
                        suggestions.scrollTo(previous);
                    }
                    event.consume();
                }
                case ENTER -> {
                    String selected = suggestions.getSelectionModel().getSelectedItem();
                    String typed = editor.getText() == null ? "" : editor.getText().trim();
                    String exact = masterValues.stream().filter(v -> v.equalsIgnoreCase(typed)).findFirst().orElse(null);
                    String value = selected != null ? selected : exact;
                    if (value != null && !value.isBlank()) {
                        // Suppress the ComboBox action only while changing its selection
                        // programmatically. The actual master-selection callback must run
                        // after the guard is released; otherwise applyCarrierSelection()
                        // sees applyingMasterSelection=true and returns before populating
                        // the dependent fields (driver, vehicle, etc.).
                        applyingMasterSelection = true;
                        try {
                            savedSelector.getSelectionModel().select(value);
                        } finally {
                            applyingMasterSelection = false;
                        }
                        onSelected.accept(value);
                    }
                    popup.hide();
                    event.consume();
                }
                case ESCAPE -> {
                    popup.hide();
                    event.consume();
                }
                default -> {}
            }
        });

        editor.focusedProperty().addListener((obs, wasFocused, focused) -> {
            if (!focused) popup.hide();
        });

        suggestions.setOnMouseClicked(event -> {
            if (event.getClickCount() != 1) return;
            String value = suggestions.getSelectionModel().getSelectedItem();
            if (value == null) return;
            // Change the non-editable master selector under the guard, then
            // perform the real selection callback after the guard is released.
            // This mirrors the CompanySelector behaviour and ensures Carrier
            // selection also populates driver/vehicle details.
            applyingMasterSelection = true;
            try {
                savedSelector.getSelectionModel().select(value);
            } finally {
                applyingMasterSelection = false;
            }
            onSelected.accept(value);
            popup.hide();
            editor.requestFocus();
        });
    }

    private static ListView<String> suggestionList() {
        ListView<String> list = new ListView<>();
        list.setPrefHeight(180);
        list.setMaxHeight(180);
        list.setFocusTraversable(false);
        list.getStyleClass().add("company-suggestion-list");
        return list;
    }

    private static void showSuggestions(TextField editor, Popup popup, ListView<String> suggestions) {
        if (suggestions.getItems().isEmpty() || !editor.isFocused() || editor.getScene() == null
                || editor.getScene().getWindow() == null) return;
        double width = Math.max(editor.getWidth(), 300);
        suggestions.setPrefWidth(width);
        suggestions.setMinWidth(width);
        suggestions.setMaxWidth(width);
        var bounds = editor.localToScreen(editor.getBoundsInLocal());
        if (bounds == null) return;
        if (!popup.isShowing()) popup.show(editor, bounds.getMinX(), bounds.getMaxY());
    }

    private void loadSavedCarriers() {
        carrierMasterValues.clear();
        carrierMasterValues.addAll(SavedDataService.findCarriers(null, true).stream()
                .map(SavedDataService.CarrierRecord::name).filter(v -> v != null && !v.isBlank()).toList());
        savedCarrier.getItems().setAll(java.util.stream.Stream.concat(
                java.util.stream.Stream.of(SELECT_CARRIER), carrierMasterValues.stream()).toList());
        savedCarrier.getSelectionModel().select(SELECT_CARRIER);
    }

    private void loadSavedLocations() {
        locationMasterValues.clear();
        locationMasterValues.addAll(SavedDataService.findLocations(null, true).stream()
                .map(SavedDataService.LocationRecord::name).filter(v -> v != null && !v.isBlank()).toList());
        List<String> values = java.util.stream.Stream.concat(
                java.util.stream.Stream.of(SELECT_LOCATION), locationMasterValues.stream()).toList();
        savedOrigin.getItems().setAll(values);
        savedDestination.getItems().setAll(values);
        savedOrigin.getSelectionModel().select(SELECT_LOCATION);
        savedDestination.getSelectionModel().select(SELECT_LOCATION);
    }

    private static ComboBox<String> savedSelector(String prompt) {
        ComboBox<String> box = new ComboBox<>();
        box.setPromptText(prompt);
        box.setMaxWidth(Double.MAX_VALUE);
        box.getItems().add(prompt);
        box.getSelectionModel().select(prompt);
        // The master-data selector is deliberately selection-only. The editable
        // value is entered/searched in the text field directly below it.
        box.setEditable(false);
        box.getStyleClass().add("settings-field");
        return box;
    }

    private void selectSavedCarrier(String value) {
        selectSavedCarrier(value, true);
    }

    private void selectSavedCarrier(String value, boolean populateDetails) {
        if (value == null || value.isBlank()) {
            savedCarrier.getSelectionModel().select(SELECT_CARRIER);
            applyCarrierSelection(SELECT_CARRIER);
            return;
        }
        if (!savedCarrier.getItems().contains(value)) savedCarrier.getItems().add(value);
        savedCarrier.getSelectionModel().select(value);
        SavedDataService.CarrierRecord record = SavedDataService.findCarrierByName(value, false);
        if (record != null) {
            carrier.setText(safe(record.name()));
            if (populateDetails) {
                driver.setText(safe(record.driverName()));
                vehicle.setText(safe(record.vehicleTrailerNo()));
            }
        } else {
            carrier.setText(value);
        }
    }

    private static void selectSavedValue(ComboBox<String> box, String value) {
        String clearOption = box == null ? "" : box.getPromptText();
        if (value == null || value.isBlank()) {
            if (!clearOption.isBlank()) box.getSelectionModel().select(clearOption);
            return;
        }
        if (!box.getItems().contains(value)) box.getItems().add(value);
        box.getSelectionModel().select(value);
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

    /** Populates party fields from the historical snapshot stored on the waybill. */
    private static void populateCompany(WaybillService.CompanyData data, TextField contact, TextArea address, TextField phone, TextField email) {
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

    private static void setInformationGraphic(Alert alert) {
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
        StackPane.setAlignment(glyph, Pos.CENTER);
        circle.getChildren().add(glyph);
        alert.getDialogPane().setGraphic(circle);
    }

    private static void setDialogButtonWidth(Button button, double width) {
        button.setMinWidth(width);
        button.setPrefWidth(width);
        button.setMaxWidth(width);
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

    private static VBox card() { VBox box = new VBox(12); box.getStyleClass().add("settings-card"); box.setPadding(new Insets(16)); return box; }
    private static Label heading(String text) { Label label = new Label(text); label.getStyleClass().add("section-heading"); label.setWrapText(true); return label; }
    private static Label label(String text, String css) { Label label = new Label(text); label.getStyleClass().add(css); return label; }
    private static TextField field(String prompt, int maxLength) { TextField field = new TextField(); field.setPromptText(prompt); InputLimits.maxLength(field, maxLength); field.getStyleClass().add("settings-field"); return field; }
    private static TextArea area(String prompt, int rows) { TextArea area = new TextArea(); area.setPromptText(prompt); area.setPrefRowCount(rows); area.setWrapText(true); area.getStyleClass().add("settings-field"); return area; }
    private static VBox labeledControl(String labelText, Region control) { VBox box = new VBox(4, label(labelText, "field-label"), control); control.setMaxWidth(Double.MAX_VALUE); return box; }
    private static GridPane grid2() { GridPane grid = new GridPane(); grid.setHgap(18); grid.setVgap(10); ColumnConstraints left = new ColumnConstraints(); left.setPercentWidth(50); left.setHgrow(Priority.ALWAYS); ColumnConstraints right = new ColumnConstraints(); right.setPercentWidth(50); right.setHgrow(Priority.ALWAYS); grid.getColumnConstraints().addAll(left, right); return grid; }
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
