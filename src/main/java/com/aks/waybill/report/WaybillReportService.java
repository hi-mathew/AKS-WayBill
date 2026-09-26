package com.aks.waybill.report;

import com.aks.waybill.logging.WaspLogger;

import com.aks.waybill.service.ReportProfileService;
import com.aks.waybill.service.TermsConditionService;
import com.aks.waybill.service.WaybillService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.docx4j.Docx4J;
import org.docx4j.convert.out.FOSettings;
import org.docx4j.fonts.IdentityPlusMapper;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Generates the approved AKS Global Logistics waybill using the supplied Word
 * template as the single visual source of truth.
 *
 * The Word report is produced by populating the existing template. The PDF is
 * then produced from that populated DOCX using docx4j's XSL-FO exporter so the
 * two formats share the same document structure and styling.
 */
public final class WaybillReportService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final String TEMPLATE_RESOURCE = "/com/aks/waybill/templates/transportation_waybill_template.docx";
    private static final String REPORT_LOGO_RESOURCE = "/com/aks/waybill/images/report-logo.png";
    private static final String TEMPLATE_LOGO_ENTRY = "word/media/image1.jpg";

    private WaybillReportService() {
    }

    public static void generateWord(WaybillService.WaybillDetails waybill, Path output) throws IOException {
        WaspLogger.debug("Starting Word report generation. output=" + output);
        if (waybill == null) {
            throw new IllegalArgumentException("Waybill data is required.");
        }

        Files.createDirectories(output.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(output.toAbsolutePath().getParent(), "aks-waybill-report-", ".docx");
        try {
            populateTemplate(waybill, temporary);
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            WaspLogger.info("Word report generated. waybillNumber=" + waybill.waybillNumber() + ", items=" + (waybill.items() == null ? 0 : waybill.items().size()) + ", output=" + output);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static void generatePdf(WaybillService.WaybillDetails waybill, Path output) throws IOException {
        WaspLogger.debug("Starting PDF report generation. output=" + output);
        if (waybill == null) {
            throw new IllegalArgumentException("Waybill data is required.");
        }

        Files.createDirectories(output.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(output.toAbsolutePath().getParent(), "aks-waybill-report-", ".docx");
        try {
            populateTemplate(waybill, temporary);
            convertDocxToPdf(temporary, output);
            if ("DRAFT".equalsIgnoreCase(waybill.status())) {
                addDraftPdfWatermark(output);
            }
            WaspLogger.info("PDF report generated. waybillNumber=" + waybill.waybillNumber() + ", items=" + (waybill.items() == null ? 0 : waybill.items().size()) + ", output=" + output);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void populateTemplate(WaybillService.WaybillDetails waybill, Path output) throws IOException {
        ReportProfileService.ReportProfile profile = ReportProfileService.get();

        try (InputStream template = requiredResource(TEMPLATE_RESOURCE);
             XWPFDocument document = new XWPFDocument(template)) {

            List<XWPFTable> tables = document.getTables();
            if (tables.size() < 6) {
                throw new IOException("The supplied waybill template structure is incomplete. Expected six tables.");
            }

            populateHeader(tables.get(0), waybill, profile);
            populateParties(tables.get(1), waybill);
            populateTransit(tables.get(2), waybill);
            populateItems(tables.get(3), waybill.items());
            populateSpecialInstructions(tables.get(3), waybill.specialInstructions(), waybill.hazardousMaterials());
            populateDeclarations(tables.get(4), waybill);
            populateTermsIntro(document, waybill);
            populateRemarks(document, waybill.remarks());
            populateTermsAndConditions(tables.get(5));
            if ("DRAFT".equalsIgnoreCase(waybill.status()) && document.getHeaderList().isEmpty()) {
                document.createHeader(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
            }
            populateFooter(document, profile);

            try (OutputStream out = Files.newOutputStream(output)) {
                document.write(out);
            }
        }

        replaceTemplateLogo(output);

        if ("DRAFT".equalsIgnoreCase(waybill.status())) {
            addDraftWatermark(output);
        }
    }

    private static void populateHeader(XWPFTable table, WaybillService.WaybillDetails waybill,
                                       ReportProfileService.ReportProfile profile) {
        XWPFTableCell company = cell(table, 0, 0);
        setRunText(company, 2, profile.crNumber());
        setRunText(company, 5, profile.vatNumber());

        XWPFTableCell meta = cell(table, 0, 1);
        // The approved template stores the header as these runs:
        // 0 = "WAYBILL NO: ", 1..3 = sample waybill number parts,
        // 4 = line break, 5 = "Date: ", 6 = sample date.
        // Keep the labels intact and replace only the dynamic values.
        setRunText(meta, 0, "WAYBILL NO: ");
        setRunText(meta, 1, waybill.waybillNumber());
        setRunText(meta, 2, "");
        setRunText(meta, 3, "");
        setRunText(meta, 5, "Date: ");
        setRunText(meta, 6, formatLongDate(waybill.waybillDate()));
    }

    private static void populateParties(XWPFTable table, WaybillService.WaybillDetails waybill) {
        WaybillService.CompanyData shipper = waybill.shipper();
        WaybillService.CompanyData consignee = waybill.consignee();
        populateParty(table, 0, 1, shipper);
        populateParty(table, 1, 1, consignee);
    }

    private static void populateParty(XWPFTable table, int column, int firstValueRow,
                                      WaybillService.CompanyData company) {
        String[] values = {
                safe(company == null ? null : company.companyName()),
                safe(company == null ? null : company.contactPerson()),
                safe(company == null ? null : company.address()),
                safe(company == null ? null : company.phoneNumber()),
                safe(company == null ? null : company.emailAddress())
        };
        for (int i = 0; i < values.length; i++) {
            replaceValueRun(cell(table, firstValueRow + i, column), values[i]);
        }
    }

    private static void populateTransit(XWPFTable table, WaybillService.WaybillDetails waybill) {
        replaceValueRun(cell(table, 1, 0), safe(waybill.carrierName()));
        replaceValueRun(cell(table, 1, 1), safe(waybill.originLoadingPoint()));
        replaceValueRun(cell(table, 2, 0), safe(waybill.driverName()));
        replaceValueRun(cell(table, 2, 1), safe(waybill.destinationUnloadingPoint()));
        replaceValueRun(cell(table, 3, 0), safe(waybill.vehicleTrailerNo()));
        replaceValueRun(cell(table, 3, 1), waybill.estimatedDeliveryDate() == null ? "" : formatLongDate(waybill.estimatedDeliveryDate()));
    }

    private static void populateItems(XWPFTable table, List<WaybillService.WaybillItemData> items) {
        double quantity = 0;
        double weight = 0;
        double volume = 0;

        int itemCount = items == null ? 0 : items.size();

        // The approved template contains three item rows followed by the totals
        // row and the special-instructions row. For additional items, insert new
        // rows into that SAME table immediately before the totals row. This keeps
        // the document structure intact and lets Word paginate one continuous
        // table naturally.
        if (itemCount > 3) {
            XWPFTableRow templateOddRow = table.getRows().get(1);
            XWPFTableRow templateEvenRow = table.getRows().get(2);
            int insertPosition = 4; // immediately before the original totals row

            for (int itemIndex = 3; itemIndex < itemCount; itemIndex++) {
                // Use POI's insertNewTableRow so the new row is registered in both
                // the underlying CTTbl and XWPFTable's row list. The previous
                // implementation used addRow() with a cloned XWPFTableRow; that
                // can leave the wrapper pointing at a detached CTRow, which is why
                // the generated report showed a blank fourth row containing the
                // old item number while the totals were still correct.
                XWPFTableRow templateRow = ((itemIndex + 1) % 2 == 0)
                        ? templateEvenRow : templateOddRow;
                XWPFTableRow insertedRow = table.insertNewTableRow(insertPosition++);

                for (int column = 0; column < templateRow.getTableCells().size(); column++) {
                    XWPFTableCell newCell = insertedRow.createCell();
                    XWPFTableCell templateCell = templateRow.getCell(column);

                    // Copy cell properties only. Keep the newly-created cell's
                    // XWPF wrapper/paragraph cache intact so setCellText() below
                    // writes to the actual row that belongs to the table.
                    if (templateCell.getCTTc().getTcPr() != null) {
                        newCell.getCTTc().setTcPr(
                                (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr)
                                        templateCell.getCTTc().getTcPr().copy());
                    }

                    XWPFParagraph newParagraph = newCell.getParagraphs().isEmpty()
                            ? newCell.addParagraph() : newCell.getParagraphs().get(0);
                    XWPFParagraph templateParagraph = templateCell.getParagraphs().isEmpty()
                            ? null : templateCell.getParagraphs().get(0);
                    if (templateParagraph != null && templateParagraph.getCTP().getPPr() != null) {
                        newParagraph.getCTP().setPPr(
                                (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr)
                                        templateParagraph.getCTP().getPPr().copy());
                    }
                }

                if (templateRow.getCtRow().getTrPr() != null) {
                    insertedRow.getCtRow().setTrPr(
                            (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTrPr)
                                    templateRow.getCtRow().getTrPr().copy());
                }
            }

            // Repeat the approved header when the SAME table flows to another
            // page, and keep each item row together.
            table.getRows().get(0).setRepeatHeader(true);
            for (int rowIndex = 1; rowIndex < 1 + itemCount; rowIndex++) {
                table.getRows().get(rowIndex).setCantSplitRow(true);
            }
        }

        // Populate all item rows in the now-expanded single table.
        for (int rowIndex = 0; rowIndex < itemCount; rowIndex++) {
            XWPFTableRow row = table.getRows().get(rowIndex + 1);
            WaybillService.WaybillItemData item = items.get(rowIndex);
            setCellText(row.getCell(0), String.valueOf(rowIndex + 1));
            setCellText(row.getCell(1), safe(item == null ? null : item.description()));
            setCellText(row.getCell(2), safe(item == null ? null : item.packageType()));
            setCellText(row.getCell(3), number(item == null ? null : item.quantity()));
            setCellText(row.getCell(4), number(item == null ? null : item.weightKg()));
            setCellText(row.getCell(5), number(item == null ? null : item.volumeM3()));

            if (item != null) {
                if (item.quantity() != null) quantity += item.quantity();
                if (item.weightKg() != null) weight += item.weightKg();
                if (item.volumeM3() != null) volume += item.volumeM3();
            }
        }

        // Clear unused template item rows when fewer than three items are supplied.
        for (int rowIndex = itemCount; rowIndex < 3; rowIndex++) {
            XWPFTableRow row = table.getRows().get(rowIndex + 1);
            setCellText(row.getCell(0), String.valueOf(rowIndex + 1));
            for (int column = 1; column < 6; column++) {
                setCellText(row.getCell(column), "");
            }
        }

        int totalsRowIndex = itemCount > 3 ? 1 + itemCount : 4;
        XWPFTableRow totals = table.getRows().get(totalsRowIndex);
        setCellText(totals.getCell(0), "");
        setCellText(totals.getCell(1), "TOTALS");
        setCellText(totals.getCell(2), "");
        setCellText(totals.getCell(3), number(quantity));
        setCellText(totals.getCell(4), number(weight));
        setCellText(totals.getCell(5), number(volume));

        // The special-instructions row follows the totals row. Its position moves
        // automatically when additional item rows are inserted above it.
        XWPFTableCell special = table.getRows().get(totalsRowIndex + 1).getCell(0);
        String instructions = extractRunText(special, 1);
        if (instructions == null) {
            replaceRunText(special, 1, "");
        }
    }

    private static void populateSpecialInstructions(XWPFTable table, String instructions, boolean hazardous) {
        XWPFTableCell cell = table.getRows().get(table.getRows().size() - 1).getCell(0);
        XWPFParagraph paragraph = cell.getParagraphs().get(0);
        if (paragraph.getRuns().size() >= 5) {
            XWPFRun instructionRun = paragraph.getRuns().get(1);
            instructionRun.setText(safe(instructions), 0);
            // Long handling instructions must remain within the approved Page 1
            // layout. Use a slightly smaller font for unusually long text rather
            // than allowing the declaration/signature area to be pushed to Page 2.
            // XWPFRun exposes font size through setFontSize(int). Do not access
            // the generated JAXB RPr#getSz API directly because the JAXB model
            // used by this project does not expose that method consistently.
            int instructionFontSize = instructions != null && instructions.length() > 300 ? 7 :
                    (instructions != null && instructions.length() > 180 ? 8 : 9);
            instructionRun.setFontSize(instructionFontSize);

            paragraph.getRuns().get(4).setText((hazardous ? "[X] Yes    [ ] No" : "[ ] Yes    [X] No") +
                    "    *(Subject to Section 4 of Terms & Conditions on Page 2)*", 0);
        } else {
            setCellText(cell, "Special Instructions / Handling: " + safe(instructions) + "    Hazardous Materials: " +
                    (hazardous ? "[X] Yes    [ ] No" : "[ ] Yes    [X] No") +
                    "    *(Subject to Section 4 of Terms & Conditions on Page 2)*");
        }
    }

    private static void populateDeclarations(XWPFTable table, WaybillService.WaybillDetails waybill) {
        // Keep the signature row together.
        if (table.getRows().size() > 2) {
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTrPr trPr =
                    table.getRows().get(2).getCtRow().getTrPr();
            if (trPr == null) trPr = table.getRows().get(2).getCtRow().addNewTrPr();
            if (trPr.sizeOfCantSplitArray() == 0) trPr.addNewCantSplit();

            setDeclarationCell(table.getRows().get(2).getCell(0), "Signature: " + safe(waybill.shipperDeclarationName()), "Date: " + formatLongDate(waybill.shipperDeclarationDate()), "");
            setDeclarationCell(table.getRows().get(2).getCell(1), "Driver Signature: " + safe(waybill.carrierReceiptDriverName()), "Date: " + formatLongDate(waybill.carrierReceiptDate()), "");
            setDeclarationCell(table.getRows().get(2).getCell(2), "Receiver Signature: " + safe(waybill.consigneePodReceiverName()), "Date: " + formatLongDate(waybill.consigneePodDate()), "");
        }
    }

    private static void populateTermsIntro(XWPFDocument document, WaybillService.WaybillDetails waybill) {
        List<XWPFParagraph> paragraphs = document.getParagraphs();
        for (XWPFParagraph paragraph : paragraphs) {
            String text = paragraphText(paragraph);

            if (text.startsWith("TRANSPORTATION WAYBILL TERMS & CONDITIONS")) {
                // T&C now follows the Remarks section naturally. Remove any page-break
                // marker from the supplied template and use modest paragraph spacing.
                for (XWPFRun run : paragraph.getRuns()) run.getCTR().getBrList().clear();
                if (paragraph.getCTP().getPPr() != null && paragraph.getCTP().getPPr().getPageBreakBefore() != null) {
                    paragraph.getCTP().getPPr().unsetPageBreakBefore();
                }
                if (paragraph.getCTP().getPPr() == null) paragraph.getCTP().addNewPPr();
                if (paragraph.getCTP().getPPr().getSpacing() == null) paragraph.getCTP().getPPr().addNewSpacing();
                paragraph.getCTP().getPPr().getSpacing().setBefore(180);
            }

            if (text.startsWith("By tendering goods for transportation,")) {
                replaceParagraphText(paragraph,
                        "By tendering goods for transportation, the Shipper, Consignee, and Carrier agree to be bound by the terms and conditions set forth below. These terms form an integral part of the contract of carriage represented by Waybill No: " +
                                waybill.waybillNumber() + ".");
            }
        }
    }

    private static void populateRemarks(XWPFDocument document, String remarks) {
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            if ("Remarks:".equals(paragraphText(paragraph).trim())) {
                // The supplied template contains a page-break run after the
                // Remarks label. That break is useful in the source document
                // only because the original Remarks field was blank. For a
                // generated report it would push Remarks onto a new page.
                // Remove that break and keep Remarks on page 1.
                for (XWPFRun run : paragraph.getRuns()) {
                    run.getCTR().getBrList().clear();
                }

                XWPFRun run = paragraph.createRun();
                run.setText(" " + safe(remarks));
                run.setFontSize(8);

                if (paragraph.getCTP().getPPr() == null) {
                    paragraph.getCTP().addNewPPr();
                }
                if (paragraph.getCTP().getPPr().getSpacing() == null) {
                    paragraph.getCTP().getPPr().addNewSpacing();
                }
                paragraph.getCTP().getPPr().getSpacing().setAfter(0);
                paragraph.getCTP().getPPr().getSpacing().setLine(240);
                paragraph.getCTP().getPPr().getSpacing().setLineRule(org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule.AUTO);
                return;
            }
        }
    }

    private static void populateTermsAndConditions(XWPFTable table) {
        List<TermsConditionService.Clause> clauses = TermsConditionService.findActive();
        if (table == null || table.getNumberOfRows() == 0 || table.getRow(0).getTableCells().size() < 2) return;

        int split = (clauses.size() + 1) / 2;
        for (int col = 0; col < 2; col++) {
            XWPFTableCell cell = table.getRow(0).getCell(col);
            // Rebuild the cell paragraphs from the configured clauses while preserving
            // the visual language of the approved template: blue/bold clause headings
            // followed by black clause content.
            while (cell.getParagraphs().size() > 0) {
                cell.removeParagraph(0);
            }

            int start = col == 0 ? 0 : split;
            int end = col == 0 ? split : clauses.size();
            if (start >= end) {
                XWPFParagraph empty = cell.addParagraph();
                empty.createRun().setText("");
                continue;
            }

            for (int i = start; i < end; i++) {
                TermsConditionService.Clause clause = clauses.get(i);

                XWPFParagraph title = cell.addParagraph();
                title.setSpacingBefore(80);
                title.setSpacingAfter(20);
                XWPFRun titleRun = title.createRun();
                titleRun.setText(clause.clauseNumber() + ". " + safe(clause.title()));
                titleRun.setBold(true);
                titleRun.setFontSize(9);
                titleRun.setColor("1F4E79");

                XWPFParagraph content = cell.addParagraph();
                content.setSpacingBefore(0);
                content.setSpacingAfter(70);
                XWPFRun contentRun = content.createRun();
                contentRun.setText(safe(clause.text()));
                contentRun.setFontSize(8);
                contentRun.setColor("262626");
            }
        }
    }

    /**
     * Adds a true Word/VML watermark to every header in the generated DOCX.
     *
     * The watermark is intentionally added at the DOCX package level instead of
     * as ordinary XWPF header text. That makes it a floating object positioned
     * behind the document content and repeated on every page which uses the
     * header. The same DOCX is then passed to docx4j for PDF generation.
     */
    private static void addDraftWatermark(Path docx) throws IOException {
        Path temp = Files.createTempFile(docx.toAbsolutePath().getParent(),
                "aks-waybill-watermark-", ".docx");
        boolean changed = false;

        try (InputStream in = Files.newInputStream(docx);
             ZipInputStream zipIn = new ZipInputStream(in);
             OutputStream out = Files.newOutputStream(temp);
             ZipOutputStream zipOut = new ZipOutputStream(out)) {

            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                ZipEntry replacement = new ZipEntry(entry.getName());
                replacement.setTime(entry.getTime());
                zipOut.putNextEntry(replacement);

                if (entry.getName().matches("word/header\\d+\\.xml")) {
                    String xml = new String(zipIn.readAllBytes(), StandardCharsets.UTF_8);
                    if (!xml.contains("PowerPlusWaterMarkObject")) {
                        int closing = xml.lastIndexOf("</w:hdr>");
                        if (closing >= 0) {
                            xml = xml.substring(0, closing) + draftWatermarkXml() + xml.substring(closing);
                            changed = true;
                        }
                    }
                    zipOut.write(xml.getBytes(StandardCharsets.UTF_8));
                } else {
                    zipIn.transferTo(zipOut);
                }

                zipOut.closeEntry();
                zipIn.closeEntry();
            }
        }

        if (!changed) {
            Files.deleteIfExists(temp);
            throw new IOException("Unable to add the Draft watermark because the generated document has no usable header.");
        }

        Files.move(temp, docx, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String draftWatermarkXml() {
        return """
                <w:p xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"
                     xmlns:v=\"urn:schemas-microsoft-com:vml\"
                     xmlns:o=\"urn:schemas-microsoft-com:office:office\">
                  <w:r>
                    <w:pict>
                      <v:shapetype id=\"_x0000_t136\" coordsize=\"21600,21600\"
                                   o:spt=\"136\" adj=\"10800\"
                                   path=\"m@7,l@8,m@5,21600l@6,21600e\">
                        <v:formulas>
                          <v:f eqn=\"sum #0 0 10800\"/>
                          <v:f eqn=\"prod #0 2 1\"/>
                          <v:f eqn=\"sum 21600 0 @1\"/>
                          <v:f eqn=\"sum 0 0 @2\"/>
                          <v:f eqn=\"sum 21600 0 @3\"/>
                          <v:f eqn=\"if @0 @4 0\"/>
                          <v:f eqn=\"if @0 21600 @1\"/>
                          <v:f eqn=\"if @0 0 @2\"/>
                          <v:f eqn=\"if @0 @5 21600\"/>
                          <v:f eqn=\"if @0 21600 @6\"/>
                        </v:formulas>
                        <v:path textpathok=\"t\" o:connecttype=\"custom\"
                                o:connectlocs=\"@9,0;@10,10800;@8,21600;@7,10800\"
                                textboxrect=\"@3,@4,@5,@6\"/>
                        <v:textpath on=\"t\" fitshape=\"t\"/>
                        <v:handles>
                          <v:h position=\"#0,bottomRight\" xrange=\"6629,14971\"/>
                        </v:handles>
                      </v:shapetype>
                      <v:shape id=\"PowerPlusWaterMarkObject\"
                               o:spid=\"_x0000_s1025\"
                               type=\"#_x0000_t136\"
                               style=\"position:absolute;margin-left:0;margin-top:0;width:468pt;height:117pt;z-index:-251654144;mso-wrap-edited:f;mso-position-horizontal:center;mso-position-horizontal-relative:margin;mso-position-vertical:center;mso-position-vertical-relative:margin;rotation:315\"
                               o:allowincell=\"f\" fillcolor=\"#D9DDE3\" stroked=\"f\">
                        <v:fill opacity=\"0.65\"/>
                        <v:textpath style=\"font-family:Arial;font-size:60pt;font-weight:bold\" string=\"D R A F T\"/>
                      </v:shape>
                    </w:pict>
                  </w:r>
                </w:p>
                """;
    }

    /**
     * Applies the Draft watermark directly to the final PDF.
     *
     * PDF watermarking is deliberately done after DOCX conversion because the
     * docx4j XSL-FO exporter does not render VML WordArt/text-path watermarks.
     * The watermark is prepended to each page so the existing report content
     * remains visually in front of it.
     */
    private static void addDraftPdfWatermark(Path pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            for (PDPage page : document.getPages()) {
                PDRectangle box = page.getCropBox();
                float width = box.getWidth();
                float height = box.getHeight();
                float fontSize = Math.min(width, height) * 0.17f;

                float textWidth = font.getStringWidth("D R A F T") / 1000f * fontSize;
                float textHeight = fontSize;
                float centerX = width / 2f;
                float centerY = height / 2f;
                float x = centerX - textWidth / 2f;
                float y = centerY - textHeight / 3f;

                try (PDPageContentStream content = new PDPageContentStream(
                        document, page, PDPageContentStream.AppendMode.PREPEND, true, true)) {
                    PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
                    content.saveGraphicsState();
                    graphicsState.setNonStrokingAlphaConstant(0.09f);
                    content.setGraphicsStateParameters(graphicsState);
                    content.setNonStrokingColor(0.55f, 0.58f, 0.62f);
                    content.beginText();
                    content.setFont(font, fontSize);
                    content.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(45), x, y));
                    content.showText("D R A F T");
                    content.endText();
                    content.restoreGraphicsState();
                }
            }

            Path temporary = Files.createTempFile(pdf.toAbsolutePath().getParent(),
                    "aks-waybill-draft-pdf-", ".pdf");
            try {
                document.save(temporary.toFile());
                Files.move(temporary, pdf, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static void populateFooter(XWPFDocument document, ReportProfileService.ReportProfile profile) {
        String footerText = safe(profile.companyName()) + ", " + safe(profile.address());
        for (XWPFFooter footer : document.getFooterList()) {
            for (XWPFParagraph paragraph : footer.getParagraphs()) {
                if (!paragraphText(paragraph).isBlank()) {
                    replaceParagraphText(paragraph, footerText);
                    return;
                }
            }
        }
    }

    private static void replaceTemplateLogo(Path docx) throws IOException {
        byte[] logoBytes;
        try (InputStream in = requiredResource(REPORT_LOGO_RESOURCE);
             ByteArrayOutputStream imageOut = new ByteArrayOutputStream()) {
            BufferedImage source = ImageIO.read(in);
            if (source == null) {
                throw new IOException("Unable to read the report logo image.");
            }
            // The supplied report logo is PNG and may contain transparency.
            // The approved DOCX template stores its logo as JPEG, so convert it
            // explicitly to an RGB image with a white background before encoding.
            // The Word template has a fixed logo frame of 1,485,900 x 622,300 EMU.
            // The supplied report logo has a different aspect ratio.  Do not simply
            // replace the JPEG while retaining the template crop rectangle: Word
            // would crop the lower part of the new logo.  Instead, render the logo
            // onto a white canvas with the exact aspect ratio of the template frame.
            final double targetAspect = 1485900d / 622300d;
            int canvasWidth = 1800;
            int canvasHeight = (int) Math.round(canvasWidth / targetAspect);
            BufferedImage logo = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = logo.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, canvasWidth, canvasHeight);

                double scale = Math.min(
                        (canvasWidth * 0.96d) / source.getWidth(),
                        (canvasHeight * 0.96d) / source.getHeight());
                int drawWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
                int drawHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
                int x = (canvasWidth - drawWidth) / 2;
                int y = (canvasHeight - drawHeight) / 2;
                graphics.drawImage(source, x, y, drawWidth, drawHeight, null);
            } finally {
                graphics.dispose();
            }
            if (!ImageIO.write(logo, "jpg", imageOut)) {
                throw new IOException("Unable to encode the report logo as JPEG. No JPEG encoder is available.");
            }
            logoBytes = imageOut.toByteArray();
        }

        Path temp = Files.createTempFile(docx.toAbsolutePath().getParent(), "aks-waybill-logo-", ".docx");
        boolean replaced = false;
        try (InputStream in = Files.newInputStream(docx);
             ZipInputStream zipIn = new ZipInputStream(in);
             OutputStream out = Files.newOutputStream(temp);
             ZipOutputStream zipOut = new ZipOutputStream(out)) {

            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                ZipEntry replacement = new ZipEntry(entry.getName());
                replacement.setTime(entry.getTime());
                zipOut.putNextEntry(replacement);
                if (TEMPLATE_LOGO_ENTRY.equals(entry.getName())) {
                    zipOut.write(logoBytes);
                    replaced = true;
                } else if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(zipIn.readAllBytes(), StandardCharsets.UTF_8);
                    // The supplied template contains an a:srcRect crop around the
                    // original logo. It is correct for the original artwork but
                    // clips the new supplied report logo. Remove that crop so the
                    // complete logo is rendered inside the fixed logo frame.
                    xml = xml.replaceFirst(
                            "<a:srcRect\\s+[^>]*/>",
                            "<a:srcRect l=\"0\" t=\"0\" r=\"0\" b=\"0\"/>");
                    zipOut.write(xml.getBytes(StandardCharsets.UTF_8));
                } else {
                    zipIn.transferTo(zipOut);
                }
                zipOut.closeEntry();
                zipIn.closeEntry();
            }
        }
        if (!replaced) {
            Files.deleteIfExists(temp);
            throw new IOException("The approved waybill template does not contain its expected logo image.");
        }
        Files.move(temp, docx, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void convertDocxToPdf(Path docx, Path pdf) throws IOException {
        Path temporaryPdf = null;

        try {
            WordprocessingMLPackage wordPackage =
                    WordprocessingMLPackage.load(docx.toFile());

            wordPackage.setFontMapper(new IdentityPlusMapper());

            temporaryPdf = Files.createTempFile(
                    pdf.toAbsolutePath().getParent(),
                    "aks-waybill-pdf-",
                    ".pdf"
            );

            try (OutputStream out = Files.newOutputStream(temporaryPdf)) {
                Docx4J.toPDF(wordPackage, out);
            }

            if (!Files.exists(temporaryPdf) || Files.size(temporaryPdf) == 0) {
                throw new IOException("PDF conversion completed without producing PDF content.");
            }

            Files.move(
                    temporaryPdf,
                    pdf,
                    StandardCopyOption.REPLACE_EXISTING
            );

            temporaryPdf = null;

        } catch (Exception e) { WaspLogger.error("Operation failed in WaybillReportService", e);
            throw new IOException("Unable to convert the approved Word waybill template to PDF.", e);
        }
    }

    private static XWPFTableCell cell(XWPFTable table, int row, int column) {
        return table.getRows().get(row).getCell(column);
    }

    private static void replaceValueRun(XWPFTableCell cell, String value) {
        if (cell.getParagraphs().isEmpty()) {
            cell.addParagraph();
        }
        XWPFParagraph paragraph = cell.getParagraphs().get(0);
        if (paragraph.getRuns().size() >= 2) {
            paragraph.getRuns().get(1).setText(safe(value), 0);
            for (int i = 2; i < paragraph.getRuns().size(); i++) {
                paragraph.getRuns().get(i).setText("", 0);
            }
        } else {
            replaceParagraphText(paragraph, safe(value));
        }
    }

    private static void setRunText(XWPFTableCell cell, int runIndex, String value) {
        if (cell.getParagraphs().isEmpty()) {
            cell.addParagraph();
        }
        XWPFParagraph paragraph = cell.getParagraphs().get(0);
        if (runIndex < paragraph.getRuns().size()) {
            paragraph.getRuns().get(runIndex).setText(safe(value), 0);
        }
    }

    private static String extractRunText(XWPFTableCell cell, int runIndex) {
        if (cell.getParagraphs().isEmpty()) return null;
        XWPFParagraph paragraph = cell.getParagraphs().get(0);
        if (runIndex >= paragraph.getRuns().size()) return null;
        return paragraph.getRuns().get(runIndex).text();
    }

    private static void replaceRunText(XWPFTableCell cell, int runIndex, String value) {
        setRunText(cell, runIndex, value);
    }

    private static void setDeclarationCell(XWPFTableCell cell, String line1, String line2, String line3) {
        XWPFParagraph paragraph = cell.getParagraphs().isEmpty() ? cell.addParagraph() : cell.getParagraphs().get(0);
        for (XWPFRun run : paragraph.getRuns()) run.setText("", 0);
        XWPFRun run = paragraph.getRuns().isEmpty() ? paragraph.createRun() : paragraph.getRuns().get(0);
        // Leave a physical signature area above the printed signature/name line.
        // Use paragraph spacing rather than blank lines so the three declaration
        // columns stay aligned and the approved report layout is preserved.
        paragraph.setSpacingBefore(360); // 18 pt signature space
        run.setText(safe(line1));
        if (line2 != null && !line2.isBlank()) {
            run.addBreak();
            run.setText(line2);
        }
        if (line3 != null && !line3.isBlank()) {
            run.addBreak();
            run.setText(line3);
        }
        run.setFontSize(8);
    }

    private static void setCellText(XWPFTableCell cell, String value) {
        XWPFParagraph paragraph;
        if (cell.getParagraphs().isEmpty()) {
            paragraph = cell.addParagraph();
        } else {
            paragraph = cell.getParagraphs().get(0);
        }
        XWPFRun valueRun;
        if (paragraph.getRuns().isEmpty()) {
            valueRun = paragraph.createRun();
            valueRun.setText(safe(value));
        } else {
            valueRun = paragraph.getRuns().get(0);
            valueRun.setText(safe(value), 0);
            for (int i = 1; i < paragraph.getRuns().size(); i++) {
                paragraph.getRuns().get(i).setText("", 0);
            }
        }
        // Keep generated cell values compact enough for the approved template.
        // Use the public XWPFRun API rather than generated JAXB RPr#getSz.
        valueRun.setFontSize(8);
    }

    private static void replaceParagraphText(XWPFParagraph paragraph, String value) {
        if (paragraph.getRuns().isEmpty()) {
            paragraph.createRun().setText(safe(value));
            return;
        }
        paragraph.getRuns().get(0).setText(safe(value), 0);
        for (int i = 1; i < paragraph.getRuns().size(); i++) {
            paragraph.getRuns().get(i).setText("", 0);
        }
    }

    private static String paragraphText(XWPFParagraph paragraph) {
        StringBuilder builder = new StringBuilder();
        for (XWPFRun run : paragraph.getRuns()) {
            String text = run.text();
            if (text != null) builder.append(text);
        }
        return builder.toString();
    }

    private static InputStream requiredResource(String path) throws IOException {
        InputStream input = WaybillReportService.class.getResourceAsStream(path);
        if (input == null) throw new IOException("Report template resource was not found: " + path);
        return input;
    }

    private static String formatLongDate(java.time.LocalDate date) {
        if (date == null) return "";
        return date.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String number(Double value) {
        if (value == null) return "";
        return value % 1 == 0 ? String.format("%.0f", value) : String.format("%.2f", value);
    }
}
