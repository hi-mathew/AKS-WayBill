package com.aks.waybill.ui;

import com.aks.waybill.security.SessionContext;
import com.aks.waybill.service.ReportProfileService;
import com.aks.waybill.service.SettingsService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

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
    { InputLimits.maxLength(part1, InputLimits.PART1); InputLimits.numeric(sequence, InputLimits.SEQUENCE, 0); InputLimits.maxLength(companyName, InputLimits.COMPANY_NAME); InputLimits.maxLength(crNumber, InputLimits.CR_NUMBER); InputLimits.maxLength(vatNumber, InputLimits.VAT_NUMBER); InputLimits.maxLength(phone, InputLimits.PHONE); InputLimits.maxLength(email, InputLimits.EMAIL); InputLimits.maxLength(address, InputLimits.ADDRESS); }
    private final Label numberingMessage = new Label();
    private final Label profileMessage = new Label();
    private final Label preview = new Label();

    public SettingsView() {
        super("Settings", "Configure waybill numbering and the issuing company information used on reports.");
        VBox content = new VBox(18, numberingCard(), profileCard());
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
        Button reset=secondary("Reset"); reset.setOnAction(e->loadNumbering()); Button save=primary("Save Changes"); save.setOnAction(e->saveNumbering()); buttons.getChildren().addAll(reset,save);
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
        HBox buttons=new HBox(10); buttons.setAlignment(Pos.CENTER_RIGHT); Button reset=secondary("Reset"); reset.setOnAction(e->loadProfile()); Button save=primary("Save Company Profile"); save.setOnAction(e->saveProfile()); buttons.getChildren().addAll(reset,save);
        profileMessage.getStyleClass().add("settings-message"); profileMessage.setWrapText(true);
        card.getChildren().addAll(title,desc,form,profileMessage,buttons); return card;
    }

    private VBox card(){VBox v=new VBox(18);v.getStyleClass().add("settings-card");v.setPadding(new Insets(24));return v;}
    private Button primary(String s){Button b=new Button(s);b.getStyleClass().add("primary-button");b.setDefaultButton(true);return b;}
    private Button secondary(String s){Button b=new Button(s);b.getStyleClass().add("secondary-button");return b;}
    private void addField(GridPane g,int row,String label,TextField f,String prompt){Label l=new Label(label);l.getStyleClass().add("field-label");f.setPromptText(prompt);f.setPrefHeight(40);f.setMaxWidth(Double.MAX_VALUE);f.getStyleClass().add("settings-field");GridPane.setHgrow(f,Priority.ALWAYS);g.add(l,0,row);g.add(f,1,row);}
    private void loadAll(){loadNumbering();loadProfile();}
    private void loadNumbering(){try{var s=SettingsService.getWaybillNumberSettings();part1.setText(s.part1());sequence.setText(String.valueOf(s.nextSequence()));setMessage(numberingMessage,"Numbering settings loaded.",false);updatePreview();}catch(Exception e){setMessage(numberingMessage,message(e),true);}}
    private void saveNumbering(){try{SettingsService.saveWaybillNumberSettings(part1.getText(),Long.parseLong(sequence.getText().trim()));loadNumbering();setMessage(numberingMessage,"Waybill numbering settings saved.",false);}catch(NumberFormatException e){setMessage(numberingMessage,"Next sequence number must be a valid whole number.",true);}catch(Exception e){setMessage(numberingMessage,message(e),true);}}
    private void updatePreview(){try{String p=part1.getText().isBlank()?"AKS":part1.getText().trim().toUpperCase();String c=SessionContext.requireUserCode();String n=sequence.getText().isBlank()?"1001":sequence.getText().trim();preview.setText(p+"/"+c+"/"+DATE.format(LocalDate.now())+"/"+n);}catch(Exception e){preview.setText("Log in with a configured User Code to preview the number.");}}
    private void loadProfile(){try{var p=ReportProfileService.get();companyName.setText(p.companyName());crNumber.setText(p.crNumber());vatNumber.setText(p.vatNumber());address.setText(p.address());phone.setText(p.phone());email.setText(p.email());setMessage(profileMessage,"Company profile loaded.",false);}catch(Exception e){setMessage(profileMessage,message(e),true);}}
    private void saveProfile(){try{ReportProfileService.save(companyName.getText(),crNumber.getText(),vatNumber.getText(),address.getText(),phone.getText(),email.getText());setMessage(profileMessage,"Report company profile saved successfully.",false);}catch(Exception e){setMessage(profileMessage,message(e),true);}}
    private void setMessage(Label l,String text,boolean error){l.setText(text);l.getStyleClass().removeAll("success-message","error-message");l.getStyleClass().add(error?"error-message":"success-message");}
    private String message(Exception e){return e.getMessage()==null?"Operation failed.":e.getMessage();}
}
