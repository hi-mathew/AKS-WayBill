package com.aks.waybill.ui;

import com.aks.waybill.security.AuthService;
import com.aks.waybill.security.SessionContext;
import com.aks.waybill.service.CompanyService;
import com.aks.waybill.service.WaybillService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Main application shell and dashboard. */
public class DashboardView extends BorderPane {
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final AuthService.UserRecord currentUser;
    private final Runnable logout;
    private final StackPane content = new StackPane();
    private final Label pageTitle = label("Dashboard", "topbar-title");

    private final Button dashboardButton = nav("▦  Dashboard", true);
    private final Button newWaybillButton = nav("＋  New Waybill", false);
    private final Button savedWaybillsButton = nav("▤  Saved Waybills", false);
    private final Button companiesButton = nav("♙  Companies", false);
    private final Button settingsButton = nav("⚙  Settings", false);
    private final Button usersButton = nav("♟  User Management", false);

    public DashboardView(AuthService.UserRecord currentUser, Runnable logout) {
        this.currentUser = currentUser;
        this.logout = logout;
        getStyleClass().add("app-root");
        setLeft(sidebar());
        setTop(topbar());
        setCenter(content);
        showDashboard();
    }

    private VBox sidebar() {
        VBox s = new VBox(8);
        s.setPrefWidth(245);
        s.setPadding(new Insets(24, 16, 20, 16));
        s.getStyleClass().add("sidebar");

        var logoStream = getClass().getResourceAsStream("/com/aks/waybill/images/aks-logo.png");
        ImageView logo = new ImageView();
        if (logoStream != null) {
            logo.setImage(new Image(logoStream));
        }
        logo.setFitWidth(155);
        logo.setPreserveRatio(true);
        StackPane lb = new StackPane(logo);
        lb.setPadding(new Insets(4, 0, 24, 0));

        Label workspace = label("WORKSPACE", "sidebar-section");
        Label administration = label("ADMINISTRATION", "sidebar-section");
        Button signOut = nav("⇥  Sign out", false);

        dashboardButton.setOnAction(e -> showDashboard());
        newWaybillButton.setOnAction(e -> showNewWaybill());
        savedWaybillsButton.setOnAction(e -> showSavedWaybills());
        companiesButton.setOnAction(e -> showCompanies());
        settingsButton.setOnAction(e -> showSettings());
        usersButton.setOnAction(e -> showUserManagement());
        usersButton.setVisible(SessionContext.isAdmin());
        usersButton.setManaged(SessionContext.isAdmin());
        settingsButton.setVisible(SessionContext.isAdmin());
        settingsButton.setManaged(SessionContext.isAdmin());
        signOut.setOnAction(e -> logout.run());

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        s.getChildren().addAll(lb, workspace, dashboardButton, newWaybillButton,
                savedWaybillsButton, companiesButton, spacer, administration,
                settingsButton, usersButton, signOut);
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
        h.getChildren().addAll(pageTitle, spacer, user);
        return h;
    }

    private void showDashboard() {
        activate(dashboardButton, "Dashboard", home());
    }

    private void showNewWaybill() {
        activate(newWaybillButton, "New Waybill", new NewWaybillView(this::showSavedWaybills, this::showNewWaybill, this::showViewWaybill));
    }

    private void showSavedWaybills() {
        activate(savedWaybillsButton, "Saved Waybills", new SavedWaybillsView(this::showSavedWaybills, this::showViewWaybill, this::showEditWaybill));
    }

    private void showViewWaybill(long waybillId) {
        activate(savedWaybillsButton, "View Waybill", new ViewWaybillView(waybillId, this::showSavedWaybills));
    }

    private void showEditWaybill(long waybillId) {
        activate(savedWaybillsButton, "Edit Waybill", new EditWaybillView(waybillId, this::showSavedWaybills, this::showSavedWaybills));
    }

    private void showCompanies() {
        activate(companiesButton, "Companies", new CompaniesView());
    }

    private void showSettings() {
        if (!SessionContext.isAdmin()) return;
        activate(settingsButton, "Settings", new SettingsView());
    }

    private void showUserManagement() {
        if (!SessionContext.isAdmin()) return;
        activate(usersButton, "User Management", new UserManagementView());
    }

