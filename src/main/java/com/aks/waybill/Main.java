package com.aks.waybill;

import com.aks.waybill.db.Database;
import com.aks.waybill.logging.WaspLogger;
import com.aks.waybill.security.SessionContext;
import com.aks.waybill.service.BackupService;
import com.aks.waybill.service.SettingsService;
import com.aks.waybill.ui.DashboardView;
import com.aks.waybill.ui.LoginView;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.Node;
import javafx.scene.text.Text;
import javafx.scene.text.Font;
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
        // Give every dialog button enough width for its complete label.
        // The default JavaFX DialogPane may shrink long labels such as
        // "Exit Without Saving" when the dialog content itself is short.
        // Size from the actual label so no action text is clipped or replaced
        // visually by an ellipsis.
        // Dialog button types are often added after the DialogPane window is
        // created. Resize both now and whenever the button list changes so the
        // labels are never measured before the actual buttons exist.
        pane.getButtonTypes().addListener((javafx.collections.ListChangeListener<ButtonType>) change ->
                javafx.application.Platform.runLater(() -> sizeDialogButtons(pane)));
        javafx.application.Platform.runLater(() -> sizeDialogButtons(pane));
        // TextInputDialog creates and lays out its editor as part of the dialog
        // skin. Reapply the sizing policy after the window is actually shown so
        // the editor cannot be stretched by the final skin/layout pass.
        if (pane.getScene() != null && pane.getScene().getWindow() instanceof Stage dialogStage) {
            dialogStage.addEventHandler(WindowEvent.WINDOW_SHOWN, event -> sizeDialogButtons(pane));
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
        // JavaFX places DialogPane graphics very close to the title/header
        // boundary. Give the badge a small downward optical offset so the
        // entire circle sits comfortably inside the dialog content area.
        // Confirmation questions need a slightly larger offset because the
        // question-mark glyph is visually heavier near the top of its circle.
        if ("?".equals(symbol)) {
            badge.setTranslateY(10);
        } else {
            badge.setTranslateY(8);
        }
        pane.setGraphic(badge);
        // JavaFX can position Dialog instances slightly off-center when a custom
        // graphic/header changes the computed dialog size. Re-center every W.A.S.P
        // dialog after it is actually shown, relative to its owning application window.
        // The stage variable used above is a pattern variable scoped only to that
        // conditional expression, so obtain the dialog window explicitly here.
        if (pane.getScene() != null && pane.getScene().getWindow() instanceof Stage dialogStage) {
            dialogStage.addEventHandler(WindowEvent.WINDOW_SHOWN, event -> centerDialog(dialogStage));
            if (dialogStage.isShowing()) {
                javafx.application.Platform.runLater(() -> centerDialog(dialogStage));
            }
        }
    }


    /**
     * Applies one sizing policy to every W.A.S.P dialog. The size is calculated
     * from the actual button text instead of being hard-coded per dialog. This
     * prevents long labels from being clipped and prevents hover/focus states
     * from changing the button geometry.
     */
    private static void sizeDialogButtons(javafx.scene.control.DialogPane pane) {
        pane.applyCss();

        var buttons = pane.lookupAll(".button").stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .toList();

        double commonButtonWidth = 112;
        int buttonCount = 0;
        for (Button button : buttons) {
            String text = button.getText() == null ? "" : button.getText().trim();
            if (text.isBlank()) continue;
            Text measure = new Text(text);
            measure.setFont(button.getFont());
            commonButtonWidth = Math.max(commonButtonWidth,
                    Math.ceil(measure.getLayoutBounds().getWidth() + 48));
            buttonCount++;
        }

        /*
         * Do not fight ButtonBarSkin with arbitrary child/container widths.
         * ButtonBar already provides the correct uniform sizing mechanism; the
         * previous implementation left the OS-specific ButtonBar ordering and
         * its spacer logic active, which is what produced the large gap between
         * the first and second buttons in some three-button dialogs.
         */
        var buttonBarNode = pane.lookup(".button-bar");
        if (buttonBarNode instanceof ButtonBar buttonBar) {
            buttonBar.setButtonOrder(ButtonBar.BUTTON_ORDER_NONE);
            buttonBar.setButtonMinWidth(commonButtonWidth);
            for (Node node : buttonBar.getButtons()) {
                if (node instanceof Button button) {
                    ButtonBar.setButtonUniformSize(button, true);
                    setStableDialogButtonSize(button, commonButtonWidth);
                }
            }
        } else {
            for (Button button : buttons) {
                setStableDialogButtonSize(button, commonButtonWidth);
            }
        }

        double requiredButtonWidth = commonButtonWidth * buttonCount
                + Math.max(0, buttonCount - 1) * 12;
        double safetyMargin = switch (buttonCount) {
            case 0, 1 -> 120;
            case 2 -> 150;
            case 3 -> 170;
            default -> 190;
        };

        double availableScreenWidth = Math.max(900,
                Screen.getPrimary().getVisualBounds().getWidth() - 80);
        double dialogWidth = Math.min(availableScreenWidth,
                Math.max(560, requiredButtonWidth + safetyMargin));
        if (pane.getStyleClass().contains("wasp-about-dialog")) {
            dialogWidth = Math.min(availableScreenWidth, Math.max(dialogWidth, 760));
        }

        pane.setMinWidth(dialogWidth);
        pane.setPrefWidth(dialogWidth);
        pane.setMaxWidth(availableScreenWidth);

        // Explicitly keep the ButtonBar compact and predictable. ButtonBarSkin
        // will now position the buttons in list order with its normal spacing;
        // there is no OS-specific spacer between action groups.
        if (buttonBarNode instanceof ButtonBar buttonBar) {
            buttonBar.setMinWidth(dialogWidth);
            buttonBar.setPrefWidth(dialogWidth);
            buttonBar.setMaxWidth(dialogWidth);
            buttonBar.applyCss();
        }

        // TextInputDialog uses a GridPane containing the prompt and editor.
        // Limit the editor width so it does not run into the dialog's right
        // edge and leave a consistent visual margin.
        var textFields = pane.lookupAll(".text-field").stream()
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .toList();
        for (TextField field : textFields) {
            constrainDialogTextField(field);
        }

        // Values captured by the runLater lambda must be final/effectively final.
        final double finalButtonWidth = commonButtonWidth;
        final double finalDialogWidth = dialogWidth;

        javafx.application.Platform.runLater(() -> {
            pane.applyCss();
            var currentButtonBar = pane.lookup(".button-bar");
            if (currentButtonBar instanceof ButtonBar bar) {
                bar.setButtonOrder(ButtonBar.BUTTON_ORDER_NONE);
                bar.setButtonMinWidth(finalButtonWidth);
                for (Node node : bar.getButtons()) {
                    if (node instanceof Button button) {
                        ButtonBar.setButtonUniformSize(button, true);
                        setStableDialogButtonSize(button, finalButtonWidth);
                    }
                }
            }

            var currentFields = pane.lookupAll(".text-field").stream()
                    .filter(TextField.class::isInstance)
                    .map(TextField.class::cast)
                    .toList();
            for (TextField field : currentFields) {
                constrainDialogTextField(field);
            }

            if (pane.getScene() != null && pane.getScene().getWindow() instanceof Stage dialogStage) {
                dialogStage.setMinWidth(finalDialogWidth);
                if (dialogStage.getWidth() < finalDialogWidth) dialogStage.setWidth(finalDialogWidth);
                centerDialog(dialogStage);
            }
        });
    }

    /**
     * Keeps TextInputDialog editors at a controlled width. TextInputDialog's
     * internal GridPane uses horizontal growth, which can otherwise stretch the
     * editor all the way to the dialog edge. The inline CSS and GridPane growth
     * settings are applied together so the limit remains effective after the
     * dialog skin performs its layout. The 400px logical width also leaves a
     * comfortable physical margin on standard Windows DPI scaling.
     */
    private static void constrainDialogTextField(TextField field) {
        field.setMinWidth(300);
        field.setPrefWidth(400);
        field.setMaxWidth(400);
        field.setMinHeight(38);
        field.setPrefHeight(38);
        field.setMaxHeight(38);
        field.setStyle(
                "-fx-min-width: 300px;" +
                "-fx-pref-width: 400px;" +
                "-fx-max-width: 400px;" +
                "-fx-min-height: 38px;" +
                "-fx-pref-height: 38px;" +
                "-fx-max-height: 38px;"
        );
        GridPane.setHgrow(field, Priority.NEVER);
        GridPane.setFillWidth(field, false);
    }

    private static void setStableDialogButtonSize(Button button, double width) {
        button.setMinWidth(width);
        button.setPrefWidth(width);
        button.setMaxWidth(width);
        button.setMinHeight(40);
        button.setPrefHeight(40);
        button.setMaxHeight(40);
        // Inline sizing wins over hover/focus CSS rules. Do not set background or
        // border here; the application stylesheet remains responsible for visual
        // states while these dimensions remain invariant.
        button.setStyle(
                "-fx-min-width: " + width + "px;" +
                "-fx-pref-width: " + width + "px;" +
                "-fx-max-width: " + width + "px;" +
                "-fx-min-height: 40px;" +
                "-fx-pref-height: 40px;" +
                "-fx-max-height: 40px;"
        );
    }

    private static void centerDialog(Stage dialogStage) {
        Window owner = dialogStage.getOwner();
        if (owner != null && owner.isShowing()) {
            dialogStage.setX(owner.getX() + (owner.getWidth() - dialogStage.getWidth()) / 2.0);
            dialogStage.setY(owner.getY() + (owner.getHeight() - dialogStage.getHeight()) / 2.0);
        } else {
            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            dialogStage.setX(bounds.getMinX() + (bounds.getWidth() - dialogStage.getWidth()) / 2.0);
            dialogStage.setY(bounds.getMinY() + (bounds.getHeight() - dialogStage.getHeight()) / 2.0);
        }
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
        // Keep explicitly-created informational graphics clear of the dialog
        // title/header boundary as well.
        circle.setTranslateY(8);
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
        backup.getButtonTypes().setAll(continueWithout, backupNow, cancel);
        setDialogGraphic(backup.getDialogPane(), "i", "wasp-dialog-info");
        if (owner != null) backup.initOwner(owner);
        var result = backup.showAndWait().orElse(cancel);
        if (result == cancel) return false;
        if (result == backupNow) {
            Dialog<Void> progress = new Dialog<>();
            progress.setTitle("Creating Backup");
            progress.setHeaderText("Creating and verifying database backup");
            if (owner != null) progress.initOwner(owner);
            ProgressIndicator indicator = new ProgressIndicator(ProgressIndicator.INDETERMINATE_PROGRESS);
            javafx.scene.control.Label message = new javafx.scene.control.Label("Please wait while W.A.S.P creates and verifies the backup.");
            message.setWrapText(true);
            javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(12, indicator, message);
            box.setAlignment(javafx.geometry.Pos.CENTER);
            box.setPadding(new javafx.geometry.Insets(20));
            progress.getDialogPane().setContent(box);
            progress.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
            progress.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);

            javafx.concurrent.Task<java.nio.file.Path> task = new javafx.concurrent.Task<>() {
                @Override protected java.nio.file.Path call() {
                    updateMessage("Creating database backup...");
                    java.nio.file.Path created = BackupService.backupTo(BackupService.defaultBackupPath());
                    if (SettingsService.isBackupRetentionEnabled()) {
                        updateMessage("Applying backup retention policy...");
                        BackupService.cleanupOldBackups(SettingsService.getBackupRetentionCount());
                    }
                    return created;
                }
            };
            task.messageProperty().addListener((obs, oldValue, newValue) -> message.setText(newValue));
            final boolean[] success = {false};
            task.setOnSucceeded(e -> {
                success[0] = true;
                progress.close();
            });
            task.setOnFailed(e -> progress.close());
            Thread thread = new Thread(task, "wasp-exit-backup");
            thread.setDaemon(true);
            thread.start();
            progress.showAndWait();

            if (success[0]) {
                Alert done = new Alert(Alert.AlertType.INFORMATION, "Today's database backup was created and verified successfully.", ButtonType.OK);
                if (owner != null) done.initOwner(owner);
                done.showAndWait();
                return true;
            }
            Throwable error = task.getException();
            Alert err = new Alert(Alert.AlertType.ERROR, "Backup failed: " + (error == null || error.getMessage() == null ? "Unknown error." : error.getMessage()), ButtonType.OK);
            if (owner != null) err.initOwner(owner);
            err.showAndWait();
            return false;
        }
        return true;
    }

    public static void main(String[] args) { launch(args); }
}
