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
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/** Application entry point and top-level window lifecycle controller. */
public class Main extends Application {
    private Stage stage;

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        WaspLogger.initialize();
        WaspLogger.info("Starting W.A.S.P 1.4.0");
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
        stage.setScene(scene);
        fitStage(0.86, 0.84, 1120, 680);
        stage.centerOnScreen();
    }

    private void showDashboard(com.aks.waybill.security.AuthService.UserRecord user) {
        var view = new DashboardView(user, this::showLogin, this::exitApplication);
        var scene = new Scene(view);
        applyStyles(scene);
        stage.setScene(scene);
        fitStage(0.90, 0.88, 1100, 650);
        stage.centerOnScreen();
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
