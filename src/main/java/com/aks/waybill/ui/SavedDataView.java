package com.aks.waybill.ui;

import com.aks.waybill.service.SavedDataService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;

/** Reusable master data used to speed up waybill entry. */
public final class SavedDataView extends AppView {
    private final TabPane tabs = new TabPane();

    public SavedDataView() {
        super("Saved Data", "Companies, carriers and locations are remembered for faster waybill entry.");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab companies = new Tab("Companies");
        companies.setContent(new CompaniesView());
        Tab carriers = new Tab("Carriers");
        carriers.setContent(new MasterTab(true));
        Tab locations = new Tab("Locations");
        locations.setContent(new MasterTab(false));
        tabs.getTabs().addAll(companies, carriers, locations);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        getChildren().add(tabs);
    }

    private static final class MasterTab extends VBox {
        private final boolean carrierMode;
        private final TextField search = new TextField();
        private final TableView<Object> table = new TableView<>();
        private final Label message = new Label();

        MasterTab(boolean carrierMode) {
            this.carrierMode = carrierMode;
            setSpacing(14);
            setPadding(new Insets(0, 0, 20, 0));
            build();
            load();
        }

        private void build() {
            HBox toolbar = new HBox(10);
            toolbar.setAlignment(Pos.CENTER_LEFT);
            search.setPromptText(carrierMode ? "Search saved carrier" : "Search saved location");
            InputLimits.maxLength(search, 400);
            HBox.setHgrow(search, Priority.ALWAYS);
            Button find = new Button("Search"); find.getStyleClass().add("secondary-button"); find.setOnAction(e -> load());
            Button clear = new Button("Clear"); clear.getStyleClass().add("secondary-button"); clear.setOnAction(e -> { search.clear(); load(); });
            Button add = new Button(carrierMode ? "＋ Add Carrier" : "＋ Add Location"); add.getStyleClass().add("primary-button"); add.setOnAction(e -> openDialog(null));
            Button edit = new Button("Edit"); edit.getStyleClass().add("secondary-button"); edit.setOnAction(e -> editSelected());
            toolbar.getChildren().addAll(search, find, clear, add, edit);

            TableColumn<Object,String> name = new TableColumn<>(carrierMode ? "Carrier Name" : "Location Name");
            name.setPrefWidth(carrierMode ? 300 : 500);
            name.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                    carrierMode ? ((SavedDataService.CarrierRecord)cell.getValue()).name() : ((SavedDataService.LocationRecord)cell.getValue()).name()));
            table.getColumns().clear();
            table.getColumns().add(name);
            if (carrierMode) {
                TableColumn<Object,String> driver = new TableColumn<>("Driver Name");
                driver.setPrefWidth(240);
                driver.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(((SavedDataService.CarrierRecord)cell.getValue()).driverName()));
                TableColumn<Object,String> vehicle = new TableColumn<>("Vehicle / Trailer No.");
                vehicle.setPrefWidth(240);
                vehicle.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(((SavedDataService.CarrierRecord)cell.getValue()).vehicleTrailerNo()));
                table.getColumns().addAll(driver, vehicle);
            }
            TableColumn<Object,String> status = new TableColumn<>("Status");
            status.setPrefWidth(120);
            status.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                    carrierMode ? (((SavedDataService.CarrierRecord)cell.getValue()).active() ? "Active" : "Inactive") : (((SavedDataService.LocationRecord)cell.getValue()).active() ? "Active" : "Inactive")));
            table.getColumns().add(status);
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            VBox.setVgrow(table, Priority.ALWAYS);
            message.getStyleClass().add("form-message");
            getChildren().addAll(toolbar, table, message);
        }

        private void load() {
            try {
                if (carrierMode) table.setItems(FXCollections.observableArrayList(SavedDataService.findCarriers(search.getText(), false).stream().map(x -> (Object)x).toList()));
                else table.setItems(FXCollections.observableArrayList(SavedDataService.findLocations(search.getText(), false).stream().map(x -> (Object)x).toList()));
                message.setText("");
            } catch (RuntimeException ex) { message.setText(ex.getMessage()); }
        }

        private void editSelected() {
            Object selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { message.setText("Select a record first."); return; }
            openDialog(selected);
        }

        private void openDialog(Object existing) {
            boolean isCarrier = carrierMode;
            String currentName = existing == null ? "" : (isCarrier ? ((SavedDataService.CarrierRecord)existing).name() : ((SavedDataService.LocationRecord)existing).name());
            String currentDriver = existing == null || !isCarrier ? "" : safe(((SavedDataService.CarrierRecord)existing).driverName());
            String currentVehicle = existing == null || !isCarrier ? "" : safe(((SavedDataService.CarrierRecord)existing).vehicleTrailerNo());
            boolean currentActive = existing == null || (isCarrier ? ((SavedDataService.CarrierRecord)existing).active() : ((SavedDataService.LocationRecord)existing).active());

            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(existing == null ? (isCarrier ? "Add Carrier" : "Add Location") : (isCarrier ? "Edit Carrier" : "Edit Location"));
            dialog.setHeaderText(isCarrier ? "Carrier details are saved together for quick waybill entry." : "Add a reusable loading or unloading location.");
            dialog.initModality(Modality.APPLICATION_MODAL);

            TextField name = new TextField(currentName); InputLimits.maxLength(name, isCarrier ? InputLimits.CARRIER : InputLimits.LOCATION);
            TextField driver = new TextField(currentDriver); InputLimits.maxLength(driver, InputLimits.DRIVER);
            TextField vehicle = new TextField(currentVehicle); InputLimits.maxLength(vehicle, InputLimits.VEHICLE);
            CheckBox active = new CheckBox("Active"); active.setSelected(currentActive);

            VBox form = new VBox(10, new Label(isCarrier ? "Carrier Name" : "Location Name"), name);
            if (isCarrier) form.getChildren().addAll(new Label("Driver Name"), driver, new Label("Vehicle / Trailer No."), vehicle);
            form.getChildren().add(active);
            form.setPrefWidth(520);
            dialog.getDialogPane().setContent(form);
            ButtonType save = new ButtonType(existing == null ? "Save" : "Update", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
            dialog.setResultConverter(button -> button == save ? save : null);

            dialog.showAndWait().ifPresent(result -> {
                if (result != save) return;
                try {
                    if (isCarrier) {
                        if (existing == null) SavedDataService.createCarrier(name.getText(), driver.getText(), vehicle.getText());
                        else SavedDataService.updateCarrier(((SavedDataService.CarrierRecord)existing).id(), name.getText(), driver.getText(), vehicle.getText(), active.isSelected());
                    } else {
                        if (existing == null) SavedDataService.createLocation(name.getText());
                        else SavedDataService.updateLocation(((SavedDataService.LocationRecord)existing).id(), name.getText(), active.isSelected());
                    }
                    load();
                } catch (RuntimeException ex) {
                    new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK).showAndWait();
                }
            });
        }

        private static String safe(String value) { return value == null ? "" : value; }
    }
}
