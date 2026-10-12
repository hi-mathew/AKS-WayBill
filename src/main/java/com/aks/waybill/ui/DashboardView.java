package com.aks.waybill.ui;

import com.aks.waybill.security.AuthService;
import com.aks.waybill.security.SessionContext;
import com.aks.waybill.service.CompanyService;
import com.aks.waybill.service.WaybillService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Main application shell and dashboard. */
public class DashboardView extends BorderPane {
    private boolean lifecycleActionInProgress;
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final AuthService.UserRecord currentUser;
    private final Runnable logout;
    private final Runnable exit;
    private final StackPane content = new StackPane();
    private final ImageView topbarLogo = createTopbarLogo();
    private final Label topbarDivider = label("|", "topbar-divider");
    private final Label pageTitle = label("Dashboard", "topbar-title");

    private final Button dashboardButton = nav("▦  Dashboard", true);
    private final Button newWaybillButton = nav("＋  New Waybill", false);
    private final Button savedWaybillsButton = nav("▤  Saved Waybills", false);
    private final Button companiesButton = nav("▦  Saved Data", false);
    private final Button dataExchangeButton = nav("⇄  Data Exchange", false);
    private final Button settingsButton = nav("⚙  Settings", false);
    private final Button usersButton = nav("♟  User Management", false);
    private final Button auditButton = nav("▤  Audit Log", false);
    private final Button aboutButton = nav("ⓘ  About", false);
    private final Label clockLabel = label("", "topbar-clock");

    public DashboardView(AuthService.UserRecord currentUser, Runnable logout) {
        this(currentUser, logout, javafx.application.Platform::exit);
    }

    public DashboardView(AuthService.UserRecord currentUser, Runnable logout, Runnable exit) {
        this.currentUser = currentUser;
        this.logout = logout;
        this.exit = exit == null ? javafx.application.Platform::exit : exit;
        getStyleClass().add("app-root");
        setLeft(sidebar());

        // Keep the sidebar full-height. The header belongs to the main workspace
        // and therefore starts immediately after the sidebar, matching the
        // reference layout.
        BorderPane workspace = new BorderPane();
        workspace.setTop(topbar());
        workspace.setCenter(content);
        setCenter(workspace);
        showDashboard();
        installShortcuts();
    }

