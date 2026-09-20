package com.aks.waybill.ui;

import com.aks.waybill.report.WaybillReportService;
import com.aks.waybill.service.WaybillService;
import javafx.stage.FileChooser;
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
        File file = chooser.showSaveDialog(owner);
        if (file == null) return;
        if (!file.getName().toLowerCase().endsWith(pdf ? ".pdf" : ".docx")) {
            file = new File(file.getAbsolutePath() + (pdf ? ".pdf" : ".docx"));
        }
        try {
            Path output = file.toPath();
            if (pdf) WaybillReportService.generatePdf(details, output);
            else WaybillReportService.generateWord(details, output);
            alert(javafx.scene.control.Alert.AlertType.INFORMATION, "Document Generated", "The " + (pdf ? "PDF" : "Word document") + " was generated successfully.\n\n" + output, owner);
        } catch (Exception e) {
            alert(javafx.scene.control.Alert.AlertType.ERROR, "Document Generation Failed", e.getMessage() == null ? "Unable to generate the document." : e.getMessage(), owner);
        }
    }

    private static String safeFileName(String value) {
        if (value == null || value.isBlank()) return "waybill";
        return value.replaceAll("[\\\\/:*?\"<>|]", "-");
    }

    private static void alert(javafx.scene.control.Alert.AlertType type, String title, String message, Window owner) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(type);
        alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(message);
        if (owner != null) alert.initOwner(owner);
        alert.showAndWait();
    }
}
