package com.aks.waybill.ui;

import com.aks.waybill.report.WaybillReportService;
import com.aks.waybill.config.AppPaths;
import com.aks.waybill.service.WaybillService;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;

/** UI helper for saving generated waybill documents. */
public final class WaybillReportActions {
    private WaybillReportActions() {}

    public static void generatePdf(Window owner, long waybillId) {
        generate(owner, waybillId, true);
    }

    public static void generateWord(Window owner, long waybillId) {
        generate(owner, waybillId, false);
    }

    private static void generate(Window owner, long waybillId, boolean pdf) {
        WaybillService.WaybillDetails details = WaybillService.findById(waybillId);
        if (details == null) {
            alert(javafx.scene.control.Alert.AlertType.ERROR, "Generate Document", "The selected waybill could not be found.", owner);
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle(pdf ? "Save Waybill PDF" : "Save Waybill Word Document");
        chooser.setInitialFileName(safeFileName(details.waybillNumber()) + (pdf ? ".pdf" : ".docx"));
        FileChooser.ExtensionFilter filter = pdf
                ? new FileChooser.ExtensionFilter("PDF Document (*.pdf)", "*.pdf")
                : new FileChooser.ExtensionFilter("Word Document (*.docx)", "*.docx");
        chooser.getExtensionFilters().add(filter);
        Path initialDir = AppPaths.defaultSaveDirectory();
        if (java.nio.file.Files.isDirectory(initialDir)) chooser.setInitialDirectory(initialDir.toFile());
        File file = chooser.showSaveDialog(owner);
        if (file == null) return;
        if (!file.getName().toLowerCase().endsWith(pdf ? ".pdf" : ".docx")) {
            file = new File(file.getAbsolutePath() + (pdf ? ".pdf" : ".docx"));
        }

        Path output = file.toPath();
        Stage progressDialog = createProgressDialog(owner, pdf);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (pdf) {
                    WaybillReportService.generatePdf(details, output);
                } else {
                    WaybillReportService.generateWord(details, output);
                }
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            dismissProgressDialog(progressDialog);
            alert(javafx.scene.control.Alert.AlertType.INFORMATION,
                    "Document Generated",
                    "The " + (pdf ? "PDF" : "Word document") + " was generated successfully.\n\n"
                            + "File name: " + output.getFileName() + "\nSaved to: " + output.getParent(),
                    owner);
        });

        task.setOnFailed(event -> {
            dismissProgressDialog(progressDialog);
            Throwable failure = task.getException();
            String message = failure == null || failure.getMessage() == null
                    ? "Unable to generate the document."
                    : failure.getMessage();
            alert(javafx.scene.control.Alert.AlertType.ERROR,
                    "Document Generation Failed", message, owner);
        });

        progressDialog.show();
        Thread worker = new Thread(task, pdf ? "wasp-pdf-generation" : "wasp-word-generation");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Dismisses the generation dialog after the background task has completed.
     *
     * The progress dialog deliberately consumes the user close request while
     * generation is running.  A programmatic hide alone is not sufficient on
     * all JavaFX dialog/window implementations because the close-request
     * handler can remain attached to the dialog lifecycle.  Remove that guard
     * first, then hide and close the window explicitly.
     */
    private static void dismissProgressDialog(Stage stage) {
        if (stage == null) return;
        // This is a real Stage rather than a JavaFX Dialog.  That avoids the
        // Dialog nested-event-loop/close-request lifecycle which can leave a
        // modal progress window alive after the report has completed.
        stage.setOnCloseRequest(null);
        if (stage.isShowing()) stage.hide();
        stage.close();
    }

    private static Stage createProgressDialog(Window owner, boolean pdf) {
        DialogPane pane = new DialogPane();
        pane.getStyleClass().add("wasp-dialog-pane");
        pane.setHeaderText(pdf ? "Generating PDF report" : "Generating Word document");

        ProgressIndicator progress = new ProgressIndicator();
        progress.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        progress.setPrefSize(48, 48);
        progress.setMinSize(48, 48);
        progress.setMaxSize(48, 48);

        Label message = new Label(pdf
                ? "Creating the report and converting it to PDF..."
                : "Creating the Word document...\nPlease wait.");
        message.setWrapText(true);
        message.setMaxWidth(360);
        message.setAlignment(Pos.CENTER);

        VBox content = new VBox(14, progress, message);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(8, 12, 12, 12));
        content.setPrefWidth(430);

        pane.setContent(content);
        pane.getButtonTypes().clear();

        StackPane circle = new StackPane();
        circle.getStyleClass().addAll("wasp-dialog-icon", "wasp-dialog-info");
        circle.setMinSize(34, 34);
        circle.setPrefSize(34, 34);
        circle.setMaxSize(34, 34);
        circle.setTranslateY(8);
        Label glyph = new Label("i");
        glyph.getStyleClass().add("wasp-dialog-icon-glyph");
        glyph.setMinSize(34, 34);
        glyph.setPrefSize(34, 34);
        glyph.setMaxSize(34, 34);
        glyph.setAlignment(Pos.CENTER);
        circle.getChildren().add(glyph);
        pane.setGraphic(circle);

        Stage stage = new Stage();
        stage.setTitle(pdf ? "Generating PDF" : "Generating Word Document");
        stage.initModality(Modality.WINDOW_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setResizable(false);
        javafx.scene.Scene scene = new javafx.scene.Scene(pane);
        if (owner != null && owner.getScene() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> event.consume());
        stage.setOnShown(event -> {
            pane.applyCss();
            stage.sizeToScene();
            if (owner != null) {
                stage.setX(owner.getX() + Math.max(0, (owner.getWidth() - stage.getWidth()) / 2));
                stage.setY(owner.getY() + Math.max(0, (owner.getHeight() - stage.getHeight()) / 2));
            }
        });
        return stage;
    }

    private static String safeFileName(String value) {
        if (value == null || value.isBlank()) return "waybill";
        return value.replaceAll("[\\\\/:*?\"<>|]", "-");
    }

    private static void alert(javafx.scene.control.Alert.AlertType type, String title, String message, Window owner) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().setMinWidth(560);
        alert.getDialogPane().setPrefWidth(560);
        if (type == javafx.scene.control.Alert.AlertType.INFORMATION) {
            StackPane circle = new StackPane();
            circle.getStyleClass().addAll("wasp-dialog-icon", "wasp-dialog-info");
            circle.setMinSize(34, 34);
            circle.setPrefSize(34, 34);
            circle.setMaxSize(34, 34);
            circle.setTranslateY(8);
            Label glyph = new Label("i");
            glyph.getStyleClass().add("wasp-dialog-icon-glyph");
            glyph.setMinSize(34, 34);
            glyph.setPrefSize(34, 34);
            glyph.setMaxSize(34, 34);
            glyph.setAlignment(Pos.CENTER);
            circle.getChildren().add(glyph);
            alert.getDialogPane().setGraphic(circle);
        }
        if (owner != null) alert.initOwner(owner);
        alert.showAndWait();
    }
}
