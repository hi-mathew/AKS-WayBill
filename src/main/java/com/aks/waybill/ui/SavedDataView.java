package com.aks.waybill.ui;

import com.aks.waybill.service.SavedDataService;
import com.aks.waybill.security.SessionContext;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import java.util.List;

/** Reusable master data used to speed up waybill entry. */
public final class SavedDataView extends AppView {
    private final TabPane tabs = new TabPane();

    public SavedDataView() {
        super("Saved Data", "Companies, carriers and locations are remembered for faster waybill entry.");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("wasp-master-tabs");
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
        private final Label resultInfo = new Label();
        private final Label pageInfo = new Label();
        private final HBox pageButtons = new HBox(5);
        private int currentPage = 0;
        private int totalPages = 1;
        private long totalRows = 0;

        MasterTab(boolean carrierMode) {
            this.carrierMode = carrierMode; setSpacing(14); setPadding(new Insets(0,0,20,0)); build(); loadPage(0);
        }

        private void build() {
            HBox toolbar=new HBox(10); toolbar.setAlignment(Pos.CENTER_LEFT);
            search.setPromptText(carrierMode?"Search saved carrier":"Search saved location"); InputLimits.maxLength(search,400); HBox.setHgrow(search,Priority.ALWAYS); search.setOnAction(e->loadPage(0));
            Button find=new Button("Search"); find.getStyleClass().add("secondary-button"); find.setOnAction(e->loadPage(0));
            Button clear=new Button("Clear"); clear.getStyleClass().add("secondary-button"); clear.setOnAction(e->{search.clear();loadPage(0);});
            Button add=new Button(carrierMode?"＋ Add Carrier":"＋ Add Location"); add.getStyleClass().add("primary-button"); add.setOnAction(e->openDialog(null));
            Button edit=new Button("Edit"); edit.getStyleClass().add("secondary-button"); edit.setOnAction(e->editSelected());
            Button delete=new Button("Delete"); delete.getStyleClass().add("secondary-button"); delete.setVisible(SessionContext.isAdmin()); delete.setManaged(SessionContext.isAdmin()); delete.setOnAction(e->deleteSelected());
            toolbar.getChildren().addAll(search,find,clear,add,edit,delete);

            TableColumn<Object,String> name=new TableColumn<>(carrierMode?"Carrier Name":"Location Name"); name.setPrefWidth(carrierMode?300:500);
            name.setCellValueFactory(cell->new javafx.beans.property.SimpleStringProperty(carrierMode?((SavedDataService.CarrierRecord)cell.getValue()).name():((SavedDataService.LocationRecord)cell.getValue()).name()));
            table.getColumns().clear(); table.getColumns().add(name);
            if(carrierMode){
                TableColumn<Object,String> driver=new TableColumn<>("Driver Name"); driver.setPrefWidth(240); driver.setCellValueFactory(cell->new javafx.beans.property.SimpleStringProperty(((SavedDataService.CarrierRecord)cell.getValue()).driverName()));
                TableColumn<Object,String> vehicle=new TableColumn<>("Vehicle / Trailer No."); vehicle.setPrefWidth(240); vehicle.setCellValueFactory(cell->new javafx.beans.property.SimpleStringProperty(((SavedDataService.CarrierRecord)cell.getValue()).vehicleTrailerNo()));
                table.getColumns().addAll(driver,vehicle);
            }
            TableColumn<Object,String> status=new TableColumn<>("Status"); status.setPrefWidth(120); status.setCellValueFactory(cell->new javafx.beans.property.SimpleStringProperty(carrierMode?(((SavedDataService.CarrierRecord)cell.getValue()).active()?"Active":"Inactive"):(((SavedDataService.LocationRecord)cell.getValue()).active()?"Active":"Inactive")));
            table.getColumns().add(status); table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN); table.setPlaceholder(new Label(carrierMode?"No saved carriers found.":"No saved locations found."));
            table.setRowFactory(tv->{TableRow<Object> row=new TableRow<>();row.setOnMouseClicked(event->{if(event.getClickCount()==2&&!row.isEmpty())openDialog(row.getItem());});return row;});

            resultInfo.getStyleClass().add("card-description"); pageInfo.getStyleClass().add("card-description"); pageButtons.setAlignment(Pos.CENTER);
            Button previous=new Button("‹"); previous.getStyleClass().add("secondary-button"); previous.setOnAction(e->loadPage(currentPage-1));
            Button next=new Button("›"); next.getStyleClass().add("secondary-button"); next.setOnAction(e->loadPage(currentPage+1));
            Region spacer=new Region(); HBox.setHgrow(spacer,Priority.ALWAYS);
            HBox bottom=new HBox(10,resultInfo,spacer,pageInfo); bottom.setAlignment(Pos.CENTER_LEFT);
            HBox paging=new HBox(12,previous,pageButtons,next); paging.setAlignment(Pos.CENTER);
            message.getStyleClass().add("form-message");
            getChildren().addAll(toolbar,table,message,bottom,paging);
        }