    private BorderPane sidebar() {
        BorderPane s = new BorderPane();
        s.setPrefWidth(255);
        s.setMinWidth(235);
        s.setMinHeight(0);
        s.getStyleClass().add("sidebar");

        var logoStream = getClass().getResourceAsStream("/com/aks/waybill/images/wasp_application_logo.png");
        ImageView logo = new ImageView();
        if (logoStream != null) {
            logo.setImage(new Image(logoStream));
        }
        // The supplied artwork is high resolution; JavaFX scales it cleanly, so
        // there is no need for the user to prepare a smaller image.
        logo.setFitWidth(180);
        logo.setFitHeight(145);
        logo.setPreserveRatio(true);

        // The supplied logo is a full-colour application mark. A straight white
        // tile keeps it readable against the dark sidebar without changing the
        // artwork itself.
        StackPane logoTile = new StackPane(logo);
        logoTile.setPrefSize(190, 151);
        logoTile.setMinSize(190, 151);
        logoTile.setMaxSize(190, 151);
        logoTile.getStyleClass().add("sidebar-logo-tile");

        StackPane lb = new StackPane(logoTile);
        lb.setPadding(new Insets(0, 0, 0, 0));

        Label workspace = label("WORKSPACE", "sidebar-section");
        VBox top = new VBox(2, lb, workspace);
        top.setPadding(new Insets(8, 14, 0, 14));
        s.setTop(top);

        // Keep the sidebar hierarchy meaningful for every role.  About is
        // informational, not administrative, so it belongs in its own group.
        VBox navigation = new VBox(1);
        navigation.setFillWidth(true);
        navigation.setPadding(new Insets(0, 14, 2, 14));

        navigation.getChildren().addAll(
                dashboardButton,
                newWaybillButton,
                savedWaybillsButton,
                companiesButton,
                dataExchangeButton);

        if (SessionContext.isAdmin()) {
            Label administration = label("ADMINISTRATION", "sidebar-section");
            navigation.getChildren().addAll(
                    administration,
                    settingsButton,
                    usersButton,
                    auditButton);
        }

        Label information = label("INFORMATION", "sidebar-section");
        navigation.getChildren().addAll(information, aboutButton);

        dashboardButton.setOnAction(e -> showDashboard());
        newWaybillButton.setOnAction(e -> showNewWaybill());
        savedWaybillsButton.setOnAction(e -> showSavedWaybills());
        companiesButton.setOnAction(e -> showSavedData());
        dataExchangeButton.setOnAction(e -> showDataExchange());
        settingsButton.setOnAction(e -> showSettings());
        usersButton.setOnAction(e -> showUserManagement());
        auditButton.setOnAction(e -> showAuditLog());
        aboutButton.setOnAction(e -> showAbout());
        usersButton.setVisible(SessionContext.isAdmin());
        auditButton.setVisible(SessionContext.isAdmin());
        auditButton.setManaged(SessionContext.isAdmin());
        usersButton.setManaged(SessionContext.isAdmin());
        settingsButton.setVisible(SessionContext.isAdmin());
        settingsButton.setManaged(SessionContext.isAdmin());

        ScrollPane navigationScroll = new ScrollPane(navigation);
        navigationScroll.setFitToWidth(true);
        navigationScroll.setFitToHeight(false);
        navigationScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        navigationScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        navigationScroll.setPannable(false);
        navigationScroll.setFocusTraversable(false);
        navigationScroll.setMinHeight(0);
        navigationScroll.getStyleClass().add("sidebar-scroll");
        s.setCenter(navigationScroll);

        Button signOut = nav("⇥  Sign out", false);
        signOut.getStyleClass().add("nav-signout");
        signOut.setStyle("-fx-text-fill: #f0a23a; -fx-font-weight: 800;");
        signOut.setOnAction(e -> requestLogout(logout));

        ImageView footerLogo = new ImageView();
        var footerLogoStream = getClass().getResourceAsStream("/com/aks/waybill/images/wasp-wordmark-white.png");
        if (footerLogoStream != null) {
            footerLogo.setImage(new Image(footerLogoStream));
        }
        footerLogo.setFitWidth(54);
        footerLogo.setFitHeight(20);
        footerLogo.setPreserveRatio(true);
        footerLogo.getStyleClass().add("app-footer-logo");

        Label footer2 = label("Simplifying Waybill Creation.\nImproving Operational Efficiency.", "app-footer-tagline");
        footer2.setWrapText(true);
        footer2.setTextOverrun(OverrunStyle.CLIP);

        Separator footerSeparator = new Separator(Orientation.HORIZONTAL);
        footerSeparator.getStyleClass().add("app-footer-separator");

        HBox developed = footerCreditRow("Developed by:", "Deepesh V. Thampi");
        HBox owned = footerCreditRow("Owned by:", "AKS Global Logistics");
        Label copyright = label("© " + LocalDate.now().getYear() + " AKS Global Logistics  ·  All Rights Reserved.", "app-footer-copyright");

        for (Label footerLabel : List.of(footer2, copyright)) {
            footerLabel.setWrapText(true);
            footerLabel.setTextOverrun(OverrunStyle.CLIP);
        }

        VBox footer = new VBox(2, footerLogo, footer2, footerSeparator, developed, owned, copyright);
        VBox.setMargin(footerLogo, new Insets(0, 0, 6, 0));
        footer.setPadding(new Insets(1, 18, 1, 18));
        footer.setMaxWidth(Double.MAX_VALUE);

        VBox bottom = new VBox(2, footer, signOut);
        bottom.setPadding(new Insets(0, 0, 3, 0));
        bottom.setFillWidth(true);
        s.setBottom(bottom);

        return s;
    }

    private Label label(String text, String css) {
        Label label = new Label(text);
        label.getStyleClass().add(css);
        return label;
    }