    private void activate(Button selected, String title, Node view) {
        for (Button button : new Button[]{dashboardButton, newWaybillButton, savedWaybillsButton, companiesButton, settingsButton, usersButton}) {
            button.getStyleClass().remove("nav-selected");
        }
        selected.getStyleClass().add("nav-selected");
        pageTitle.setText(title);
        content.getChildren().setAll(view);
    }

    private ScrollPane home() {
        VBox c = new VBox(22);
        c.setPadding(new Insets(30));
        c.getStyleClass().add("content-area");

        Label welcome = label("Good day, " + currentUser.displayName(), "page-heading");
        Label intro = label(SessionContext.isAdmin()
                ? "Manage transportation waybills, companies, users and report settings from one workspace."
                : "Create, manage and export your transportation waybills from one workspace.", "page-subheading");

        DashboardStats stats = loadStats();
        HBox statsRow = new HBox(16,
                stat(String.valueOf(stats.monthWaybills()), "Waybills this month", SessionContext.isAdmin() ? "All users" : "Created by you"),
                stat(String.valueOf(stats.totalWaybills()), "Total saved waybills", SessionContext.isAdmin() ? "Across all users" : "Created by you"),
                stat(String.valueOf(stats.companies()), "Saved companies", "Shared company master"));
        statsRow.setFillHeight(true);

        Label quickTitle = label("Quick Actions", "section-heading");
        HBox actions = new HBox(14);
        actions.setFillHeight(true);
        actions.getChildren().addAll(
                action("New Waybill", "Create a new transportation waybill", "＋", this::showNewWaybill),
                action("Saved Waybills", "View, edit and generate PDF or Word reports", "▤", this::showSavedWaybills),
                action("Companies", "Manage reusable shipper and consignee companies", "♙", this::showCompanies));
        for (Node node : actions.getChildren()) HBox.setHgrow(node, Priority.ALWAYS);

        HBox adminActions = new HBox(14);
        if (SessionContext.isAdmin()) {
            adminActions.getChildren().addAll(
                    action("Settings", "Manage report profile and application settings", "⚙", this::showSettings),
                    action("User Management", "Manage users, roles and waybill codes", "♟", this::showUserManagement));
            for (Node node : adminActions.getChildren()) HBox.setHgrow(node, Priority.ALWAYS);
        }

        Label recentTitle = label("Recent Waybills", "section-heading");
        VBox recentCard = recentWaybills();

        c.getChildren().addAll(welcome, intro, statsRow, quickTitle, actions);
        if (SessionContext.isAdmin()) {
            c.getChildren().addAll(label("Administration", "section-heading"), adminActions);
        }
        c.getChildren().addAll(recentTitle, recentCard);

        ScrollPane p = new ScrollPane(c);
        p.setFitToWidth(true);
        p.getStyleClass().add("content-scroll");
        return p;
    }

    private record DashboardStats(long monthWaybills, long totalWaybills, long companies) {}

    private DashboardStats loadStats() {
        LocalDate today = LocalDate.now();
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        WaybillService.WaybillPage month = WaybillService.findPageForCurrentUser(null, firstOfMonth, today, 0, 1);
        WaybillService.WaybillPage total = WaybillService.findPageForCurrentUser(null, null, null, 0, 1);
        CompanyService.CompanyPage companies = CompanyService.findPage(null, 0, 1);
        return new DashboardStats(month.totalRows(), total.totalRows(), companies.totalRows());
    }

    private VBox stat(String value, String title, String hint) {
        VBox box = new VBox(7);
        box.setMinHeight(112);
        box.setPrefHeight(112);
        box.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setPadding(new Insets(18));
        box.getStyleClass().add("stat-card");
        box.getChildren().addAll(label(value, "stat-value"), label(title, "stat-title"), label(hint, "stat-hint"));
        return box;
    }

    private VBox action(String title, String description, String icon, Runnable command) {
        VBox box = new VBox(9);
        box.setMinHeight(130);
        box.setPrefHeight(130);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setPadding(new Insets(18));
        box.getStyleClass().add("action-card");
        Label iconLabel = label(icon, "action-icon");
        Label titleLabel = label(title, "action-title");
        Label descriptionLabel = label(description, "action-description");
        descriptionLabel.setWrapText(true);
        box.getChildren().addAll(iconLabel, titleLabel, descriptionLabel);
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
        table.setPrefHeight(Math.min(290, 52 + page.rows().size() * 42));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
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