        private void loadPage(int requestedPage){
            try{
                int size=Math.max(1,com.aks.waybill.service.SettingsService.getPageSize());
                if(carrierMode){
                    List<SavedDataService.CarrierRecord> all=SavedDataService.findCarriers(search.getText(),false); totalRows=all.size(); totalPages=(int)Math.max(1,(totalRows+size-1)/size); currentPage=Math.max(0,Math.min(requestedPage,totalPages-1));
                    int from=(int)((long)currentPage*size),to=Math.min(all.size(),from+size); table.setItems(FXCollections.observableArrayList(all.subList(from,to).stream().map(x->(Object)x).toList()));
                }else{
                    List<SavedDataService.LocationRecord> all=SavedDataService.findLocations(search.getText(),false); totalRows=all.size(); totalPages=(int)Math.max(1,(totalRows+size-1)/size); currentPage=Math.max(0,Math.min(requestedPage,totalPages-1));
                    int from=(int)((long)currentPage*size),to=Math.min(all.size(),from+size); table.setItems(FXCollections.observableArrayList(all.subList(from,to).stream().map(x->(Object)x).toList()));
                }
                fitTableHeight(table,table.getItems().size(),size,42); long start=totalRows==0?0:(long)currentPage*size+1; long end=Math.min(totalRows,(long)(currentPage+1)*size);
                resultInfo.setText("Showing "+start+"–"+end+" of "+totalRows); pageInfo.setText("Page "+(currentPage+1)+" of "+totalPages); buildPages(); message.setText("");
            }catch(RuntimeException ex){message.setText(ex.getMessage());}
        }

        private void buildPages(){
            pageButtons.getChildren().clear(); int start=Math.max(0,currentPage-2),end=Math.min(totalPages-1,start+4); start=Math.max(0,end-4);
            for(int i=start;i<=end;i++){final int page=i;Button b=new Button(String.valueOf(i+1));b.getStyleClass().add(i==currentPage?"nav-selected":"secondary-button");b.setOnAction(e->loadPage(page));pageButtons.getChildren().add(b);}
        }

        private void editSelected(){Object selected=table.getSelectionModel().getSelectedItem();if(selected==null){message.setText("Select a record first.");return;}openDialog(selected);}
        private void deleteSelected(){Object selected=table.getSelectionModel().getSelectedItem();if(selected==null){message.setText("Select a record first.");return;}String name=carrierMode?((SavedDataService.CarrierRecord)selected).name():((SavedDataService.LocationRecord)selected).name();Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Delete saved "+(carrierMode?"carrier":"location")+" \""+name+"\"? This does not alter historical waybill values.",ButtonType.OK,ButtonType.CANCEL);a.setTitle("Delete Saved Data");if(a.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;try{if(carrierMode)SavedDataService.deleteCarrier(((SavedDataService.CarrierRecord)selected).id());else SavedDataService.deleteLocation(((SavedDataService.LocationRecord)selected).id());loadPage(currentPage);}catch(RuntimeException ex){message.setText(ex.getMessage());}}

        private void openDialog(Object existing){
            boolean isCarrier=carrierMode; String currentName=existing==null?"":(isCarrier?((SavedDataService.CarrierRecord)existing).name():((SavedDataService.LocationRecord)existing).name()); String currentDriver=existing==null||!isCarrier?"":safe(((SavedDataService.CarrierRecord)existing).driverName()); String currentVehicle=existing==null||!isCarrier?"":safe(((SavedDataService.CarrierRecord)existing).vehicleTrailerNo()); boolean currentActive=existing==null||(isCarrier?((SavedDataService.CarrierRecord)existing).active():((SavedDataService.LocationRecord)existing).active());
            Dialog<ButtonType> dialog=new Dialog<>();dialog.setTitle(existing==null?(isCarrier?"Add Carrier":"Add Location"):(isCarrier?"Edit Carrier":"Edit Location"));dialog.setHeaderText(isCarrier?"Carrier details are saved together for quick waybill entry.":"Add a reusable loading or unloading location.");dialog.initModality(Modality.APPLICATION_MODAL);
            TextField name=new TextField(currentName);InputLimits.maxLength(name,isCarrier?InputLimits.CARRIER:InputLimits.LOCATION);TextField driver=new TextField(currentDriver);InputLimits.maxLength(driver,InputLimits.DRIVER);TextField vehicle=new TextField(currentVehicle);InputLimits.maxLength(vehicle,InputLimits.VEHICLE);CheckBox active=new CheckBox("Active");active.setSelected(currentActive);
            VBox form=new VBox(10,new Label(isCarrier?"Carrier Name":"Location Name"),name);if(isCarrier)form.getChildren().addAll(new Label("Driver Name"),driver,new Label("Vehicle / Trailer No."),vehicle);form.getChildren().add(active);form.setPrefWidth(520);dialog.getDialogPane().setContent(form);ButtonType save=new ButtonType(existing==null?"Save":"Update",ButtonBar.ButtonData.OK_DONE);dialog.getDialogPane().getButtonTypes().addAll(save,ButtonType.CANCEL);dialog.setResultConverter(button->button==save?save:null);
            dialog.showAndWait().ifPresent(result->{if(result!=save)return;try{if(isCarrier){if(existing==null)SavedDataService.createCarrier(name.getText(),driver.getText(),vehicle.getText(),active.isSelected());else SavedDataService.updateCarrier(((SavedDataService.CarrierRecord)existing).id(),name.getText(),driver.getText(),vehicle.getText(),active.isSelected());}else{if(existing==null)SavedDataService.createLocation(name.getText(),active.isSelected());else SavedDataService.updateLocation(((SavedDataService.LocationRecord)existing).id(),name.getText(),active.isSelected());}loadPage(currentPage);}catch(RuntimeException ex){new Alert(Alert.AlertType.ERROR,ex.getMessage(),ButtonType.OK).showAndWait();}});
        }
        private static String safe(String value){return value==null?"":value;}
    }
}