    private HBox footerCreditRow(String caption, String value) {
        Label captionLabel = label(caption + " ", "app-footer-credit-caption");
        captionLabel.setTextFill(Color.web("#a9bdcc"));
        Label valueLabel = label(value, "app-footer-credit-value");
        valueLabel.setTextFill(Color.WHITE);
        valueLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: 800;");
        HBox row = new HBox(0, captionLabel, valueLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    private Button nav(String text, boolean selected) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setPrefHeight(44);
        button.getStyleClass().add("nav-button");
        if (selected) button.getStyleClass().add("nav-selected");
        return button;
    }

    private ImageView createTopbarLogo() {
        ImageView imageView = new ImageView();
        var stream = getClass().getResourceAsStream("/com/aks/waybill/images/wasp-wordmark.png");
        if (stream != null) {
            imageView.setImage(new Image(stream));
        }
        // Small, crisp wordmark in the header. The source remains high-resolution.
        imageView.setFitWidth(96);
        imageView.setFitHeight(28);
        imageView.setPreserveRatio(true);
        imageView.getStyleClass().add("topbar-logo");
        return imageView;
    }

    private HBox topbar() {
        HBox h = new HBox(10);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(10, 22, 10, 22));
        h.getStyleClass().add("topbar");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label user = label(currentUser.displayName() + "  •  " + currentUser.role(), "user-pill");
        Button minimize = new Button("—");
        minimize.getStyleClass().add("window-control-button");
        minimize.setTooltip(new Tooltip("Minimize"));
        minimize.setOnAction(e -> { if (getScene() != null) ((Stage) getScene().getWindow()).setIconified(true); });
        Button close = new Button("✕");
        close.getStyleClass().addAll("window-control-button", "window-close-button");
        close.setTooltip(new Tooltip("Close W.A.S.P"));
        close.setOnAction(e -> requestExit(exit));
        h.getChildren().addAll(topbarLogo, topbarDivider, pageTitle, spacer, clockLabel, user, minimize, close);
        updateClock();
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateClock()));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
        return h;
    }

    private void updateClock() {
        clockLabel.setText(java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy  hh:mm:ss a")));
    }

    private void installShortcuts() {
        setFocusTraversable(true);
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.N) { showNewWaybill(); event.consume(); }
            else if (event.isControlDown() && event.getCode() == KeyCode.L) { showSavedWaybills(); event.consume(); }
            else if (event.isControlDown() && event.getCode() == KeyCode.DIGIT1) { showDashboard(); event.consume(); }
            else if (event.isControlDown() && event.getCode() == KeyCode.DIGIT2) { showNewWaybill(); event.consume(); }
            else if (event.isControlDown() && event.getCode() == KeyCode.DIGIT3) { showSavedWaybills(); event.consume(); }
            else if (event.isControlDown() && event.getCode() == KeyCode.DIGIT4) { showSavedData(); event.consume(); }
            else if (event.isControlDown() && event.getCode() == KeyCode.S) {
                if (!content.getChildren().isEmpty() && content.getChildren().get(0) instanceof WaybillFormView form) form.handleShortcutSave();
                event.consume();
            }
            else if (event.isControlDown() && event.getCode() == KeyCode.P) {
                if (!content.getChildren().isEmpty() && content.getChildren().get(0) instanceof WaybillFormView form) form.handleShortcutPrint();
                event.consume();
            }
            else if (event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.A) { showAbout(); event.consume(); }
        });
    }

    public void requestLogout(Runnable afterLogout) {
        if (lifecycleActionInProgress) return;
        lifecycleActionInProgress = true;
        if (!confirmSimpleAction("Log out", "Are you sure you want to log out of W.A.S.P?")) {
            lifecycleActionInProgress = false;
            return;
        }
        if (hasUnsavedChanges()) {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setTitle("Unsaved Changes");
            a.setHeaderText("You have unsaved changes.");
            a.setContentText("Would you like to save before logging out?");
            ButtonType save = new ButtonType("Save and Log Out", ButtonBar.ButtonData.OK_DONE);
            ButtonType discard = new ButtonType("Log Out Without Saving", ButtonBar.ButtonData.OTHER);
            ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            a.getButtonTypes().setAll(discard, save, cancel);
            if (getScene()!=null) a.initOwner(getScene().getWindow());
            ButtonType result=a.showAndWait().orElse(cancel);
            if(result==cancel) { lifecycleActionInProgress = false; return; }
            if(result==save && !saveCurrentForm()) { lifecycleActionInProgress = false; return; }
        }
        if (!MainConfirmBackup("log out")) { lifecycleActionInProgress = false; return; }
        SessionContext.clear();
        lifecycleActionInProgress = false;
        afterLogout.run();
    }

    public void requestExit(Runnable finalExit) {
        if (lifecycleActionInProgress) return;
        lifecycleActionInProgress = true;
        if (!confirmSimpleAction("Exit W.A.S.P", "Are you sure you want to exit W.A.S.P?")) {
            lifecycleActionInProgress = false;
            return;
        }
        if (hasUnsavedChanges()) {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setTitle("Unsaved Changes");
            a.setHeaderText("You have unsaved changes.");
            a.setContentText("Would you like to save before exiting?");
            ButtonType save = new ButtonType("Save and Exit", ButtonBar.ButtonData.OK_DONE);
            ButtonType discard = new ButtonType("Exit Without Saving", ButtonBar.ButtonData.OTHER);
            ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            a.getButtonTypes().setAll(discard, save, cancel);
            if (getScene()!=null) a.initOwner(getScene().getWindow());
            ButtonType result=a.showAndWait().orElse(cancel);
            if(result==cancel) { lifecycleActionInProgress = false; return; }
            if(result==save && !saveCurrentForm()) { lifecycleActionInProgress = false; return; }
        }
        if (!MainConfirmBackup("exit")) { lifecycleActionInProgress = false; return; }
        lifecycleActionInProgress = false;
        finalExit.run();
    }

    private boolean confirmSimpleAction(String title, String message) {
        ButtonType confirm = new ButtonType(title.equals("Log out") ? "Log Out" : "Exit", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert a=new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.getButtonTypes().setAll(confirm, cancel);
        // Dialog dimensions are calculated centrally by Main so every W.A.S.P
        // alert has the same sizing rules and stable buttons.
        if(getScene()!=null) a.initOwner(getScene().getWindow());
        return a.showAndWait().orElse(cancel)==confirm;
    }

    private boolean hasUnsavedChanges() {
        return !content.getChildren().isEmpty() && content.getChildren().get(0) instanceof WaybillFormView form && form.hasUnsavedChanges();
    }

    private boolean saveCurrentForm() {
        if (!content.getChildren().isEmpty() && content.getChildren().get(0) instanceof WaybillFormView form) return form.saveForExit();
        return true;
    }

    private boolean MainConfirmBackup(String action) {
        return com.aks.waybill.Main.confirmBackup(getScene()==null?null:getScene().getWindow(), action);
    }

    private void showAuditLog() {
        if (!SessionContext.isAdmin()) return;
        activate(auditButton, "Audit Log", new AuditLogView());
    }

    private void showAbout() {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("About W.A.S.P");
        dialog.getDialogPane().getStyleClass().add("wasp-about-dialog");
        dialog.setHeaderText("W.A.S.P (Waybill Automation & Shipping Platform)");

        VBox about = new VBox(14);
        about.getStyleClass().add("about-content");
        about.setFillWidth(true);
        about.setPrefWidth(650);
        about.setMaxWidth(650);

        about.getChildren().addAll(
                aboutParagraph("W.A.S.P (Waybill Automation & Shipping Platform) is a dedicated waybill management solution developed to simplify, standardize, and accelerate the waybill creation process within logistics operations. Designed with practicality and ease of use in mind, W.A.S.P enables users to generate professional waybills efficiently while reducing repetitive data entry, minimizing documentation errors, and improving overall productivity."),
                aboutParagraph("The platform allows for the organized management of customer, sender, receiver, and carrier information, enabling faster preparation of shipping documents and ensuring consistency across all waybills. By automating routine processes and centralizing essential data, W.A.S.P helps users save time, maintain accuracy, and enhance operational efficiency in day-to-day shipment handling activities."),
                aboutParagraph("W.A.S.P has been developed as an operational tool to support the documentation requirements of modern logistics services while providing a simple, reliable, and user-friendly experience for both administrative and operational staff."),
                aboutSectionTitle("Ownership"),
                aboutParagraph("W.A.S.P is a proprietary application owned and operated by AKS Global Logistics. All rights relating to the software, branding, business processes, and operational use of the application are reserved by AKS Global Logistics."),
                aboutMetadata("Developed by", "Deepesh V. Thampi"),
                aboutMetadata("Owned by", "AKS Global Logistics"),
                aboutMetadata("Version", "2.0.0")
        );

        dialog.getDialogPane().setContent(about);
        if(getScene()!=null) dialog.initOwner(getScene().getWindow());
        dialog.showAndWait();
    }

    private Label aboutParagraph(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("about-paragraph");
        label.setWrapText(true);
        label.setPrefWidth(650);
        label.setMaxWidth(650);
        label.setTextOverrun(OverrunStyle.CLIP);
        return label;
    }

    private Label aboutSectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("about-section-title");
        return label;
    }

    private HBox aboutMetadata(String key, String value) {
        Label keyLabel = new Label(key + ":");
        keyLabel.getStyleClass().add("about-metadata-key");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("about-metadata-value");
        HBox row = new HBox(5, keyLabel, valueLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("about-metadata-row");
        return row;
    }

    private void showDashboard() {
        navigateWithUnsavedChanges(() -> activate(dashboardButton, "Dashboard", home()));
    }

    private void showNewWaybill() {
        navigateWithUnsavedChanges(() -> activate(newWaybillButton, "New Waybill",
                new NewWaybillView(this::showSavedWaybills, this::showNewWaybill, this::showViewWaybill)));
    }

    private void showSavedWaybills() {
        navigateWithUnsavedChanges(() -> activate(savedWaybillsButton, "Saved Waybills",
                new SavedWaybillsView(this::showSavedWaybills, this::showViewWaybill, this::showEditWaybill)));
    }

    private void showViewWaybill(long waybillId) {
        navigateWithUnsavedChanges(() -> activate(savedWaybillsButton, "View Waybill",
                new ViewWaybillView(waybillId, this::showSavedWaybills)));
    }

    private void showEditWaybill(long waybillId) {
        navigateWithUnsavedChanges(() -> activate(savedWaybillsButton, "Edit Waybill",
                new EditWaybillView(waybillId, this::showSavedWaybills, this::showSavedWaybills)));
    }

    private void showSavedData() {
        navigateWithUnsavedChanges(() -> activate(companiesButton, "Saved Data", new SavedDataView()));
    }

    private void showDataExchange() {
        if (currentUser == null) return;
        // Clicking the already-active Data Exchange navigation item should not
        // reopen the re-authentication dialog or recreate the current screen.
        if (dataExchangeButton.getStyleClass().contains("nav-selected")) return;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Confirm Data Exchange Access");
        dialog.setHeaderText("Password confirmation required");
        if (getScene() != null && getScene().getWindow() != null) {
            dialog.initOwner(getScene().getWindow());
        }
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Label message = new Label("Data Exchange is a critical operation. Enter your current password to continue.");
        message.setWrapText(true);
        message.setAlignment(javafx.geometry.Pos.CENTER);
        message.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Current password");
        VBox fields = new VBox(14, message, passwordField);
        fields.setAlignment(javafx.geometry.Pos.CENTER);
        fields.setPrefWidth(500);
        fields.setPadding(new Insets(12, 8, 8, 8));
        message.setMaxWidth(Double.MAX_VALUE);
        message.setPrefWidth(500);
        passwordField.setMaxWidth(420);
        passwordField.setPrefWidth(420);
        passwordField.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        GridPane centeredPassword = new GridPane();
        centeredPassword.setAlignment(javafx.geometry.Pos.CENTER);
        centeredPassword.add(passwordField, 0, 0);
        fields.getChildren().setAll(message, centeredPassword);
        dialog.getDialogPane().setContent(fields);
        dialog.setResultConverter(button -> button);
        dialog.setOnShown(event -> passwordField.requestFocus());

        var result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;
        if (passwordField.getText() == null || passwordField.getText().isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Please enter your current password.", ButtonType.OK).showAndWait();
            return;
        }
        try {
            if (!new AuthService().verifyPassword(currentUser.id(), passwordField.getText())) {
                new Alert(Alert.AlertType.ERROR, "Incorrect password. Data Exchange was not opened.", ButtonType.OK).showAndWait();
                return;
            }
        } catch (RuntimeException ex) {
            new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK).showAndWait();
            return;
        }

        navigateWithUnsavedChanges(() -> activate(dataExchangeButton, "Data Exchange", new DataExchangeView()));
    }

    private void showSettings() {
        if (!SessionContext.isAdmin()) return;
        navigateWithUnsavedChanges(() -> activate(settingsButton, "Settings", new SettingsView()));
    }

    private void showUserManagement() {
        if (!SessionContext.isAdmin()) return;
        navigateWithUnsavedChanges(() -> activate(usersButton, "User Management", new UserManagementView()));
    }

    /**
     * Protects every in-application navigation away from a dirty New/Edit
     * Waybill form. This is intentionally separate from logout/exit, because
     * navigation should not trigger the backup reminder.
     */
    private void navigateWithUnsavedChanges(Runnable navigation) {
        if (!hasUnsavedChanges()) {
            navigation.run();
            return;
        }

        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle("Unsaved Changes");
        dialog.setHeaderText("You have unsaved changes.");
        dialog.setContentText("Would you like to save before leaving this waybill?");
        ButtonType save = new ButtonType("Save and Continue", ButtonBar.ButtonData.OK_DONE);
        ButtonType discard = new ButtonType("Leave Without Saving", ButtonBar.ButtonData.OTHER);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getButtonTypes().setAll(discard, save, cancel);
        if (getScene() != null) dialog.initOwner(getScene().getWindow());

        ButtonType result = dialog.showAndWait().orElse(cancel);
        if (result == cancel) return;
        if (result == save && !saveCurrentForm()) return;
        navigation.run();
    }

    private void activate(Button selected, String title, Node view) {
        for (Button button : new Button[]{dashboardButton, newWaybillButton, savedWaybillsButton, companiesButton, dataExchangeButton, settingsButton, usersButton, auditButton, aboutButton}) {
            button.getStyleClass().remove("nav-selected");
        }
        selected.getStyleClass().add("nav-selected");
        pageTitle.setText(title);
        content.getChildren().setAll(view);
    }

    private ScrollPane home() {
        VBox c = new VBox(4);
        c.setPadding(new Insets(8, 20, 4, 20));
        c.getStyleClass().add("content-area");
        c.setFillWidth(true);

        Label welcome = label("Good day, " + currentUser.displayName(), "page-heading");
        Label intro = label("Waybill Automation & Shipping Platform", "page-subheading");
        Label introDetail = label(SessionContext.isAdmin()
                ? "Manage transportation waybills, companies, users and report settings from one workspace."
                : "Create, manage and export your transportation waybills from one workspace.", "card-description");

        DashboardStats stats = loadStats();
        String userScope = SessionContext.isAdmin() ? "All users" : "Created by you";

        Label overviewTitle = label("Overview", "section-heading");
        GridPane countsRow = threeColumnRow();
        Node monthCard = stat("Waybills This Month", String.valueOf(stats.monthWaybills()),
                userScope, "stat-month");
        Node statusCard = waybillStatusStat(stats.draftWaybills(), stats.finalWaybills(), userScope);
        Node companiesCard = stat("Saved Companies", String.valueOf(stats.companies()),
                "Shipper + Consignee masters", "stat-companies");
        addThree(countsRow, monthCard, statusCard, companiesCard);

        Label quickActionsTitle = label("Quick Actions", "section-heading");
        GridPane actionsRow = threeColumnRow();
        Node newCard = action("New Waybill", "Create a new transportation waybill", "＋", "action-icon-orange", this::showNewWaybill);
        Node savedCard = action("Saved Waybills", "View, edit and generate PDF or Word reports", "▤", "action-icon-teal", this::showSavedWaybills);
        Node dataCard = action("Saved Data", "Manage reusable companies, carriers and locations", "▦", "action-icon-purple", this::showSavedData);
        addThree(actionsRow, newCard, savedCard, dataCard);

        Label administrationTitle = label("Administration", "section-heading");
        GridPane adminActions = twoColumnRow();
        if (SessionContext.isAdmin()) {
            Node settingsCard = action("Settings", "Manage report profile and application settings", "⚙", "action-icon-indigo", this::showSettings);
            Node usersCard = action("User Management", "Manage users, roles and waybill codes", "♟", "action-icon-teal", this::showUserManagement);
            addTwoEqual(adminActions, settingsCard, usersCard);
        }

        Label recentTitle = label("Recent Waybills", "section-heading");
        VBox recentCard = recentWaybills();

        // Preserve clear visual separation between section labels and their cards.
        VBox.setMargin(quickActionsTitle, new Insets(4, 0, 0, 0));
        VBox.setMargin(actionsRow, new Insets(3, 0, 1, 0));
        VBox.setMargin(administrationTitle, new Insets(4, 0, 0, 0));
        VBox.setMargin(adminActions, new Insets(3, 0, 1, 0));
        VBox.setMargin(recentTitle, new Insets(4, 0, 0, 0));
        VBox.setMargin(recentCard, new Insets(3, 0, 0, 0));

        c.getChildren().addAll(
                welcome,
                intro,
                introDetail,
                overviewTitle,
                countsRow,
                quickActionsTitle,
                actionsRow);

        if (SessionContext.isAdmin()) {
            c.getChildren().addAll(administrationTitle, adminActions);
        }

        Region recentSpacer = new Region();
        recentSpacer.setMinHeight(0);
        recentSpacer.setPrefHeight(0);
        c.getChildren().addAll(recentSpacer, recentTitle, recentCard);

        ScrollPane p = new ScrollPane(c);
        p.setFitToWidth(true);
        p.setFitToHeight(false);
        p.setPannable(false);
        p.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        p.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        p.setFocusTraversable(false);
        p.getStyleClass().add("content-scroll");
        c.setMinWidth(0);

        javafx.application.Platform.runLater(() -> p.setVvalue(0));
        return p;
    }

    private GridPane threeColumnRow() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(0);
        grid.setMaxWidth(Double.MAX_VALUE);
        grid.getStyleClass().add("dashboard-card-row");
        for (int i = 0; i < 3; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(33.333333);
            column.setHgrow(Priority.ALWAYS);
            column.setFillWidth(true);
            grid.getColumnConstraints().add(column);
        }
        return grid;
    }

    private void addThree(GridPane grid, Node first, Node second, Node third) {
        prepareGridCard(first);
        prepareGridCard(second);
        prepareGridCard(third);
        grid.add(first, 0, 0);
        grid.add(second, 1, 0);
        grid.add(third, 2, 0);
    }

    private GridPane fourColumnRow() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(0);
        grid.setMaxWidth(Double.MAX_VALUE);
        grid.getStyleClass().add("dashboard-card-row");
        for (int i = 0; i < 4; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(25);
            column.setHgrow(Priority.ALWAYS);
            column.setFillWidth(true);
            grid.getColumnConstraints().add(column);
        }
        return grid;
    }

    private void addFour(GridPane grid, Node first, Node second, Node third, Node fourth) {
        prepareGridCard(first);
        prepareGridCard(second);
        prepareGridCard(third);
        prepareGridCard(fourth);
        grid.add(first, 0, 0);
        grid.add(second, 1, 0);
        grid.add(third, 2, 0);
        grid.add(fourth, 3, 0);
    }

    private GridPane twoColumnRow() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(0);
        grid.setMaxWidth(Double.MAX_VALUE);
        grid.getStyleClass().add("dashboard-card-row");
        for (int i = 0; i < 2; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(50);
            column.setHgrow(Priority.ALWAYS);
            column.setFillWidth(true);
            grid.getColumnConstraints().add(column);
        }
        return grid;
    }

    private void addTwoEqual(GridPane grid, Node first, Node second) {
        prepareGridCard(first);
        prepareGridCard(second);
        grid.add(first, 0, 0);
        grid.add(second, 1, 0);
    }

    private void prepareGridCard(Node node) {
        if (node instanceof Region region) {
            region.setMinWidth(0);
            region.setPrefWidth(0);
            region.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(region, Priority.ALWAYS);
            GridPane.setFillWidth(region, true);
        }
    }

    private record DashboardStats(long monthWaybills, long draftWaybills, long finalWaybills, long companies) {}

    private DashboardStats loadStats() {
        LocalDate today = LocalDate.now();
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        WaybillService.WaybillPage month = WaybillService.findPageForCurrentUser(null, firstOfMonth, today, 0, 1);
        WaybillService.WaybillPage draft = WaybillService.findPageForCurrentUser(null, null, null, "DRAFT", 0, 1);
        WaybillService.WaybillPage finalized = WaybillService.findPageForCurrentUser(null, null, null, "FINAL", 0, 1);
        return new DashboardStats(month.totalRows(), draft.totalRows(), finalized.totalRows(), CompanyService.countAll());
    }

    private VBox stat(String title, String value, String hint, String variantStyle) {
        VBox box = new VBox(4);
        box.setMinWidth(0);
        box.setPrefHeight(96);
        box.setMinHeight(96);
        box.setMaxHeight(96);
        box.setPadding(new Insets(14, 16, 12, 16));
        box.getStyleClass().addAll("stat-card", variantStyle);

        Label valueLabel = label(value, "stat-number");
        Label titleLabel = label(title, "stat-title-line");
        Label hintLabel = label(hint, "stat-hint");
        hintLabel.setWrapText(true);
        box.getChildren().addAll(valueLabel, titleLabel, hintLabel);
        return box;
    }

    private VBox waybillStatusStat(long draftCount, long finalCount, String scope) {
        // The orange bar represents the Final share of all waybills (Final / (Draft + Final)).
        // It is intentionally kept inside the same compact 96px card as the other overview cards.
        VBox box = new VBox(4);
        box.setMinWidth(0);
        box.setPrefHeight(96);
        box.setMinHeight(96);
        box.setMaxHeight(96);
        box.setPadding(new Insets(10, 16, 9, 16));
        box.getStyleClass().addAll("stat-card", "stat-status");

        Label title = label("Draft / Final", "stat-title-line");
        Label scopeLabel = label(scope, "stat-scope");
        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        HBox titleRow = new HBox(8, title, titleSpacer, scopeLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        HBox values = new HBox(38);
        values.setAlignment(Pos.CENTER_LEFT);

        VBox draft = new VBox(0,
                label(String.valueOf(draftCount), "stat-status-value stat-draft-value"),
                label("Draft", "stat-status-label"));
        VBox finalBox = new VBox(0,
                label(String.valueOf(finalCount), "stat-status-value stat-final-value"),
                label("Final", "stat-status-label"));
        values.getChildren().addAll(draft, finalBox);

        long total = draftCount + finalCount;
        double finalRatio = total == 0 ? 0.0 : (double) finalCount / total;
        // Use a simple JavaFX region-based ratio bar instead of ProgressBar.
        // This makes the filled portion render consistently across JavaFX skins.
        StackPane progressTrack = new StackPane();
        progressTrack.setMaxWidth(Double.MAX_VALUE);
        progressTrack.setPrefHeight(7);
        progressTrack.setMinHeight(7);
        progressTrack.setMaxHeight(7);
        progressTrack.getStyleClass().add("draft-final-progress");

        // Base track represents Draft; orange overlay represents Final.
        Rectangle draftTrack = new Rectangle();
        draftTrack.setHeight(7);
        draftTrack.setFill(Color.web("#9aa9b6"));
        draftTrack.setMouseTransparent(true);
        draftTrack.widthProperty().bind(progressTrack.widthProperty());
        StackPane.setAlignment(draftTrack, Pos.CENTER_LEFT);

        Rectangle progressFill = new Rectangle();
        progressFill.setHeight(7);
        progressFill.setFill(Color.web("#e99a2b"));
        progressFill.setMouseTransparent(true);
        progressFill.widthProperty().bind(progressTrack.widthProperty().multiply(finalRatio));
        StackPane.setAlignment(progressFill, Pos.CENTER_LEFT);
        progressTrack.getChildren().addAll(draftTrack, progressFill);

        String ratioText = Math.round(finalRatio * 100) + "% Final";
        progressTrack.setAccessibleText("Grey = Draft; Orange = Final; " + ratioText);
        Tooltip.install(progressTrack, new Tooltip("Grey = Draft | Orange = Final | " + ratioText + " of all waybills"));

        // Explicit legend keeps the Draft/Final meaning readable without
        // requiring the labels themselves to use the indicator colours.
        HBox legend = new HBox(14);
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.getStyleClass().add("draft-final-legend");
        legend.getChildren().addAll(
                legendItem("Draft", "draft-final-legend-draft"),
                legendItem("Final", "draft-final-legend-final")
        );

        box.getChildren().addAll(titleRow, values, progressTrack, legend);
        return box;
    }

    private HBox legendItem(String text, String markerStyle) {
        // Use an actual JavaFX shape rather than a styled Region so the colour
        // marker is reliably visible across JavaFX skins and CSS loading order.
        Circle marker = new Circle(4.5);
        // Set the actual paint directly so the legend colours are reliable
        // even if JavaFX CSS for Shape fills is overridden by the active skin.
        marker.setFill("draft-final-legend-draft".equals(markerStyle)
                ? Color.web("#9aa9b6")
                : Color.web("#e99a2b"));
        marker.setStroke("draft-final-legend-draft".equals(markerStyle)
                ? Color.web("#8796a3")
                : Color.web("#d4871e"));
        marker.setStrokeWidth(0.6);
        marker.setMouseTransparent(true);

        Label textLabel = label(text, "draft-final-legend-label");
        HBox item = new HBox(5, marker, textLabel);
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    private VBox action(String title, String description, String icon, String iconStyle, Runnable command) {
        VBox box = new VBox(5);
        box.setMinWidth(0);
        box.setPrefHeight(74);
        box.setMinHeight(74);
        box.setMaxHeight(74);
        box.setPadding(new Insets(7, 12, 6, 12));
        box.getStyleClass().add("action-card");

        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().addAll("action-icon-badge", iconStyle);
        iconLabel.setMinSize(30, 30);
        iconLabel.setPrefSize(30, 30);
        iconLabel.setMaxSize(30, 30);
        iconLabel.setAlignment(Pos.CENTER);

        Label titleLabel = label(title, "action-title-line");
        HBox titleRow = new HBox(8, iconLabel, titleLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label descriptionLabel = label(description, "action-description");
        descriptionLabel.setWrapText(true);
        box.getChildren().addAll(titleRow, descriptionLabel);
        box.setOnMouseClicked(e -> command.run());
        Tooltip.install(box, new Tooltip(title));
        return box;
    }

    private VBox recentWaybills() {
        VBox card = new VBox(0);
        card.getStyleClass().add("settings-card");

        WaybillService.WaybillPage page = WaybillService.findPageForCurrentUser(null, null, null, 0, 5);
        if (page.rows().isEmpty()) {
            card.setAlignment(Pos.CENTER);
            card.setPadding(new Insets(22, 20, 22, 20));
            card.setMinHeight(112);
            card.setPrefHeight(112);
            card.setMaxWidth(Double.MAX_VALUE);
            VBox emptyContent = new VBox(7);
            emptyContent.setAlignment(Pos.CENTER);

            Label emptyTitle = label("No saved waybills yet.", "dashboard-empty-state-title");
            Label emptyMessage = label("Create your first waybill to get started.", "dashboard-empty-state-message");
            Button newWaybill = new Button("＋ New Waybill");
            newWaybill.getStyleClass().add("primary-button");
            newWaybill.setPrefHeight(36);
            newWaybill.setMinWidth(140);
            newWaybill.setOnAction(e -> showNewWaybill());

            emptyContent.getChildren().addAll(emptyTitle, emptyMessage, newWaybill);
            card.getChildren().add(emptyContent);
            return card;
        }

        TableView<WaybillService.WaybillListRow> table = new TableView<>();
        table.getStyleClass().add("dashboard-recent-table");
        // Only five rows are shown on the dashboard. Give the table exactly
        // enough height for those rows so it does not introduce a nested
        // vertical scrollbar. The dashboard ScrollPane handles page scrolling.
        table.setFixedCellSize(34);
        // The dashboard deliberately displays at most five recent records.
        // Size the table for its header plus exactly those rows and suppress
        // the vertical scrollbar when there is no possible overflow.
        double tableHeight = 38 + (page.rows().size() * 34);
        table.setMinHeight(tableHeight);
        table.setPrefHeight(tableHeight);
        table.setMaxHeight(tableHeight);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getStyleClass().add("dashboard-recent-no-scrollbar");
        table.setPlaceholder(label("No saved waybills.", "empty-state"));

        TableColumn<WaybillService.WaybillListRow, String> number = column("Waybill No.", WaybillService.WaybillListRow::waybillNumber, 210);
        TableColumn<WaybillService.WaybillListRow, String> date = column("Date", r -> DISPLAY_DATE.format(r.waybillDate()), 110);
        TableColumn<WaybillService.WaybillListRow, String> shipper = column("Shipper / Consignor", WaybillService.WaybillListRow::shipperName, 190);
        TableColumn<WaybillService.WaybillListRow, String> consignee = column("Consignee / Receiver", WaybillService.WaybillListRow::consigneeName, 190);
        TableColumn<WaybillService.WaybillListRow, String> carrier = column("Carrier", WaybillService.WaybillListRow::carrierName, 160);
        TableColumn<WaybillService.WaybillListRow, Void> view = new TableColumn<>("Action");
        view.setPrefWidth(95);
        view.setCellFactory(col -> new TableCell<>() {
            private final Button button = new Button("View");
            {
                button.getStyleClass().add("secondary-button");
                button.setOnAction(e -> {
                    WaybillService.WaybillListRow row = getTableView().getItems().get(getIndex());
                    showViewWaybill(row.id());
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : button);
            }
        });

        table.getColumns().addAll(number, date, shipper, consignee, carrier, view);
        table.getItems().setAll(page.rows());
        card.getChildren().add(table);
        return card;
    }

    private <T> TableColumn<WaybillService.WaybillListRow, T> column(String title,
                                                                       java.util.function.Function<WaybillService.WaybillListRow, T> value,
                                                                       double width) {
        TableColumn<WaybillService.WaybillListRow, T> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new javafx.beans.property.SimpleObjectProperty<>(value.apply(cell.getValue())));
        return column;
    }
}
