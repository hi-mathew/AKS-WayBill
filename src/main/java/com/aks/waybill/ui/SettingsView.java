package com.aks.waybill.ui;

import com.aks.waybill.security.SessionContext;
import com.aks.waybill.service.ReportProfileService;
import com.aks.waybill.service.SettingsService;
import com.aks.waybill.service.BackupService;
import com.aks.waybill.service.TermsConditionService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.application.Platform;
import java.nio.file.Path;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Application and issuing-company report settings. */
public final class SettingsView extends AppView {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private final TextField part1 = new TextField();
    private final TextField sequence = new TextField();
    private final TextField companyName = new TextField();
    private final TextField crNumber = new TextField();
    private final TextField vatNumber = new TextField();
    private final TextArea address = new TextArea();
    private final TextField phone = new TextField();
    private final TextField email = new TextField();
    private final ComboBox<Integer> pageSize = new ComboBox<>();
    { InputLimits.maxLength(part1, InputLimits.PART1); InputLimits.numeric(sequence, InputLimits.SEQUENCE, 0); InputLimits.maxLength(companyName, InputLimits.COMPANY_NAME); InputLimits.maxLength(crNumber, InputLimits.CR_NUMBER); InputLimits.maxLength(vatNumber, InputLimits.VAT_NUMBER); InputLimits.maxLength(phone, InputLimits.PHONE); InputLimits.maxLength(email, InputLimits.EMAIL); }
    private final Label numberingMessage = new Label();
    private final Label profileMessage = new Label();
    private final Label paginationMessage = new Label();
    private final Label preview = new Label();

