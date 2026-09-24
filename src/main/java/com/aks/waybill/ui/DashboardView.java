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
    private final Label pageTitle = label("Dashboard", "topbar-title");

    private final Button dashboardButton = nav("▦  Dashboard", true);
    private final Button newWaybillButton = nav("＋  New Waybill", false);
    private final Button savedWaybillsButton = nav("▤  Saved Waybills", false);
    private final Button companiesButton = nav("▦  Saved Data", false);
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
        setTop(topbar());
        setCenter(content);
        showDashboard();
        installShortcuts();
    }

    private BorderPane sidebar() {
        BorderPane s = new BorderPane();
        s.setPrefWidth(255);
        s.setMinWidth(235);
        s.setMinHeight(0);
        s.getStyleClass().add("sidebar");

        var logoStream = getClass().getResourceAsStream("/com/aks/waybill/images/aks-logo.png");
        ImageView logo = new ImageView();
        if (logoStream != null) {
            logo.setImage(new Image(logoStream));
        }
        logo.setFitWidth(145);
        logo.setPreserveRatio(true);

        StackPane lb = new StackPane(logo);
        lb.setPadding(new Insets(2, 0, 14, 0));

        Label workspace = label("WORKSPACE", "sidebar-section");
        VBox top = new VBox(2, lb, workspace);
        top.setPadding(new Insets(18, 14, 0, 14));
        s.setTop(top);

        // Keep the sidebar hierarchy meaningful for every role.  About is
        // informational, not administrative, so it belongs in its own group.
        VBox navigation = new VBox(4);
        navigation.setFillWidth(true);
        navigation.setPadding(new Insets(0, 14, 8, 14));

        navigation.getChildren().addAll(
                dashboardButton,
                newWaybillButton,
                savedWaybillsButton,
                companiesButton);

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
        signOut.setOnAction(e -> requestLogout(logout));

        Label footer1 = label("W.A.S.P", "app-footer-title");
        Label footer2 = label("Simplifying Waybill Creation. Improving Operational Efficiency.", "app-footer");
        footer2.setWrapText(true);
        footer2.setTextOverrun(OverrunStyle.CLIP);
        Label footer3 = label("© AKS Global Logistics. All Rights Reserved.", "app-footer");
        footer3.setWrapText(true);
        footer3.setTextOverrun(OverrunStyle.CLIP);
        Label footer4 = label("Developed by: Deepesh V. Thampi", "app-footer");
        footer4.setWrapText(true);
        footer4.setTextOverrun(OverrunStyle.CLIP);
        Label footer5 = label("Owned by: AKS Global Logistics", "app-footer");
        footer5.setWrapText(true);
        footer5.setTextOverrun(OverrunStyle.CLIP);

        VBox footer = new VBox(2, footer1, footer2, footer3, footer4, footer5);
        footer.setPadding(new Insets(6, 18, 4, 18));
        footer.setMaxWidth(Double.MAX_VALUE);

        VBox bottom = new VBox(2, footer, signOut);
        bottom.setPadding(new Insets(0, 0, 10, 0));
        bottom.setFillWidth(true);
        s.setBottom(bottom);

        return s;
    }

    private Label label(String text, String css) {
        Label label = new Label(text);
        label.getStyleClass().add(css);
        return label;
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

    private HBox topbar() {
        HBox h = new HBox(18);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(16, 28, 16, 28));
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
        h.getChildren().addAll(pageTitle, spacer, clockLabel, user, minimize, close);
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
            a.getButtonTypes().setAll(save, discard, cancel);
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
            a.getButtonTypes().setAll(save, discard, cancel);
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
        Alert dialog=new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("About W.A.S.P");
        dialog.setHeaderText("W.A.S.P (Waybill Automation & Shipping Platform)");
        dialog.setContentText("W.A.S.P (Waybill Automation & Shipping Platform) is a dedicated waybill management solution developed to simplify, standardize, and accelerate the waybill creation process within logistics operations. Designed with practicality and ease of use in mind, W.A.S.P enables users to generate professional waybills efficiently while reducing repetitive data entry, minimizing documentation errors, and improving overall productivity.\n\n" +
                "The platform allows for the organized management of customer, sender, receiver, and carrier information, enabling faster preparation of shipping documents and ensuring consistency across all waybills. By automating routine processes and centralizing essential data, W.A.S.P helps users save time, maintain accuracy, and enhance operational efficiency in day-to-day shipment handling activities.\n\n" +
                "W.A.S.P has been developed as an operational tool to support the documentation requirements of modern logistics services while providing a simple, reliable, and user-friendly experience for both administrative and operational staff.\n\n" +
                "W.A.S.P is a proprietary application owned and operated by AKS Global Logistics. All rights relating to the software, branding, business processes, and operational use of the application are reserved by AKS Global Logistics.\n\n" +
                "Developed by: Deepesh V. Thampi\nOwned by: AKS Global Logistics\n\nVersion 1.4.0");
        if(getScene()!=null) dialog.initOwner(getScene().getWindow());
        dialog.showAndWait();
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
        dialog.getButtonTypes().setAll(save, discard, cancel);
        if (getScene() != null) dialog.initOwner(getScene().getWindow());

        ButtonType result = dialog.showAndWait().orElse(cancel);
        if (result == cancel) return;
        if (result == save && !saveCurrentForm()) return;
        navigation.run();
    }

    private void activate(Button selected, String title, Node view) {
        for (Button button : new Button[]{dashboardButton, newWaybillButton, savedWaybillsButton, companiesButton, settingsButton, usersButton, auditButton, aboutButton}) {
            button.getStyleClass().remove("nav-selected");
        }
        selected.getStyleClass().add("nav-selected");
        pageTitle.setText(title);
        content.getChildren().setAll(view);
    }

    private ScrollPane home() {
        VBox c = new VBox(7);
        c.setPadding(new Insets(8, 22, 12, 22));
        c.getStyleClass().add("content-area");
        c.setFillWidth(true);

        Label welcome = label("Good day, " + currentUser.displayName(), "page-heading");
        Label intro = label("Waybill Automation & Shipping Platform", "page-subheading");
        Label introDetail = label(SessionContext.isAdmin()
                ? "Manage transportation waybills, companies, users and report settings from one workspace."
                : "Create, manage and export your transportation waybills from one workspace.", "card-description");

        DashboardStats stats = loadStats();

        Label overviewTitle = label("Overview & Quick Actions", "section-heading");

        // Keep the six overview cards in two deliberate rows.  The first row
        // contains the three count cards; the second row contains the three
        // primary actions.  Each row always has three equal-width cards on the
        // desktop workspace, so the cards can become wider without becoming taller.
        GridPane countsRow = threeColumnRow();
        Node monthCard = stat("Waybills this month", String.valueOf(stats.monthWaybills()),
                SessionContext.isAdmin() ? "All users" : "Created by you");
        Node totalCard = stat("Total saved waybills", String.valueOf(stats.totalWaybills()),
                SessionContext.isAdmin() ? "Across all users" : "Created by you");
        Node companiesCard = stat("Saved companies", String.valueOf(stats.companies()),
                "Shipper + Consignee masters");
        addThree(countsRow, monthCard, totalCard, companiesCard);

        GridPane actionsRow = threeColumnRow();
        Node newCard = action("New Waybill", "Create a new transportation waybill", "＋", this::showNewWaybill);
        Node savedCard = action("Saved Waybills", "View, edit and generate PDF or Word reports", "▤", this::showSavedWaybills);
        Node dataCard = action("Saved Data", "Manage reusable companies, carriers and locations", "▦", this::showSavedData);
        addThree(actionsRow, newCard, savedCard, dataCard);

        Label administrationTitle = label("Administration", "section-heading");
        GridPane adminActions = twoColumnRow();
        if (SessionContext.isAdmin()) {
            Node settingsCard = action("Settings", "Manage report profile and application settings", "⚙", this::showSettings);
            Node usersCard = action("User Management", "Manage users, roles and waybill codes", "♟", this::showUserManagement);
            addTwoEqual(adminActions, settingsCard, usersCard);
        }

        Label recentTitle = label("Recent Waybills", "section-heading");
        VBox recentCard = recentWaybills();

        c.getChildren().addAll(welcome, intro, introDetail, overviewTitle, countsRow, actionsRow);
        if (SessionContext.isAdmin()) {
            c.getChildren().addAll(administrationTitle, adminActions);
        }

        // A small deliberate separation keeps Recent Waybills visually distinct
        // while the compact rows above preserve enough vertical space for it to
        // remain fully visible on the maximized desktop workspace.
        Region recentSpacer = new Region();
        recentSpacer.setMinHeight(5);
        recentSpacer.setPrefHeight(5);
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

    private record DashboardStats(long monthWaybills, long totalWaybills, long companies) {}

    private DashboardStats loadStats() {
        LocalDate today = LocalDate.now();
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        WaybillService.WaybillPage month = WaybillService.findPageForCurrentUser(null, firstOfMonth, today, 0, 1);
        WaybillService.WaybillPage total = WaybillService.findPageForCurrentUser(null, null, null, 0, 1);
        return new DashboardStats(month.totalRows(), total.totalRows(), CompanyService.countAll());
    }

    private VBox stat(String title, String value, String hint) {
        VBox box = new VBox(4);
        box.setMinWidth(165);
        box.setPrefWidth(198);
        box.setMaxWidth(230);
        box.setMinHeight(70);
        box.setPrefHeight(70);
        box.setMaxHeight(70);
        box.setPadding(new Insets(8, 12, 7, 12));
        box.getStyleClass().add("stat-card");
        box.getChildren().addAll(
                label(value + "  " + title, "stat-value-line"),
                label(hint, "stat-hint"));
        return box;
    }

    private VBox action(String title, String description, String icon, Runnable command) {
        VBox box = new VBox(4);
        box.setMinWidth(165);
        box.setPrefWidth(198);
        box.setMaxWidth(230);
        box.setMinHeight(70);
        box.setPrefHeight(70);
        box.setMaxHeight(70);
        box.setPadding(new Insets(8, 12, 7, 12));
        box.getStyleClass().add("action-card");
        Label titleLabel = label(icon + "  " + title, "action-title-line");
        Label descriptionLabel = label(description, "action-description");
        descriptionLabel.setWrapText(true);
        box.getChildren().addAll(titleLabel, descriptionLabel);
        box.setOnMouseClicked(e -> command.run());
        Tooltip.install(box, new Tooltip(title));
        return box;
    }

    private VBox recentWaybills() {
        VBox card = new VBox(0);
        card.getStyleClass().add("settings-card");

        WaybillService.WaybillPage page = WaybillService.findPageForCurrentUser(null, null, null, 0, 5);
        if (page.rows().isEmpty()) {
            Label empty = label("No saved waybills yet. Create your first waybill using New Waybill.", "empty-state");
            card.getChildren().add(empty);
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
        double tableHeight = 34 + (page.rows().size() * 34) + 6;
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
