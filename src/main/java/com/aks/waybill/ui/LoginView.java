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
    private static final String VERSION = "2.0.0";

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
        brand.setPadding(new Insets(24, 34, 20, 34));
        brand.getStyleClass().add("brand-panel");
        brand.setPrefWidth(390);
        brand.setMinWidth(360);
        brand.setMaxWidth(420);

        ImageView logo = new ImageView(new Image(
                getClass().getResourceAsStream("/com/aks/waybill/images/wasp_application_logo.png")));
        logo.setFitWidth(235);
        logo.setPreserveRatio(true);
        logo.setSmooth(true);

        StackPane logoHolder = new StackPane(logo);
        logoHolder.setAlignment(Pos.TOP_CENTER);
        logoHolder.setMaxWidth(Double.MAX_VALUE);

        // Match the requested vertical rhythm: logo, two-line gap, separator,
        // two-line gap, feature list, two-line gap, then the centred route flow.
        Region logoGap = new Region();
        logoGap.setMinHeight(24);
        logoGap.setPrefHeight(24);
        logoGap.setMaxHeight(24);

        Region separator = new Region();
        separator.getStyleClass().add("brand-separator");
        separator.setMinHeight(1);
        separator.setPrefHeight(1);
        separator.setMaxWidth(Double.MAX_VALUE);

        Region featureGap = new Region();
        featureGap.setMinHeight(24);
        featureGap.setPrefHeight(24);
        featureGap.setMaxHeight(24);

        VBox features = new VBox(12);
        features.getStyleClass().add("brand-features");
        features.getChildren().addAll(
                feature("CREATE", "Create and manage waybills efficiently.", "＋"),
                feature("MANAGE", "Keep companies, carriers and locations organized.", "▦"),
                feature("DELIVER", "Generate professional Word and PDF documents.", "↗")
        );

        Region flowGap = new Region();
        flowGap.setMinHeight(24);
        flowGap.setPrefHeight(24);
        flowGap.setMaxHeight(24);

        VBox flow = buildFlow();

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label footer = new Label("W.A.S.P.  •  DESKTOP APPLICATION");
        footer.getStyleClass().add("brand-footer");
        footer.setMaxWidth(Double.MAX_VALUE);
        footer.setAlignment(Pos.CENTER);

        VBox footerBox = new VBox(4, footer);
        footerBox.getStyleClass().add("brand-footer-box");
        footerBox.setAlignment(Pos.CENTER);
        footerBox.setMaxWidth(Double.MAX_VALUE);

        brand.getChildren().addAll(logoHolder, logoGap, separator, featureGap,
                features, flowGap, flow, spacer, footerBox);
        setLeft(brand);
    }

    private VBox feature(String title, String description, String iconText) {
        Label icon = new Label(iconText);
        icon.getStyleClass().add("brand-feature-icon");
        icon.setMinSize(30, 30);
        icon.setPrefSize(30, 30);
        icon.setMaxSize(30, 30);
        icon.setAlignment(Pos.CENTER);

        Label featureTitle = new Label(title);
        featureTitle.getStyleClass().add("brand-feature-title");
        featureTitle.setGraphic(icon);
        featureTitle.setGraphicTextGap(10);

        Label featureDescription = new Label(description);
        featureDescription.setWrapText(true);
        featureDescription.getStyleClass().add("brand-feature-description");
        featureDescription.setTranslateX(40);

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
        row.setAlignment(Pos.CENTER);
        row.setMaxWidth(Double.MAX_VALUE);

        VBox box = new VBox(2, row);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setAlignment(Pos.CENTER);
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
        // A little more top breathing room, as requested, without resizing the form.
        wrapper.setPadding(new Insets(78, 50, 35, 50));
        wrapper.getStyleClass().add("login-panel");

        VBox content = new VBox(16);
        content.setMaxWidth(460);

        boolean exists = auth.hasUsers();
        Label eyebrow = new Label(exists ? "" : "INITIAL SETUP");
        eyebrow.getStyleClass().add("eyebrow");
        eyebrow.setMaxWidth(Double.MAX_VALUE);
        eyebrow.setAlignment(Pos.CENTER);
        Label heading = new Label(exists ? "Welcome back" : "Create administrator");
        heading.setMaxWidth(Double.MAX_VALUE);
        heading.setAlignment(Pos.CENTER);
        heading.getStyleClass().add("login-heading");
        Label description = new Label(exists
                ? "Sign in to continue to your waybill workspace."
                : "This is the first launch. Create the initial administrator account.");
        description.getStyleClass().add("login-description");
        description.setMaxWidth(Double.MAX_VALUE);
        description.setAlignment(Pos.CENTER);

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

        if (!exists) {
            content.getChildren().add(eyebrow);
        }
        content.getChildren().addAll(heading, description, form, close, version);
        // Keep keyboard interaction natural on both the sign-in and first-launch forms.
        wrapper.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                requestClose(Platform::exit);
                event.consume();
            }
        });

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
        TextField code = text("Waybill code, e.g. ABC");
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

        // Support Enter-key navigation through the initial setup fields.
        username.setOnAction(event -> displayName.requestFocus());
        displayName.setOnAction(event -> code.requestFocus());
        code.setOnAction(event -> password.requestFocus());
        password.setOnAction(event -> confirmPassword.requestFocus());
        confirmPassword.setOnAction(create.getOnAction());

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
