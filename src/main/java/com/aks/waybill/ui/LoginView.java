package com.aks.waybill.ui;

import com.aks.waybill.security.AuthService;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.function.Consumer;

public class LoginView extends BorderPane {
    private static final String VERSION = "1.4.0";

    private final AuthService auth = new AuthService();
    private final Consumer<AuthService.UserRecord> onLogin;
    private final VBox form = new VBox(14);
    private Label message;

    public LoginView(Consumer<AuthService.UserRecord> onLogin) {
        this.onLogin = onLogin;
        getStyleClass().add("login-root");
        buildBrand();
        buildForm();
    }

    private void buildBrand() {
        VBox brand = new VBox(12);
        brand.setAlignment(Pos.TOP_LEFT);
        brand.setPadding(new Insets(26, 36, 20, 36));
        brand.getStyleClass().add("brand-panel");
        brand.setPrefWidth(390);
        brand.setMinWidth(330);

        ImageView logo = new ImageView(new Image(
                getClass().getResourceAsStream("/com/aks/waybill/images/aks-logo.png")));
        logo.setFitWidth(230);
        logo.setPreserveRatio(true);

        StackPane logoHolder = new StackPane(logo);
        logoHolder.setAlignment(Pos.CENTER);
        logoHolder.setMaxWidth(Double.MAX_VALUE);

        Label title = new Label("W.A.S.P");
        title.getStyleClass().add("brand-title");

        Label fullName = new Label("Waybill Automation & Shipping Platform");
        fullName.setWrapText(true);
        fullName.getStyleClass().add("brand-full-name");

        Label subtitle = new Label("Professional waybill management for\nAKS Global Logistics Co.");
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(Double.MAX_VALUE);
        subtitle.setTextOverrun(OverrunStyle.CLIP);
        subtitle.getStyleClass().add("brand-subtitle");

        Separator separator = new Separator();
        separator.getStyleClass().add("brand-separator");

        VBox features = new VBox(10);
        features.getStyleClass().add("brand-features");
        features.getChildren().addAll(
                feature("CREATE", "Create and manage waybills efficiently.", "＋"),
                feature("MANAGE", "Keep companies, carriers and locations organized.", "▦"),
                feature("DELIVER", "Generate professional Word and PDF documents.", "↗")
        );

        VBox flow = buildFlow();

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label tagline = new Label("Simplifying Waybill Creation.\nImproving Operational Efficiency.");
        tagline.setWrapText(true);
        tagline.setMaxWidth(Double.MAX_VALUE);
        tagline.setTextOverrun(OverrunStyle.CLIP);
        tagline.getStyleClass().add("brand-tagline");

        Label footer = new Label("W.A.S.P.  •  DESKTOP APPLICATION");
        footer.getStyleClass().add("brand-footer");

        Label version = new Label("Version " + VERSION);
        version.getStyleClass().add("brand-version");

        VBox footerBox = new VBox(4, tagline, footer, version);
        footerBox.getStyleClass().add("brand-footer-box");

        brand.getChildren().addAll(logoHolder, title, fullName, subtitle, separator, features, flow, spacer, footerBox);
        setLeft(brand);
    }

    private VBox feature(String title, String description, String iconText) {
        Label icon = new Label(iconText);
        icon.getStyleClass().add("brand-feature-icon");
        icon.setMinSize(24, 24);
        icon.setPrefSize(24, 24);
        icon.setMaxSize(24, 24);
        icon.setAlignment(Pos.CENTER);

        Label featureTitle = new Label(title);
        featureTitle.getStyleClass().add("brand-feature-title");
        featureTitle.setGraphic(icon);
        featureTitle.setGraphicTextGap(8);

        Label featureDescription = new Label(description);
        featureDescription.setWrapText(true);
        featureDescription.getStyleClass().add("brand-feature-description");

        VBox text = new VBox(2, featureTitle, featureDescription);
        text.setFillWidth(true);
        return text;
    }

    private VBox buildFlow() {
        Label origin = flowNode("ORIGIN");
        Label transit = flowNode("TRANSIT");
        Label destination = flowNode("DESTINATION");

        Label arrow1 = flowArrow();
        Label arrow2 = flowArrow();

        HBox row = new HBox(7, origin, arrow1, transit, arrow2, destination);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(2, row);
        box.setMaxWidth(Double.MAX_VALUE);
        box.getStyleClass().add("brand-flow");
        return box;
    }

    private Label flowNode(String text) {
        Label node = new Label("●  " + text);
        node.getStyleClass().add("brand-flow-node");
        return node;
    }

    private Label flowArrow() {
        Label arrow = new Label("→");
        arrow.getStyleClass().add("brand-flow-arrow");
        return arrow;
    }

