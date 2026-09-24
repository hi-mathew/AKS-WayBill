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

        double totalButtonWidth = 0;
        int buttonCount = 0;
        for (Button button : buttons) {
            String text = button.getText() == null ? "" : button.getText().trim();
            if (text.isBlank()) continue;

            Font font = button.getFont();
            Text measure = new Text(text);
            measure.setFont(font);
            // Text width + generous horizontal room for the W.A.S.P button padding.
            // A fixed width is then applied so hover/focus pseudo-classes cannot
            // change the button geometry.
            double width = Math.max(112, Math.ceil(measure.getLayoutBounds().getWidth() + 48));
            setStableDialogButtonSize(button, width);
            totalButtonWidth += width;
            buttonCount++;
        }

        double gaps = Math.max(0, buttonCount - 1) * 12;
        double requiredButtonWidth = totalButtonWidth + gaps;

        // JavaFX's DialogPane/ButtonBar has its own internal layout constraints.
        // Give it deliberate breathing room instead of relying on the pane's
        // calculated minimum, which can leave the last button clipped at the
        // right edge (especially for short two-button confirmation dialogs).
        double safetyMargin = switch (buttonCount) {
            case 0, 1 -> 120;
            case 2 -> 150;
            case 3 -> 170;
            default -> 190;
        };
        double requiredDialogWidth = requiredButtonWidth + safetyMargin;

        double availableScreenWidth = Math.max(900,
                Screen.getPrimary().getVisualBounds().getWidth() - 80);
        double dialogWidth = Math.min(availableScreenWidth,
                Math.max(560, requiredDialogWidth));

        pane.setMinWidth(dialogWidth);
        pane.setPrefWidth(dialogWidth);
        pane.setMaxWidth(availableScreenWidth);

        // The ButtonBar skin can retain its own preferred width. Keep the bar and
        // its internal container at the same width as the dialog so the final
        // button can never be laid out beyond the DialogPane's right edge.
        pane.lookup(".button-bar");
        var buttonBar = pane.lookup(".button-bar");
        if (buttonBar instanceof javafx.scene.layout.Region bar) {
            bar.setMinWidth(dialogWidth);
            bar.setPrefWidth(dialogWidth);
            bar.setMaxWidth(dialogWidth);
        }
        var buttonContainer = pane.lookup(".button-bar .container");
        if (buttonContainer instanceof javafx.scene.layout.Region container) {
            double containerWidth = Math.max(0, dialogWidth - 32);
            container.setMinWidth(containerWidth);
            container.setPrefWidth(containerWidth);
            container.setMaxWidth(containerWidth);
        }

        javafx.application.Platform.runLater(() -> {
            pane.applyCss();

            var currentButtons = pane.lookupAll(".button").stream()
                    .filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .toList();
            double total = 0;
            int count = 0;
            for (Button button : currentButtons) {
                String text = button.getText() == null ? "" : button.getText().trim();
                if (text.isBlank()) continue;
                Text measure = new Text(text);
                measure.setFont(button.getFont());
                double width = Math.max(112, Math.ceil(measure.getLayoutBounds().getWidth() + 48));
                setStableDialogButtonSize(button, width);
                total += width;
                count++;
            }

            double required = total + Math.max(0, count - 1) * 12;
            double margin = switch (count) {
                case 0, 1 -> 120;
                case 2 -> 150;
                case 3 -> 170;
                default -> 190;
            };
            double width = Math.min(availableScreenWidth, Math.max(560, required + margin));
            pane.setMinWidth(width);
            pane.setPrefWidth(width);
            pane.setMaxWidth(availableScreenWidth);

            var bar = pane.lookup(".button-bar");
            if (bar instanceof javafx.scene.layout.Region region) {
                region.setMinWidth(width);
                region.setPrefWidth(width);
                region.setMaxWidth(width);
            }
            var container = pane.lookup(".button-bar .container");
            if (container instanceof javafx.scene.layout.Region region) {
                double containerWidth = Math.max(0, width - 32);
                region.setMinWidth(containerWidth);
                region.setPrefWidth(containerWidth);
                region.setMaxWidth(containerWidth);
            }

            // DialogPane width is not always propagated to the decorated Stage
            // immediately. Enforce the calculated minimum at the window level too.
            if (pane.getScene() != null && pane.getScene().getWindow() instanceof Stage stage) {
                stage.setMinWidth(width);
                if (stage.getWidth() < width) stage.setWidth(width);
                centerDialog(stage);
            }
        });
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
        backup.getButtonTypes().setAll(backupNow, continueWithout, cancel);
        // This is an informational reminder with choices, not a yes/no question.
        // Use the information badge instead of the generic confirmation '?'.
        setDialogGraphic(backup.getDialogPane(), "i", "wasp-dialog-info");
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
