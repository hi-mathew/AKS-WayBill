package com.aks.waybill;

import com.aks.waybill.db.Database;
import com.aks.waybill.logging.WaspLogger;
import com.aks.waybill.security.SessionContext;
import com.aks.waybill.service.BackupService;
import com.aks.waybill.ui.DashboardView;
import com.aks.waybill.ui.LoginView;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.stage.Window;

/** Application entry point and top-level window lifecycle controller. */
public class Main extends Application {
    private Stage stage;

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        WaspLogger.initialize();
        WaspLogger.info("Starting W.A.S.P 1.4.0");
        installDialogStyling();
        try {
            Database.initialize();
            WaspLogger.info("Database initialization completed successfully.");
        } catch (RuntimeException exception) {
            WaspLogger.error("Database initialization failed.", exception);
            throw exception;
        }
        stage.setTitle("W.A.S.P");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setResizable(true);
        stage.getIcons().clear();
        var iconStream = getClass().getResourceAsStream("/com/aks/waybill/images/wasp-logo.png");
        if (iconStream != null) stage.getIcons().add(new javafx.scene.image.Image(iconStream));
        stage.setOnCloseRequest(this::handleWindowClose);
        showLogin();
        stage.show();
    }

    public void showLogin() {
        SessionContext.clear();
        var view = new LoginView(this::showDashboard);
        var scene = new Scene(view);
        applyStyles(scene);
        // The login window is intentionally restored to a normal size.  The
        // dashboard will maximize again after every successful login.  Keeping
        // this state explicit avoids JavaFX retaining a stale maximized state
        // across the logout -> login scene transition.
        stage.setMaximized(false);
        stage.setScene(scene);
        fitStage(0.86, 0.84, 1120, 680);
        stage.centerOnScreen();
    }

    private void showDashboard(com.aks.waybill.security.AuthService.UserRecord user) {
        var view = new DashboardView(user, this::showLogin, this::exitApplication);
        var scene = new Scene(view);
        applyStyles(scene);
        stage.setScene(scene);
        // W.A.S.P is a desktop workspace application.  Apply maximization on
        // the next JavaFX pulse so it is reliable both on the first login and
        // after returning from the logout -> login scene transition.
        javafx.application.Platform.runLater(() -> {
            if (stage.isShowing()) {
                stage.setMaximized(true);
                stage.toFront();
            }
        });
    }


    /** Apply W.A.S.P visual styling to every standard JavaFX dialog used by the application. */
    private void installDialogStyling() {
        Window.getWindows().addListener((javafx.collections.ListChangeListener<Window>) change -> {
            while (change.next()) {
                if (!change.wasAdded()) continue;
                for (Window window : change.getAddedSubList()) {
                    if (window instanceof javafx.stage.Stage dialogStage
                            && dialogStage.getScene() != null
                            && dialogStage.getScene().getRoot() instanceof javafx.scene.control.DialogPane pane) {
                        styleDialogPane(pane);
                    }
                }
            }
        });
    }

    private void styleDialogPane(javafx.scene.control.DialogPane pane) {
        var css = getClass().getResource("/com/aks/waybill/css/app.css");
        if (css != null && !pane.getStylesheets().contains(css.toExternalForm())) {
            pane.getStylesheets().add(css.toExternalForm());
        }
        if (!pane.getStyleClass().contains("wasp-dialog-pane")) {
            pane.getStyleClass().add("wasp-dialog-pane");
        }
        // Replace the stock JavaFX alert graphic with a small W.A.S.P-styled badge.
        // JavaFX does not always expose the built-in confirmation graphic with a
        // predictable CSS class, so use the DialogPane's alert-type style class
        // first and fall back to the graphic classes when necessary.
        var existingGraphic = pane.getGraphic();
        String symbol = "i";
        String badgeStyle = "wasp-dialog-info";
        var paneClasses = pane.getStyleClass();
        if (pane.getScene() != null
                && pane.getScene().getWindow() instanceof javafx.stage.Stage dialogStage
                && "Backup Reminder".equals(dialogStage.getTitle())) {
            // Backup Reminder uses a confirmation alert for its three choices,
            // but its meaning is informational rather than a yes/no question.
            symbol = "i";
            badgeStyle = "wasp-dialog-info";
        } else if (paneClasses.contains("confirmation")) {
            symbol = "?";
            badgeStyle = "wasp-dialog-question";
        } else if (paneClasses.contains("error")) {
            symbol = "!";
            badgeStyle = "wasp-dialog-error";
        } else if (paneClasses.contains("warning")) {
            symbol = "!";
            badgeStyle = "wasp-dialog-warning";
        } else if (existingGraphic != null) {
            var classes = existingGraphic.getStyleClass();
            if (classes.stream().anyMatch(c -> c.contains("question"))) {
                symbol = "?";
                badgeStyle = "wasp-dialog-question";
            } else if (classes.stream().anyMatch(c -> c.contains("error"))) {
                symbol = "!";
                badgeStyle = "wasp-dialog-error";
            } else if (classes.stream().anyMatch(c -> c.contains("warning"))) {
                symbol = "!";
                badgeStyle = "wasp-dialog-warning";
            }
        }
        // Keep confirmation dialogs as questions by default.  The shared backup
        // reminder is a three-action informational prompt, so it explicitly
        // overrides the icon after construction.
        var badge = createDialogBadge(symbol, badgeStyle);
        // JavaFX places a DialogPane graphic at the top of the content area.
        // For confirmation dialogs, give the badge a small downward optical
        // offset so it sits comfortably below the title/header boundary.
        if ("?".equals(symbol)) {
            badge.setTranslateY(10);
        }
        pane.setGraphic(badge);
    }

    /** Creates a consistently centered W.A.S.P dialog badge. */
    private javafx.scene.layout.StackPane createDialogBadge(String symbol, String styleClass) {
        var circle = new javafx.scene.layout.StackPane();
        circle.getStyleClass().addAll("wasp-dialog-icon", styleClass);
        circle.setMinSize(34, 34);
        circle.setPrefSize(34, 34);
        circle.setMaxSize(34, 34);
        var glyph = new javafx.scene.control.Label(symbol);
        glyph.getStyleClass().add("wasp-dialog-icon-glyph");
        // Let the label occupy the full badge so JavaFX centers the glyph
        // within the actual 34x34 icon box instead of centering only its
        // font line bounds (which makes the ? appear too close to the top).
        glyph.setMinSize(34, 34);
        glyph.setPrefSize(34, 34);
        glyph.setMaxSize(34, 34);
        glyph.setAlignment(javafx.geometry.Pos.CENTER);
        javafx.scene.layout.StackPane.setAlignment(glyph, javafx.geometry.Pos.CENTER);
        circle.getChildren().add(glyph);
        return circle;
    }

    private static void setDialogGraphic(javafx.scene.control.DialogPane pane, String symbol, String styleClass) {
        var circle = new javafx.scene.layout.StackPane();
        circle.getStyleClass().addAll("wasp-dialog-icon", styleClass);
        circle.setMinSize(34, 34);
        circle.setPrefSize(34, 34);
        circle.setMaxSize(34, 34);
        var glyph = new javafx.scene.control.Label(symbol);
        glyph.getStyleClass().add("wasp-dialog-icon-glyph");
        glyph.setMinSize(34, 34);
        glyph.setPrefSize(34, 34);
        glyph.setMaxSize(34, 34);
        glyph.setAlignment(javafx.geometry.Pos.CENTER);
        javafx.scene.layout.StackPane.setAlignment(glyph, javafx.geometry.Pos.CENTER);
        circle.getChildren().add(glyph);
        pane.setGraphic(circle);
    }

    private void applyStyles(Scene scene) {
        var css = getClass().getResource("/com/aks/waybill/css/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
    }

    private void fitStage(double widthRatio, double heightRatio, double preferredMinWidth, double preferredMinHeight) {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        double width = Math.max(preferredMinWidth, bounds.getWidth() * widthRatio);
        double height = Math.max(preferredMinHeight, bounds.getHeight() * heightRatio);
        width = Math.min(width, bounds.getWidth());
        height = Math.min(height, bounds.getHeight());
        stage.setWidth(width);
        stage.setHeight(height);
    }

    private void handleWindowClose(WindowEvent event) {
        if (stage.getScene() != null && stage.getScene().getRoot() instanceof DashboardView dashboard) {
            event.consume();
            dashboard.requestExit(this::exitApplication);
            return;
        }
        if (stage.getScene() != null && stage.getScene().getRoot() instanceof LoginView login) {
            event.consume();
            login.requestClose(this::exitApplication);
            return;
        }
        exitApplication();
    }

    private void exitApplication() {
        WaspLogger.info("W.A.S.P shutdown requested.");
        stage.setOnCloseRequest(null);
        SessionContext.clear();
        javafx.application.Platform.exit();
    }

    /** Shared backup reminder used by the application shell. */
    public static boolean confirmBackup(javafx.stage.Window owner, String action) {
        Alert backup = new Alert(Alert.AlertType.CONFIRMATION);
        backup.setTitle("Backup Reminder");
        backup.setHeaderText("Before you " + action);
        backup.setContentText("Have you backed up today's data? It is recommended to create a backup before logging out or closing the application.");
        ButtonType backupNow = new ButtonType("Backup Now", ButtonBar.ButtonData.OK_DONE);
        ButtonType continueWithout = new ButtonType("Continue Without Backup", ButtonBar.ButtonData.OTHER);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        backup.getButtonTypes().setAll(backupNow, continueWithout, cancel);
        backup.getDialogPane().setPrefWidth(700);
        backup.getDialogPane().setMinWidth(700);
        // This is an informational reminder with choices, not a yes/no question.
        // Use the information badge instead of the generic confirmation '?'.
        setDialogGraphic(backup.getDialogPane(), "i", "wasp-dialog-info");
        Button continueButton = (Button) backup.getDialogPane().lookupButton(continueWithout);
        continueButton.setMinWidth(240);
        continueButton.setPrefWidth(240);
        continueButton.setMaxWidth(260);
        if (owner != null) backup.initOwner(owner);
        var result = backup.showAndWait().orElse(cancel);
        if (result == cancel) return false;
        if (result == backupNow) {
            try {
                BackupService.backupTo(BackupService.defaultBackupPath());
                Alert done = new Alert(Alert.AlertType.INFORMATION, "Today's database backup was created successfully.", ButtonType.OK);
                if (owner != null) done.initOwner(owner);
                done.showAndWait();
                return true;
            } catch (Exception ex) {
                Alert error = new Alert(Alert.AlertType.ERROR, "Backup failed: " + (ex.getMessage() == null ? "Unknown error." : ex.getMessage()), ButtonType.OK);
                if (owner != null) error.initOwner(owner);
                error.showAndWait();
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) { launch(args); }
}