    private void buildForm() {
        StackPane wrapper = new StackPane();
        wrapper.setPadding(new Insets(35, 50, 35, 50));
        wrapper.getStyleClass().add("login-panel");

        VBox content = new VBox(20);
        content.setMaxWidth(420);

        boolean exists = auth.hasUsers();
        Label eyebrow = new Label("SECURE SIGN IN");
        eyebrow.getStyleClass().add("eyebrow");
        Label heading = new Label(exists ? "Welcome back" : "Create administrator");
        heading.getStyleClass().add("login-heading");
        Label description = new Label(exists
                ? "Sign in to continue to your waybill workspace."
                : "This is the first launch. Create the initial administrator account.");
        description.getStyleClass().add("login-description");

        form.getStyleClass().add("login-form");
        if (exists) {
            loginForm();
        } else {
            setupForm();
        }

        Button close = new Button("Close");
        close.getStyleClass().add("secondary-button");
        close.setMaxWidth(Double.MAX_VALUE);
        close.setOnAction(event -> requestClose(Platform::exit));

        Label version = new Label("W.A.S.P.  •  Version " + VERSION);
        version.getStyleClass().add("login-version");
        version.setMaxWidth(Double.MAX_VALUE);
        version.setAlignment(Pos.CENTER);

        content.getChildren().addAll(eyebrow, heading, description, form, close, version);
        wrapper.getChildren().add(content);
        setCenter(wrapper);
    }

    private void loginForm() {
        TextField username = text("Username");
        PasswordField password = pass("Password");
        Button signIn = primary("Sign in");
        message = new Label();
        message.getStyleClass().add("form-message");

        signIn.setOnAction(event -> {
            if (username.getText().isBlank() || password.getText().isBlank()) {
                error("Enter your username and password.");
                return;
            }
            var user = auth.authenticate(username.getText(), password.getText());
            if (user.isPresent()) {
                com.aks.waybill.security.SessionContext.setCurrentUser(user.get());
                onLogin.accept(user.get());
            } else {
                error("Invalid credentials, disabled account, or temporary lockout.");
            }
        });
        password.setOnAction(signIn.getOnAction());
        form.getChildren().addAll(labeled("Username", username), labeled("Password", password), signIn, message);
    }

    private void setupForm() {
        TextField username = text("Administrator username");
        TextField displayName = text("Display name");
        TextField code = text("Waybill user code, e.g. MAT");
        PasswordField password = pass("Password");
        PasswordField confirmPassword = pass("Confirm password");
        Button create = primary("Create administrator");
        message = new Label();
        message.getStyleClass().add("form-message");

        create.setOnAction(event -> {
            if (username.getText().isBlank() || displayName.getText().isBlank() || password.getText().length() < 8) {
                error("Username, display name and a password of at least 8 characters are required.");
                return;
            }
            if (code.getText().isBlank()) {
                error("Waybill user code is required.");
                return;
            }
            if (!password.getText().equals(confirmPassword.getText())) {
                error("Passwords do not match.");
                return;
            }
            auth.createInitialAdmin(username.getText(), displayName.getText(), code.getText(), password.getText());
            var user = auth.authenticate(username.getText(), password.getText());
            if (user.isPresent()) {
                com.aks.waybill.security.SessionContext.setCurrentUser(user.get());
                onLogin.accept(user.get());
            }
        });

        form.getChildren().addAll(
                labeled("Username", username),
                labeled("Display name", displayName),
                labeled("Waybill Code", code),
                labeled("Password", password),
                labeled("Confirm password", confirmPassword),
                create,
                message
        );
    }

    private VBox labeled(String label, Node field) {
        Label fieldLabel = new Label(label);
        fieldLabel.getStyleClass().add("field-label");
        return new VBox(6, fieldLabel, field);
    }

    private TextField text(String prompt) {
        TextField field = new TextField();
        InputLimits.maxLength(field, InputLimits.USERNAME);
        field.setPromptText(prompt);
        field.setPrefHeight(46);
        return field;
    }

    private PasswordField pass(String prompt) {
        PasswordField field = new PasswordField();
        InputLimits.maxLength(field, 128);
        field.setPromptText(prompt);
        field.setPrefHeight(46);
        return field;
    }

    private Button primary(String text) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setPrefHeight(48);
        button.getStyleClass().add("primary-button");
        return button;
    }

    /**
     * Closing from the login screen does not require an exit confirmation or
     * backup reminder because no authenticated user session is active and the
     * user could not have made unsaved application changes.
     */
    public void requestClose(Runnable exitAction) {
        exitAction.run();
    }

    private void error(String text) {
        message.setText(text);
        if (!message.getStyleClass().contains("error-message")) {
            message.getStyleClass().add("error-message");
        }
    }
}
