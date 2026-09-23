package com.aks.waybill.ui;
import com.aks.waybill.security.AuthService;
import javafx.geometry.*; import javafx.scene.Node; import javafx.scene.control.*; import javafx.scene.image.*; import javafx.scene.layout.*;
import java.util.function.Consumer;
public class LoginView extends BorderPane {
    private final AuthService auth=new AuthService(); private final Consumer<AuthService.UserRecord> onLogin; private final VBox form=new VBox(14); private Label message;
    public LoginView(Consumer<AuthService.UserRecord> onLogin){this.onLogin=onLogin;getStyleClass().add("login-root");buildBrand();buildForm();}
    private void buildBrand(){VBox b=new VBox(22);b.setAlignment(Pos.CENTER_LEFT);b.setPadding(new Insets(35));b.getStyleClass().add("brand-panel");b.setPrefWidth(390);
        ImageView logo=new ImageView(new Image(getClass().getResourceAsStream("/com/aks/waybill/images/aks-logo.png")));logo.setFitWidth(220);logo.setPreserveRatio(true);
        Label t=new Label("W.A.S.P");t.getStyleClass().add("brand-title");Label s=new Label("Professional waybill management for\nAKS Global Logistics Co.");s.getStyleClass().add("brand-subtitle");Region sp=new Region();VBox.setVgrow(sp,Priority.ALWAYS);Label f=new Label("W.A.S.P  •  DESKTOP APPLICATION");f.getStyleClass().add("brand-footer");b.getChildren().addAll(logo,t,s,sp,f);setLeft(b);}
    private void buildForm(){StackPane w=new StackPane();w.setPadding(new Insets(35));w.getStyleClass().add("login-panel");VBox c=new VBox(24);c.setMaxWidth(420);
        boolean exists=auth.hasUsers();Label e=new Label("SECURE ACCESS");e.getStyleClass().add("eyebrow");Label h=new Label(exists?"Welcome back":"Create administrator");h.getStyleClass().add("login-heading");Label d=new Label(exists?"Sign in to continue to your waybill workspace.":"This is the first launch. Create the initial administrator account.");d.getStyleClass().add("login-description");form.getStyleClass().add("login-form");if(exists)loginForm();else setupForm();Button close=new Button("Close");close.getStyleClass().add("secondary-button");close.setMaxWidth(Double.MAX_VALUE);close.setOnAction(event->requestClose(()->javafx.application.Platform.exit()));c.getChildren().addAll(e,h,d,form,close);w.getChildren().add(c);setCenter(w);}
    private void loginForm(){TextField u=text("Username");PasswordField p=pass("Password");Button b=primary("Sign in");message=new Label();message.getStyleClass().add("form-message");b.setOnAction(x->{if(u.getText().isBlank()||p.getText().isBlank()){error("Enter your username and password.");return;}var user=auth.authenticate(u.getText(),p.getText());if(user.isPresent()){com.aks.waybill.security.SessionContext.setCurrentUser(user.get());onLogin.accept(user.get());}else error("Invalid credentials, disabled account, or temporary lockout.");});p.setOnAction(b.getOnAction());form.getChildren().addAll(labeled("Username",u),labeled("Password",p),b,message);}
    private void setupForm(){TextField u=text("Administrator username");TextField n=text("Display name");TextField code=text("Waybill user code, e.g. MAT");PasswordField p=pass("Password");PasswordField q=pass("Confirm password");Button b=primary("Create administrator");message=new Label();message.getStyleClass().add("form-message");b.setOnAction(x->{if(u.getText().isBlank()||n.getText().isBlank()||p.getText().length()<8){error("Username, display name and a password of at least 8 characters are required.");return;}if(code.getText().isBlank()){error("Waybill user code is required.");return;}if(!p.getText().equals(q.getText())){error("Passwords do not match.");return;}auth.createInitialAdmin(u.getText(),n.getText(),code.getText(),p.getText());var user=auth.authenticate(u.getText(),p.getText());if(user.isPresent()){com.aks.waybill.security.SessionContext.setCurrentUser(user.get());onLogin.accept(user.get());}});form.getChildren().addAll(labeled("Username",u),labeled("Display name",n),labeled("Waybill Code",code),labeled("Password",p),labeled("Confirm password",q),b,message);}
    private VBox labeled(String label,Node field){Label l=new Label(label);l.getStyleClass().add("field-label");return new VBox(6,l,field);}
    private TextField text(String prompt){TextField f=new TextField(); InputLimits.maxLength(f, InputLimits.USERNAME);f.setPromptText(prompt);f.setPrefHeight(46);return f;}
    private PasswordField pass(String prompt){PasswordField f=new PasswordField(); InputLimits.maxLength(f, 128);f.setPromptText(prompt);f.setPrefHeight(46);return f;}
    private Button primary(String s){Button b=new Button(s);b.setMaxWidth(Double.MAX_VALUE);b.setPrefHeight(48);b.getStyleClass().add("primary-button");return b;}
    /**
     * Closing from the login screen does not require an exit confirmation or
     * backup reminder because no authenticated user session is active and the
     * user could not have made unsaved application changes.
     */
    public void requestClose(Runnable exitAction) {
        exitAction.run();
    }

    private void error(String s){message.setText(s);if(!message.getStyleClass().contains("error-message"))message.getStyleClass().add("error-message");}
}
