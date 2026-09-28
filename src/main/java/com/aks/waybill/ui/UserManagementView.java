package com.aks.waybill.ui;

import com.aks.waybill.security.SessionContext;
import com.aks.waybill.service.UserService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/** Administrator-only user management. */
public final class UserManagementView extends AppView {
    private static final double TABLE_ROW_HEIGHT = 38.0;
    private static final double TABLE_HEADER_HEIGHT = 32.0;
    private static final double TABLE_EMPTY_HEIGHT = 72.0;
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final TableView<UserService.UserRecord> table = new TableView<>();
    private final Label message = new Label();
    private final TextField search = new TextField();
    private final Label resultInfo = new Label();
    private final Label pageInfo = new Label();
    private final HBox pageButtons = new HBox(5);
    private int currentPage = 0;
    private int totalPages = 1;
    private long totalRows = 0;

    public UserManagementView() {
        super("User Management", "Manage application users, roles, user-specific waybill codes and passwords.");
        getChildren().add(build()); VBox.setVgrow(getChildren().get(2), Priority.ALWAYS);
        loadPage(0);
    }

    private VBox build() {
        VBox root=new VBox(14); root.setPadding(new Insets(4,0,30,0));
        HBox toolbar=new HBox(10); toolbar.setAlignment(Pos.CENTER_LEFT);
        search.setPromptText("Search username, display name, user code or role"); InputLimits.maxLength(search,400); HBox.setHgrow(search,Priority.ALWAYS); search.setOnAction(e->loadPage(0));
        Button find=primary("Search"); find.setOnAction(e->loadPage(0));
        Button clear=secondary("Clear"); clear.setOnAction(e->{search.clear();loadPage(0);});
        Button add=primary("＋ Add User"); add.setOnAction(e->openEditor(null));
        Button edit=secondary("Edit"); edit.getStyleClass().add("user-selection-action"); edit.setOnAction(e->{var r=table.getSelectionModel().getSelectedItem();if(r!=null)openEditor(r);});
        Button toggle=secondary("Enable / Disable"); toggle.getStyleClass().add("user-selection-action"); toggle.setOnAction(e->toggleSelected());
        Button reset=secondary("Reset Password"); reset.getStyleClass().add("user-selection-action"); reset.setOnAction(e->resetSelected());
        Button refresh=secondary("Refresh"); refresh.setOnAction(e->loadPage(currentPage));
        toolbar.getChildren().addAll(search,find,clear,add,edit,toggle,reset,refresh);

        TableColumn<UserService.UserRecord,String> username=new TableColumn<>("Username"); username.setCellValueFactory(c->new javafx.beans.property.SimpleStringProperty(c.getValue().username())); username.setPrefWidth(160);
        TableColumn<UserService.UserRecord,String> name=new TableColumn<>("Display Name"); name.setCellValueFactory(c->new javafx.beans.property.SimpleStringProperty(c.getValue().displayName())); name.setPrefWidth(220);
        TableColumn<UserService.UserRecord,String> code=new TableColumn<>("Waybill Code"); code.setCellValueFactory(c->new javafx.beans.property.SimpleStringProperty(c.getValue().userCode())); code.setPrefWidth(120);
        TableColumn<UserService.UserRecord,String> role=new TableColumn<>("Role"); role.setCellValueFactory(c->new javafx.beans.property.SimpleStringProperty(c.getValue().role())); role.setPrefWidth(100);
        TableColumn<UserService.UserRecord,String> status=new TableColumn<>("Status"); status.setCellValueFactory(c->new javafx.beans.property.SimpleStringProperty(c.getValue().enabled()?"Active":"Disabled")); status.setPrefWidth(110);
        TableColumn<UserService.UserRecord,String> last=new TableColumn<>("Last Login"); last.setCellValueFactory(c->new javafx.beans.property.SimpleStringProperty(format(c.getValue().lastLoginAt()))); last.setPrefWidth(190);
        table.getColumns().setAll(username,name,code,role,status,last); table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN); table.setPlaceholder(new Label("No users found."));
        table.setRowFactory(tv->{TableRow<UserService.UserRecord> row=new TableRow<>();row.setOnMouseClicked(e->{if(e.getClickCount()==2&&!row.isEmpty())openEditor(row.getItem());});return row;});
        table.getSelectionModel().selectedItemProperty().addListener((obs,oldValue,newValue)->updateActionState());