    public SettingsView() {
        super("Settings", "Configure waybill numbering and the issuing company information used on reports.");
        VBox content = new VBox(18, numberingCard(), profileCard(), termsCard(), paginationCard(), backupCard());
        content.setMaxWidth(900);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true); scroll.getStyleClass().add("content-scroll");
        getChildren().add(scroll); VBox.setVgrow(scroll, Priority.ALWAYS);
        loadAll();
    }

    private VBox numberingCard() {
        VBox card=card();
        Label title=new Label("Waybill Number Configuration"); title.getStyleClass().add("settings-card-title");
        Label desc=new Label("Part 1 and the running sequence are application-level settings. Part 2 is now taken from the logged-in user's User Code in User Management."); desc.setWrapText(true); desc.getStyleClass().add("settings-card-description");
        GridPane form=new GridPane(); form.setHgap(18); form.setVgap(14);
        addField(form,0,"Part 1",part1,"Example: AKS"); addField(form,1,"Next Sequence Number",sequence,"Example: 1001");
        Label cap=new Label("Preview"); cap.getStyleClass().add("settings-preview-caption"); preview.getStyleClass().add("settings-preview"); preview.setMaxWidth(Double.MAX_VALUE);
        part1.textProperty().addListener((o,a,b)->updatePreview()); sequence.textProperty().addListener((o,a,b)->updatePreview());
        HBox buttons=new HBox(10); buttons.setAlignment(Pos.CENTER_RIGHT);
        Button reset=secondary("Reset"); reset.setOnAction(e->confirmResetNumbering()); Button save=primary("Save Changes"); save.setOnAction(e->saveNumbering()); buttons.getChildren().addAll(reset,save);
        numberingMessage.getStyleClass().add("settings-message"); numberingMessage.setWrapText(true);
        card.getChildren().addAll(title,desc,form,cap,preview,numberingMessage,buttons); return card;
    }

    private VBox profileCard() {
        VBox card=card();
        Label title=new Label("Report / Company Profile"); title.getStyleClass().add("settings-card-title");
        Label desc=new Label("These details belong to AKS Global Logistics and are used for the report letterhead. They are separate from client companies in the Companies master."); desc.setWrapText(true); desc.getStyleClass().add("settings-card-description");
        GridPane form=new GridPane(); form.setHgap(18); form.setVgap(14);
        addField(form,0,"Company Name",companyName,"AKS GLOBAL LOGISTICS CO.");
        addField(form,1,"CR Number",crNumber,"1010558527");
        addField(form,2,"VAT Number",vatNumber,"311676840600003");
        addField(form,3,"Phone Number",phone,"Company phone number");
        addField(form,4,"Email Address",email,"Company email address");
        address.setPromptText("Company address"); address.setPrefRowCount(3); address.setWrapText(true); address.setMaxWidth(Double.MAX_VALUE); address.getStyleClass().add("settings-field"); GridPane.setHgrow(address,Priority.ALWAYS);
        Label al=new Label("Address"); al.getStyleClass().add("field-label"); form.add(al,0,5); form.add(address,1,5);
        Label logo=new Label("Report Logo"); logo.getStyleClass().add("field-label"); Label logoValue=new Label("AKS Global Logistics report logo (application resource)"); logoValue.getStyleClass().add("settings-note"); form.add(logo,0,6); form.add(logoValue,1,6);
        HBox buttons=new HBox(10); buttons.setAlignment(Pos.CENTER_RIGHT); Button reset=secondary("Reset"); reset.setOnAction(e->confirmResetProfile()); Button save=primary("Save Company Profile"); save.setOnAction(e->saveProfile()); buttons.getChildren().addAll(reset,save);
        profileMessage.getStyleClass().add("settings-message"); profileMessage.setWrapText(true);
        card.getChildren().addAll(title,desc,form,profileMessage,buttons); return card;
    }

    private VBox termsCard() {
        VBox card=card();
        Label title=new Label("Terms & Conditions"); title.getStyleClass().add("settings-card-title");
        Label desc=new Label("Manage the clauses printed on generated waybills. Changes are used for new report generation without requiring an application rebuild."); desc.setWrapText(true); desc.getStyleClass().add("settings-card-description");
        TableView<TermsConditionService.Clause> table=new TableView<>();
        table.setPrefHeight(320);
        table.setMinHeight(260);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Keep the identifying columns visible even when the Settings screen is
        // resized/re-laid out inside the outer ScrollPane.  Using only prefWidth
        // with the FLEX_LAST_COLUMN policy can cause JavaFX to collapse the
        // non-last columns during a constrained layout pass, leaving only Text
        // visible.  Explicit min/pref widths make the grid stable.
        TableColumn<TermsConditionService.Clause,String> no=new TableColumn<>("No.");
        no.setMinWidth(55); no.setPrefWidth(65); no.setMaxWidth(75);
        no.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(String.valueOf(d.getValue().clauseNumber())));

        TableColumn<TermsConditionService.Clause,String> clause=new TableColumn<>("Clause");
        clause.setMinWidth(160); clause.setPrefWidth(190);
        clause.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().title()));

        TableColumn<TermsConditionService.Clause,String> text=new TableColumn<>("Text");
        text.setMinWidth(300);
        text.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().text()));

        TableColumn<TermsConditionService.Clause,String> active=new TableColumn<>("Status");
        active.setMinWidth(85); active.setPrefWidth(95); active.setMaxWidth(110);
        active.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().active()?"Active":"Inactive"));
        table.getColumns().setAll(no,clause,text,active);
        Runnable reload=()->table.getItems().setAll(TermsConditionService.findAll()); reload.run();
        Button add=primary("Add Clause"); add.setOnAction(e->editTermsClause(table,null,reload));
        Button edit=secondary("Edit"); edit.setOnAction(e->{var x=table.getSelectionModel().getSelectedItem();if(x==null){showSimpleError("Select a clause first.");return;}editTermsClause(table,x,reload);});
        Button up=secondary("Move Up"); up.setOnAction(e->{var x=table.getSelectionModel().getSelectedItem();if(x!=null){TermsConditionService.move(x.id(),true);reload.run();table.getSelectionModel().select(x);}});
        Button down=secondary("Move Down"); down.setOnAction(e->{var x=table.getSelectionModel().getSelectedItem();if(x!=null){TermsConditionService.move(x.id(),false);reload.run();table.getSelectionModel().select(x);}});
        Button delete=secondary("Delete"); delete.setOnAction(e->{var x=table.getSelectionModel().getSelectedItem();if(x==null){showSimpleError("Select a clause first.");return;}Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Delete clause "+x.clauseNumber()+"? This will remove it from future reports.",ButtonType.OK,ButtonType.CANCEL);a.setTitle("Delete Terms & Conditions Clause");if(a.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK){try{TermsConditionService.delete(x.id());reload.run();}catch(Exception ex){showSimpleError(ex.getMessage());}}});
        HBox buttons=new HBox(8,add,edit,up,down,delete); buttons.setAlignment(Pos.CENTER_RIGHT);
        card.getChildren().addAll(title,desc,table,buttons); return card;
    }

    private void editTermsClause(TableView<TermsConditionService.Clause> table, TermsConditionService.Clause existing, Runnable reload) {
        Dialog<ButtonType> dialog=new Dialog<>(); dialog.setTitle(existing==null?"Add Terms & Conditions Clause":"Edit Terms & Conditions Clause");
        TextField number=new TextField(existing==null?"":String.valueOf(existing.clauseNumber())); TextField title=new TextField(existing==null?"":existing.title()); TextArea text=new TextArea(existing==null?"":existing.text()); CheckBox active=new CheckBox("Active"); active.setSelected(existing==null||existing.active());
        InputLimits.numeric(number,6,0); InputLimits.maxLength(title,300); InputLimits.maxLength(text,3000); text.setWrapText(true); text.setPrefRowCount(7);
        VBox form=new VBox(8,new Label("Clause Number"),number,new Label("Clause Title"),title,new Label("Clause Text"),text,active); form.setPrefWidth(650); dialog.getDialogPane().setContent(form);
        ButtonType save=new ButtonType(existing==null?"Add":"Save",ButtonBar.ButtonData.OK_DONE); dialog.getDialogPane().getButtonTypes().addAll(save,ButtonType.CANCEL);
        dialog.setResultConverter(b->{if(b!=save)return null;try{int n=Integer.parseInt(number.getText().trim());if(existing==null)TermsConditionService.create(title.getText(),text.getText(),active.isSelected());else TermsConditionService.update(existing.id(),n,title.getText(),text.getText(),existing.displayOrder(),active.isSelected());reload.run();return b;}catch(Exception ex){showSimpleError(ex.getMessage());return null;}}); dialog.showAndWait();
    }

    private void showSimpleError(String message){new Alert(Alert.AlertType.ERROR,message==null?"Operation failed.":message,ButtonType.OK).showAndWait();}

    private VBox paginationCard() {
        VBox card=card();
        Label title=new Label("List Pagination"); title.getStyleClass().add("settings-card-title");
        Label desc=new Label("Choose how many records are shown per page in paginated lists such as Saved Waybills and Companies."); desc.setWrapText(true); desc.getStyleClass().add("settings-card-description");
        pageSize.getItems().setAll(10,20,50,100); pageSize.setPrefWidth(160); pageSize.getStyleClass().add("settings-field");
        Button save=primary("Save Page Size"); save.setOnAction(e->{try{SettingsService.savePageSize(pageSize.getValue()); setMessage(paginationMessage,"Page size saved. It will apply when list screens are opened or refreshed.",false);}catch(Exception ex){setMessage(paginationMessage,message(ex),true);}});
        paginationMessage.getStyleClass().add("settings-message"); paginationMessage.setWrapText(true);
        HBox row=new HBox(12,new Label("Rows per page"),pageSize,save); row.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().addAll(title,desc,row,paginationMessage); return card;
    }

    private VBox backupCard() {
        VBox card=card();
        Label title=new Label("Database Backup & Restore"); title.getStyleClass().add("settings-card-title");
        Label desc=new Label("Back up the local SQLite database to a safe location, or restore from a previous backup. A restore also creates a safety copy of the current database and requires an application restart."); desc.setWrapText(true); desc.getStyleClass().add("settings-card-description");
        Button backup=primary("Backup Now"); backup.setOnAction(e->backupDatabase());
        Button restore=secondary("Restore Backup"); restore.setOnAction(e->restoreDatabase());
        card.getChildren().addAll(title,desc,new HBox(10,backup,restore)); return card;
    }

    private void backupDatabase() {
        FileChooser chooser=new FileChooser(); chooser.setTitle("Save W.A.S.P Database Backup"); chooser.setInitialFileName(BackupService.defaultBackupPath().getFileName().toString()); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite Database (*.db)","*.db"));
        Window owner=getScene()==null?null:getScene().getWindow(); java.io.File file=chooser.showSaveDialog(owner); if(file==null)return;
        try{Path saved=BackupService.backupTo(file.toPath()); new Alert(Alert.AlertType.INFORMATION,"Database backup created successfully:\n"+saved,ButtonType.OK).showAndWait();}catch(Exception ex){new Alert(Alert.AlertType.ERROR,"Backup failed: "+message(ex),ButtonType.OK).showAndWait();}
    }

    private void restoreDatabase() {
        FileChooser chooser=new FileChooser(); chooser.setTitle("Select W.A.S.P Database Backup"); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite Database (*.db)","*.db"));
        Window owner=getScene()==null?null:getScene().getWindow(); java.io.File file=chooser.showOpenDialog(owner); if(file==null)return;
        Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,"Restore this database backup? The current database will first be preserved as a safety copy, and the application will close after restore.",ButtonType.OK,ButtonType.CANCEL); confirm.setHeaderText("Restore Database"); if(confirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
        try{BackupService.restoreFrom(file.toPath());new Alert(Alert.AlertType.INFORMATION,"Database restored successfully. The application will now close. Start W.A.S.P again to continue.",ButtonType.OK).showAndWait();Platform.exit();}catch(Exception ex){new Alert(Alert.AlertType.ERROR,"Restore failed: "+message(ex),ButtonType.OK).showAndWait();}
    }

    private VBox card(){VBox v=new VBox(18);v.getStyleClass().add("settings-card");v.setPadding(new Insets(24));return v;}
    private Button primary(String s){Button b=new Button(s);b.getStyleClass().add("primary-button");b.setDefaultButton(true);return b;}
    private Button secondary(String s){Button b=new Button(s);b.getStyleClass().add("secondary-button");return b;}
    private void addField(GridPane g,int row,String label,TextField f,String prompt){Label l=new Label(label);l.getStyleClass().add("field-label");f.setPromptText(prompt);f.setPrefHeight(40);f.setMaxWidth(Double.MAX_VALUE);f.getStyleClass().add("settings-field");GridPane.setHgrow(f,Priority.ALWAYS);g.add(l,0,row);g.add(f,1,row);}
    private void loadAll(){loadNumbering();loadProfile(); pageSize.setValue(SettingsService.getPageSize());}
    private void confirmResetNumbering(){ Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Reset the numbering fields to the values currently stored in the database? Unsaved changes on this screen will be lost.",ButtonType.OK,ButtonType.CANCEL); if(a.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK) loadNumbering(); }
    private void confirmResetProfile(){ Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Reset the company profile fields to the values currently stored in the database? Unsaved changes on this screen will be lost.",ButtonType.OK,ButtonType.CANCEL); if(a.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK) loadProfile(); }
    private void loadNumbering(){try{var s=SettingsService.getWaybillNumberSettings();part1.setText(s.part1());sequence.setText(String.valueOf(s.nextSequence()));setMessage(numberingMessage,"Numbering settings loaded.",false);updatePreview();}catch(Exception e){setMessage(numberingMessage,message(e),true);}}
    private void saveNumbering(){try{SettingsService.saveWaybillNumberSettings(part1.getText(),Long.parseLong(sequence.getText().trim()));loadNumbering();setMessage(numberingMessage,"Waybill numbering settings saved.",false);}catch(NumberFormatException e){setMessage(numberingMessage,"Next sequence number must be a valid whole number.",true);}catch(Exception e){setMessage(numberingMessage,message(e),true);}}
    private void updatePreview(){try{String p=part1.getText().isBlank()?"AKS":part1.getText().trim().toUpperCase();String c=SessionContext.requireUserCode();String n=sequence.getText().isBlank()?"1001":sequence.getText().trim();preview.setText(p+"/"+c+"/"+DATE.format(LocalDate.now())+"/"+n);}catch(Exception e){preview.setText("Log in with a configured User Code to preview the number.");}}
    private void loadProfile(){try{var p=ReportProfileService.get();companyName.setText(p.companyName());crNumber.setText(p.crNumber());vatNumber.setText(p.vatNumber());address.setText(p.address());phone.setText(p.phone());email.setText(p.email());setMessage(profileMessage,"Company profile loaded.",false);}catch(Exception e){setMessage(profileMessage,message(e),true);}}
    private void saveProfile(){try{ReportProfileService.save(companyName.getText(),crNumber.getText(),vatNumber.getText(),address.getText(),phone.getText(),email.getText());setMessage(profileMessage,"Report company profile saved successfully.",false);}catch(Exception e){setMessage(profileMessage,message(e),true);}}
    private void setMessage(Label l,String text,boolean error){l.setText(text);l.getStyleClass().removeAll("success-message","error-message");l.getStyleClass().add(error?"error-message":"success-message");}
    private String message(Exception e){return e.getMessage()==null?"Operation failed.":e.getMessage();}
}
