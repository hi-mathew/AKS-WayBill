package com.aks.waybill.ui;

import com.aks.waybill.security.SessionContext;
import com.aks.waybill.service.ReportProfileService;
import com.aks.waybill.service.SettingsService;
import com.aks.waybill.service.BackupService;
import com.aks.waybill.service.TermsConditionService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private final CheckBox backupRetentionEnabled = new CheckBox("Automatically remove older regular backups");
    private final ComboBox<Integer> backupRetentionCount = new ComboBox<>();
    private final Label backupRetentionMessage = new Label();
    { InputLimits.maxLength(part1, InputLimits.PART1); InputLimits.numeric(sequence, InputLimits.SEQUENCE, 0); InputLimits.maxLength(companyName, InputLimits.COMPANY_NAME); InputLimits.maxLength(crNumber, InputLimits.CR_NUMBER); InputLimits.maxLength(vatNumber, InputLimits.VAT_NUMBER); InputLimits.maxLength(phone, InputLimits.PHONE); InputLimits.maxLength(email, InputLimits.EMAIL); }
    private final Label numberingMessage = new Label();
    private final Label profileMessage = new Label();
    private final Label paginationMessage = new Label();
    private final Label preview = new Label();

    public SettingsView() {
        super("Settings", "Manage waybill numbering, company information, terms and conditions, pagination, and application data settings.");

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("wasp-master-tabs");

        Tab numbering = new Tab("Waybill Numbering");
        numbering.setContent(numberingCard());
        Tab profile = new Tab("Company Profile");
        profile.setContent(profileCard());
        Tab terms = new Tab("Terms & Conditions");
        terms.setContent(termsCard());
        Tab pagination = new Tab("Pagination");
        pagination.setContent(paginationCard());
        Tab backup = new Tab("Backup & Restore");
        backup.setContent(backupCard());

        tabs.getTabs().addAll(numbering, profile, terms, pagination, backup);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        getChildren().add(tabs);
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
        Label desc=new Label("Manage the clauses printed on generated waybills. Clause numbers follow the current display order automatically. Double-click a clause to edit it."); desc.setWrapText(true); desc.getStyleClass().add("settings-card-description");
        TableView<TermsConditionService.Clause> table=new TableView<>();
        fitTableHeight(table, 0, 10, 38);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

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

        Runnable reload=()->{ table.getItems().setAll(TermsConditionService.findAll()); fitTableHeight(table, table.getItems().size(), 10, 38); };
        reload.run();

        table.setRowFactory(tv -> {
            TableRow<TermsConditionService.Clause> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    editTermsClause(table, row.getItem(), reload);
                }
            });
            return row;
        });

        Button add=primary("Add Clause");
        add.setOnAction(e->editTermsClause(table,null,reload));
        Button edit=secondary("Edit");
        edit.setOnAction(e->{var x=table.getSelectionModel().getSelectedItem();if(x==null){showSimpleError("Select a clause first.");return;}editTermsClause(table,x,reload);});
        Button up=secondary("Move Up");
        up.setOnAction(e->moveClause(table,true,reload));
        Button down=secondary("Move Down");
        down.setOnAction(e->moveClause(table,false,reload));
        Button delete=secondary("Delete");
        delete.setOnAction(e->{var x=table.getSelectionModel().getSelectedItem();if(x==null){showSimpleError("Select a clause first.");return;}Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Delete clause "+x.clauseNumber()+"? This will remove it from future reports.",ButtonType.OK,ButtonType.CANCEL);a.setTitle("Delete Terms & Conditions Clause");if(a.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK){try{TermsConditionService.delete(x.id());reload.run();}catch(Exception ex){showSimpleError(ex.getMessage());}}});
        HBox buttons=new HBox(8,add,edit,up,down,delete); buttons.setAlignment(Pos.CENTER_RIGHT);
        card.getChildren().addAll(title,desc,table,buttons); return card;
    }

    private void moveClause(TableView<TermsConditionService.Clause> table, boolean up, Runnable reload) {
        TermsConditionService.Clause selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showSimpleError("Select a clause first.");
            return;
        }
        long selectedId = selected.id();
        try {
            TermsConditionService.move(selectedId, up);
            reload.run();
            for (int i = 0; i < table.getItems().size(); i++) {
                if (table.getItems().get(i).id() == selectedId) {
                    table.getSelectionModel().select(i);
                    table.scrollTo(i);
                    break;
                }
            }
        } catch (RuntimeException ex) {
            showSimpleError(ex.getMessage());
        }
    }

    private void editTermsClause(TableView<TermsConditionService.Clause> table, TermsConditionService.Clause existing, Runnable reload) {
        Dialog<ButtonType> dialog=new Dialog<>(); dialog.setTitle(existing==null?"Add Terms & Conditions Clause":"Edit Terms & Conditions Clause");
        TextField title=new TextField(existing==null?"":existing.title()); TextArea text=new TextArea(existing==null?"":existing.text()); CheckBox active=new CheckBox("Active"); active.setSelected(existing==null||existing.active());
        InputLimits.maxLength(title,300); InputLimits.maxLength(text,3000); text.setWrapText(true); text.setPrefRowCount(7);
        Label numberValue = new Label(existing == null ? "Assigned automatically when saved" : "Clause " + existing.clauseNumber());
        numberValue.getStyleClass().add("settings-note");
        VBox form=new VBox(8,new Label("Clause Number"),numberValue,new Label("Clause Title"),title,new Label("Clause Text"),text,active); form.setPrefWidth(650); dialog.getDialogPane().setContent(form);
        ButtonType save=new ButtonType(existing==null?"Add":"Save",ButtonBar.ButtonData.OK_DONE); dialog.getDialogPane().getButtonTypes().addAll(save,ButtonType.CANCEL);
        dialog.setResultConverter(b->{if(b!=save)return null;try{if(existing==null)TermsConditionService.create(title.getText(),text.getText(),active.isSelected());else TermsConditionService.update(existing.id(),title.getText(),text.getText(),existing.displayOrder(),active.isSelected());reload.run();return b;}catch(Exception ex){showSimpleError(ex.getMessage());return null;}}); dialog.showAndWait();
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

    private TableView<BackupService.BackupInfo> backupTable;
    private Button backupNowButton;

    private VBox backupCard() {
        VBox card=card();
        Label title=new Label("Database Backup & Restore"); title.getStyleClass().add("settings-card-title");
        Label desc=new Label("Create backups in the W.A.S.P backup folder, review previous backups, or restore a selected backup. A restore first creates a safety copy of the current database and then closes the application."); desc.setWrapText(true); desc.getStyleClass().add("settings-card-description");

        backupTable=new TableView<>();
        backupTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        backupTable.setPlaceholder(new Label("No backups have been created yet."));
        fitTableHeight(backupTable, 0, 6, 38);

        TableColumn<BackupService.BackupInfo,String> name=new TableColumn<>("Backup File");
        name.setMinWidth(300); name.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().path().getFileName().toString()));
        TableColumn<BackupService.BackupInfo,String> type=new TableColumn<>("Type");
        type.setMinWidth(120); type.setMaxWidth(150); type.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().type()));
        TableColumn<BackupService.BackupInfo,String> performedBy=new TableColumn<>("Performed By");
        performedBy.setMinWidth(120); performedBy.setMaxWidth(150);
        performedBy.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().performedBy()));
        TableColumn<BackupService.BackupInfo,String> date=new TableColumn<>("Created / Modified");
        date.setMinWidth(170); date.setMaxWidth(200);
        date.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(
                DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss").withZone(ZoneId.systemDefault()).format(d.getValue().modified())));
        TableColumn<BackupService.BackupInfo,String> size=new TableColumn<>("Size");
        size.setMinWidth(90); size.setMaxWidth(110); size.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(formatBytes(d.getValue().size())));
        backupTable.getColumns().setAll(name,type,performedBy,date,size);
        refreshBackups();

        backupRetentionEnabled.setSelected(SettingsService.isBackupRetentionEnabled());
        backupRetentionCount.getItems().setAll(5, 10, 20, 50, 100);
        backupRetentionCount.setValue(SettingsService.getBackupRetentionCount());
        backupRetentionCount.setPrefWidth(110);
        backupRetentionCount.setDisable(!backupRetentionEnabled.isSelected());
        backupRetentionEnabled.selectedProperty().addListener((obs, oldValue, newValue) -> backupRetentionCount.setDisable(!newValue));
        backupRetentionMessage.getStyleClass().add("settings-message");
        Button saveRetention=secondary("Save Retention Settings");
        saveRetention.setOnAction(e -> saveBackupRetention());
        HBox retentionRow=new HBox(12, backupRetentionEnabled, new Label("Keep latest"), backupRetentionCount, new Label("regular backups"), saveRetention);
        retentionRow.setAlignment(Pos.CENTER_LEFT);
        retentionRow.setPadding(new Insets(4,0,4,0));

        Button backup=primary("Backup Now"); backupNowButton=backup; backup.setOnAction(e->backupDatabase());
        Button restore=secondary("Restore Selected"); restore.setOnAction(e->restoreSelectedBackup());
        Button delete=secondary("Delete Selected"); delete.setOnAction(e->deleteSelectedBackup());
        Button open=secondary("Open Backup Folder"); open.setOnAction(e->openBackupFolder());
        Button refresh=secondary("Refresh"); refresh.setOnAction(e->refreshBackups());
        HBox buttons=new HBox(8,backup,restore,delete,open,refresh); buttons.setAlignment(Pos.CENTER_RIGHT); buttons.getStyleClass().add("backup-actions");
        card.getChildren().addAll(title,desc,retentionRow,backupRetentionMessage,backupTable,buttons); return card;
    }

    private void saveBackupRetention() {
        try {
            SettingsService.saveBackupRetention(backupRetentionEnabled.isSelected(), backupRetentionCount.getValue());
            if (backupRetentionEnabled.isSelected()) {
                int deleted = BackupService.cleanupOldBackups(backupRetentionCount.getValue());
                refreshBackups();
                setMessage(backupRetentionMessage, deleted == 0 ? "Retention settings saved. No old backups needed to be removed." : "Retention settings saved. " + deleted + " old backup(s) removed.", false);
            } else {
                setMessage(backupRetentionMessage, "Retention settings saved. Older backups will not be removed automatically.", false);
            }
        } catch (Exception ex) {
            setMessage(backupRetentionMessage, message(ex), true);
        }
    }

    private void refreshBackups(){
        if(backupTable==null) return;
        try{
            backupTable.getItems().setAll(BackupService.listBackups());
            fitTableHeight(backupTable, backupTable.getItems().size(), 6, 38);
        }catch(Exception ex){showSimpleError(ex.getMessage());}
    }

    private void backupDatabase() {
        Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,
                "Create a database backup now? The backup will be saved automatically in the W.A.S.P backup folder and verified after creation.",
                ButtonType.OK,ButtonType.CANCEL);
        confirm.setTitle("Create Database Backup");
        confirm.setHeaderText("Backup Database");
        if(confirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK) return;

        runBackupTask(backupNowButton);
    }

    private void runBackupTask(Button backupButton) {
        javafx.concurrent.Task<Path> task = new javafx.concurrent.Task<>() {
            @Override protected Path call() {
                updateMessage("Creating database backup...");
                Path created = BackupService.backupTo(BackupService.defaultBackupPath());
                if (SettingsService.isBackupRetentionEnabled()) {
                    updateMessage("Applying backup retention policy...");
                    BackupService.cleanupOldBackups(SettingsService.getBackupRetentionCount());
                }
                return created;
            }
        };
        Dialog<Void> dialog = createBackupProgressDialog(task);
        backupButton.setDisable(true);
        task.setOnSucceeded(e -> {
            dialog.close();
            backupButton.setDisable(false);
            refreshBackups();
            new Alert(Alert.AlertType.INFORMATION,"Database backup created and verified successfully.\n\n"+task.getValue().getFileName(),ButtonType.OK).showAndWait();
        });
        task.setOnFailed(e -> {
            dialog.close();
            backupButton.setDisable(false);
            new Alert(Alert.AlertType.ERROR,"Backup failed: "+message(task.getException()),ButtonType.OK).showAndWait();
        });
        Thread thread = new Thread(task, "wasp-backup");
        thread.setDaemon(true);
        thread.start();
        dialog.show();
    }

    private Dialog<Void> createBackupProgressDialog(javafx.concurrent.Task<?> task) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Creating Backup");
        dialog.setHeaderText("Creating and verifying database backup");
        if (getScene()!=null) dialog.initOwner(getScene().getWindow());
        ProgressIndicator indicator = new ProgressIndicator(ProgressIndicator.INDETERMINATE_PROGRESS);
        Label message = new Label("Please wait while W.A.S.P creates and verifies the backup.");
        message.setWrapText(true);
        VBox box = new VBox(12, indicator, message);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);
        task.messageProperty().addListener((obs, oldValue, newValue) -> message.setText(newValue));
        return dialog;
    }

    private void restoreSelectedBackup(){
        BackupService.BackupInfo selected=backupTable.getSelectionModel().getSelectedItem();
        if(selected==null){showSimpleError("Select a backup from the list first.");return;}
        Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,
                "Restore "+selected.path().getFileName()+"?\n\nThe current database will first be preserved as a safety copy. W.A.S.P will close after the restore; start the application again to continue.",
                ButtonType.OK,ButtonType.CANCEL);
        confirm.setTitle("Restore Database"); confirm.setHeaderText("Restore Selected Backup");
        if(confirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
        try{
            BackupService.restoreFrom(selected.path());
            new Alert(Alert.AlertType.INFORMATION,"Database restored successfully. The application will now close. Start W.A.S.P again to continue.",ButtonType.OK).showAndWait();
            Platform.exit();
        }catch(Exception ex){new Alert(Alert.AlertType.ERROR,"Restore failed: "+message(ex),ButtonType.OK).showAndWait();}
    }

    private void deleteSelectedBackup(){
        BackupService.BackupInfo selected=backupTable.getSelectionModel().getSelectedItem();
        if(selected==null){showSimpleError("Select a backup from the list first.");return;}
        Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,
                "Delete "+selected.path().getFileName()+"? This cannot be undone.",ButtonType.OK,ButtonType.CANCEL);
        confirm.setTitle("Delete Backup"); confirm.setHeaderText("Delete Selected Backup");
        if(confirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
        try{BackupService.deleteBackup(selected.path());refreshBackups();}
        catch(Exception ex){showSimpleError(ex.getMessage());}
    }

    private void openBackupFolder(){
        try{Path dir=com.aks.waybill.config.AppPaths.backupDirectory();Files.createDirectories(dir);
            new ProcessBuilder("explorer.exe",dir.toAbsolutePath().toString()).start();
        }catch(Exception ex){showSimpleError("Unable to open the backup folder: "+message(ex));}
    }

    private static String formatBytes(long bytes){
        if(bytes<1024) return bytes+" B";
        if(bytes<1024*1024) return String.format("%.1f KB",bytes/1024.0);
        return String.format("%.1f MB",bytes/(1024.0*1024.0));
    }

    private static VBox card() {
        VBox box = new VBox(12);
        box.getStyleClass().add("settings-card");
        box.setPadding(new Insets(16));
        return box;
    }

    private static void addField(GridPane grid, int row, String labelText, Region control, String prompt) {
        Label label = new Label(labelText);
        label.getStyleClass().add("field-label");
        if (control instanceof TextInputControl input) {
            input.setPromptText(prompt);
        }
        control.setMaxWidth(Double.MAX_VALUE);
        control.getStyleClass().add("settings-field");
        GridPane.setHgrow(control, Priority.ALWAYS);
        grid.add(label, 0, row);
        grid.add(control, 1, row);
        ColumnConstraints left = grid.getColumnConstraints().isEmpty() ? new ColumnConstraints() : grid.getColumnConstraints().get(0);
        if (grid.getColumnConstraints().isEmpty()) {
            left.setMinWidth(170);
            ColumnConstraints right = new ColumnConstraints();
            right.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().addAll(left, right);
        }
    }

    private static Button primary(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("primary-button");
        return button;
    }

    private static Button secondary(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary-button");
        return button;
    }

    private void loadAll(){loadNumbering();loadProfile(); pageSize.setValue(SettingsService.getPageSize());}
    private void confirmResetNumbering(){ Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Reset the numbering fields to the values currently stored in the database? Unsaved changes on this screen will be lost.",ButtonType.OK,ButtonType.CANCEL); if(a.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK) loadNumbering(); }
    private void confirmResetProfile(){ Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Reset the company profile fields to the values currently stored in the database? Unsaved changes on this screen will be lost.",ButtonType.OK,ButtonType.CANCEL); if(a.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK) loadProfile(); }
    private void loadNumbering(){try{var s=SettingsService.getWaybillNumberSettings();part1.setText(s.part1());sequence.setText(String.valueOf(s.nextSequence()));setMessage(numberingMessage,"Numbering settings loaded.",false);updatePreview();}catch(Exception e){setMessage(numberingMessage,message(e),true);}}
    private void saveNumbering(){try{SettingsService.saveWaybillNumberSettings(part1.getText(),Long.parseLong(sequence.getText().trim()));loadNumbering();setMessage(numberingMessage,"Waybill numbering settings saved.",false);}catch(NumberFormatException e){setMessage(numberingMessage,"Next sequence number must be a valid whole number.",true);}catch(Exception e){setMessage(numberingMessage,message(e),true);}}
    private void updatePreview(){try{String p=part1.getText().isBlank()?"AKS":part1.getText().trim().toUpperCase();String c=SessionContext.requireUserCode();String n=sequence.getText().isBlank()?"1001":sequence.getText().trim();preview.setText(p+"/"+c+"/"+DATE.format(LocalDate.now())+"/"+n);}catch(Exception e){preview.setText("Log in with a configured User Code to preview the number.");}}
    private void loadProfile(){try{var p=ReportProfileService.get();companyName.setText(p.companyName());crNumber.setText(p.crNumber());vatNumber.setText(p.vatNumber());address.setText(p.address());phone.setText(p.phone());email.setText(p.email());setMessage(profileMessage,"Company profile loaded.",false);}catch(Exception e){setMessage(profileMessage,message(e),true);}}
    private void saveProfile(){try{ReportProfileService.save(companyName.getText(),crNumber.getText(),vatNumber.getText(),address.getText(),phone.getText(),email.getText());setMessage(profileMessage,"Report company profile saved successfully.",false);}catch(Exception e){setMessage(profileMessage,message(e),true);}}
    private void setMessage(Label l,String text,boolean error){l.setText(text);l.getStyleClass().removeAll("success-message","error-message");l.getStyleClass().add(error?"error-message":"success-message");}
    private String message(Throwable e){return e.getMessage()==null?"Operation failed.":e.getMessage();}
}