        resultInfo.getStyleClass().add("card-description"); pageInfo.getStyleClass().add("card-description"); pageButtons.setAlignment(Pos.CENTER);
        Button previous=secondary("‹"); previous.setOnAction(e->loadPage(currentPage-1)); Button next=secondary("›"); next.setOnAction(e->loadPage(currentPage+1));
        Region spacer=new Region(); HBox.setHgrow(spacer,Priority.ALWAYS); HBox bottom=new HBox(10,resultInfo,spacer,pageInfo); bottom.setAlignment(Pos.CENTER_LEFT);
        HBox paging=new HBox(12,previous,pageButtons,next); paging.setAlignment(Pos.CENTER);
        message.getStyleClass().add("settings-message"); message.setWrapText(true);
        root.getChildren().addAll(toolbar,table,message,bottom,paging); updateActionState(); return root;
    }

    private void loadPage(int requestedPage){
        try{
            int size=Math.max(1,com.aks.waybill.service.SettingsService.getPageSize());
            String term=search.getText()==null?"":search.getText().trim().toLowerCase();
            List<UserService.UserRecord> all=UserService.findAll().stream().filter(u->term.isBlank()
                    || contains(u.username(),term) || contains(u.displayName(),term) || contains(u.userCode(),term)
                    || contains(u.role(),term) || contains(u.enabled()?"Active":"Disabled",term)).toList();
            totalRows=all.size(); totalPages=(int)Math.max(1,(totalRows+size-1)/size); currentPage=Math.max(0,Math.min(requestedPage,totalPages-1));
            int from=(int)((long)currentPage*size),to=Math.min(all.size(),from+size); table.setItems(FXCollections.observableArrayList(all.subList(from,to)));
            updateTableHeight(to-from,size); long start=totalRows==0?0:(long)currentPage*size+1; long end=Math.min(totalRows,(long)(currentPage+1)*size);
            resultInfo.setText("Showing "+start+"–"+end+" of "+totalRows); pageInfo.setText("Page "+(currentPage+1)+" of "+totalPages); buildPages(); updateActionState();
            setMessage(totalRows==0?"No users match the current search.":"Users loaded.",false);
        }catch(Exception e){updateTableHeight(0,sizeSafe());updateActionState();setMessage(msg(e),true);}
    }

    private int sizeSafe(){try{return Math.max(1,com.aks.waybill.service.SettingsService.getPageSize());}catch(Exception e){return 10;}}
    private static boolean contains(String value,String term){return value!=null&&value.toLowerCase().contains(term);}
    private void buildPages(){pageButtons.getChildren().clear();int start=Math.max(0,currentPage-2),end=Math.min(totalPages-1,start+4);start=Math.max(0,end-4);for(int i=start;i<=end;i++){final int page=i;Button b=new Button(String.valueOf(i+1));b.getStyleClass().add(i==currentPage?"nav-selected":"secondary-button");b.setOnAction(e->loadPage(page));pageButtons.getChildren().add(b);}}

    private void updateTableHeight(int rows,int size){
        double height=TABLE_HEADER_HEIGHT+(Math.max(1,Math.min(rows,size))*TABLE_ROW_HEIGHT)+10;
        table.setMinHeight(Math.max(TABLE_EMPTY_HEIGHT,Math.min(height,TABLE_HEADER_HEIGHT+TABLE_ROW_HEIGHT+4))); table.setPrefHeight(height); table.setMaxHeight(height); VBox.setVgrow(table,Priority.NEVER);
    }

    private void updateActionState() {
        UserService.UserRecord selected = table.getSelectionModel().getSelectedItem();
        for (javafx.scene.Node node : ((HBox) table.getParent().getChildrenUnmodifiable().get(0)).getChildrenUnmodifiable()) {
            if (node instanceof Button button && button.getStyleClass().contains("user-selection-action")) button.setDisable(selected == null);
        }
    }

    private void toggleSelected(){var u=table.getSelectionModel().getSelectedItem();if(u==null){setMessage("Select a user first.",true);return;}if(u.id()==SessionContext.requireUserId()&&!u.enabled()){setMessage("You cannot enable/disable the current session this way.",true);return;}if(u.id()==SessionContext.requireUserId()&&u.enabled()){setMessage("You cannot disable the account currently in use.",true);return;}try{UserService.setEnabled(u.id(),!u.enabled());loadPage(currentPage);}catch(Exception e){setMessage(msg(e),true);}}

    private void resetSelected(){var u=table.getSelectionModel().getSelectedItem();if(u==null){setMessage("Select a user first.",true);return;}Dialog<ButtonType> d=new Dialog<>();d.setTitle("Reset Password");d.setHeaderText("Reset password for "+u.username());PasswordField p=new PasswordField(); InputLimits.maxLength(p, 128);p.setPromptText("New password (minimum 8 characters)");PasswordField q=new PasswordField(); InputLimits.maxLength(q, 128);q.setPromptText("Confirm password");VBox box=new VBox(10,new Label("New password"),p,new Label("Confirm password"),q);box.setPadding(new Insets(10));d.getDialogPane().setContent(box);d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,ButtonType.OK);d.setResultConverter(x->x);Optional<ButtonType> result=d.showAndWait();if(result.isPresent()&&result.get()==ButtonType.OK){if(!p.getText().equals(q.getText())){setMessage("Passwords do not match.",true);return;}try{UserService.resetPassword(u.id(),p.getText());setMessage("Password reset. The user will be required to change it at next login.",false);}catch(Exception e){setMessage(msg(e),true);}}}

    private void openEditor(UserService.UserRecord existing){
        Dialog<ButtonType> d=new Dialog<>(); d.setTitle(existing==null?"Add User":"Edit User"); d.setHeaderText(existing==null?"Create a new application user.":"Update "+existing.username());
        TextField username=new TextField(existing==null?"":existing.username()); InputLimits.maxLength(username, InputLimits.USERNAME); TextField name=new TextField(existing==null?"":existing.displayName()); InputLimits.maxLength(name, InputLimits.DISPLAY_NAME); TextField code=new TextField(existing==null?"":existing.userCode()); InputLimits.maxLength(code, InputLimits.USER_CODE);
        ComboBox<String> role=new ComboBox<>(); role.getItems().addAll("USER","ADMIN"); role.setValue(existing==null?"USER":existing.role());
        PasswordField password=new PasswordField(); InputLimits.maxLength(password, 128); PasswordField confirm=new PasswordField(); InputLimits.maxLength(confirm, 128);
        if(existing!=null){password.setPromptText("Leave blank to keep current password");confirm.setPromptText("Leave blank to keep current password");}
        GridPane g=new GridPane();g.setHgap(12);g.setVgap(10);g.setPadding(new Insets(10));
        add(g,0,"Username",username);add(g,1,"Display Name",name);add(g,2,"Waybill Code",code);add(g,3,"Role",role);add(g,4,"Password",password);add(g,5,"Confirm Password",confirm);
        d.getDialogPane().setContent(g);d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,ButtonType.OK);d.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION,e->{try{if(existing==null){UserService.create(username.getText(),name.getText(),code.getText(),role.getValue(),password.getText());}else{UserService.update(existing.id(),username.getText(),name.getText(),code.getText(),role.getValue());if(!password.getText().isBlank())UserService.resetPassword(existing.id(),password.getText());}loadPage(currentPage);}catch(Exception ex){e.consume();new Alert(Alert.AlertType.ERROR,"Unable to save user: "+msg(ex),ButtonType.OK).showAndWait();}});d.showAndWait();}
    private void add(GridPane g,int row,String label,Control c){Label l=new Label(label);l.getStyleClass().add("field-label");c.setPrefWidth(320);g.add(l,0,row);g.add(c,1,row);}
    private Button primary(String s){Button b=new Button(s);b.getStyleClass().add("primary-button");return b;}
    private Button secondary(String s){Button b=new Button(s);b.getStyleClass().add("secondary-button");return b;}
    private void setMessage(String s,boolean error){message.setText(s);message.getStyleClass().removeAll("success-message","error-message");message.getStyleClass().add(error?"error-message":"success-message");}
    private String msg(Exception e){return e.getMessage()==null?"Operation failed.":e.getMessage();}
    private String format(String value){
        if (value == null || value.isBlank()) return "Never";

        // Login timestamps are stored as UTC instants (e.g. 2026-09-26T06:18:42Z).
        // Convert them to the workstation's local timezone before displaying them.
        try {
            return Instant.parse(value).atZone(ZoneId.systemDefault()).format(DISPLAY_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).format(DISPLAY_DATE_TIME);
            } catch (DateTimeParseException ignoredAgain) {
                // Backward compatibility for legacy timestamps that have no offset.
                try {
                    return LocalDateTime.parse(value.replace("Z", "")).format(DISPLAY_DATE_TIME);
                } catch (DateTimeParseException legacyIgnored) {
                    return value.replace('T', ' ');
                }
            }
        }
    }
}
