package com.aks.waybill.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Creates a professionally formatted Excel export of saved waybills.
 *
 * The workbook is intentionally formatted for both on-screen review and printing:
 * title/subtitle, frozen headers, filters, wrapped cells, borders, alternating rows,
 * sensible column widths, print setup and a compact summary row.
 */
public final class ExcelExportService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter GENERATED_AT = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a");

    private static final int HEADER_ROW = 3;
    private static final int FIRST_DATA_ROW = 4;
    private static final int COLUMN_COUNT = 12;

    private ExcelExportService() {
    }

    public static Path export(Path target, List<WaybillService.WaybillExportRow> rows) {
        if (target == null) {
            throw new IllegalArgumentException("Export file is required.");
        }

        List<WaybillService.WaybillExportRow> exportRows = rows == null ? List.of() : rows;

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Waybills");
            sheet.setDisplayGridlines(false);

            // W.A.S.P. colour palette.
            XSSFColor navy = rgb(13, 55, 83);
            XSSFColor blue = rgb(31, 104, 151);
            XSSFColor lightBlue = rgb(235, 243, 248);
            XSSFColor lighterBlue = rgb(247, 250, 252);
            XSSFColor border = rgb(205, 216, 224);
            XSSFColor white = rgb(255, 255, 255);
            XSSFColor darkText = rgb(25, 48, 69);

            // -----------------------------------------------------------------
            // Title
            // -----------------------------------------------------------------
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, COLUMN_COUNT - 1));
            Row title = sheet.createRow(0);
            title.setHeightInPoints(30);
            Cell titleCell = title.createCell(0);
            titleCell.setCellValue("W.A.S.P.  |  SAVED WAYBILLS");
            titleCell.setCellStyle(titleStyle(wb, navy, white));

            // -----------------------------------------------------------------
            // Subtitle / generated information
            // -----------------------------------------------------------------
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, COLUMN_COUNT - 1));
            Row subtitle = sheet.createRow(1);
            subtitle.setHeightInPoints(22);
            Cell subtitleCell = subtitle.createCell(0);
            subtitleCell.setCellValue("Transportation Waybill Register  •  Generated " + GENERATED_AT.format(LocalDateTime.now()));
            subtitleCell.setCellStyle(subtitleStyle(wb, lighterBlue, blue));

            // Small summary row.
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, COLUMN_COUNT - 1));
            Row summary = sheet.createRow(2);
            summary.setHeightInPoints(21);
            Cell summaryCell = summary.createCell(0);
            summaryCell.setCellValue("Records exported: " + exportRows.size());
            summaryCell.setCellStyle(summaryStyle(wb, lightBlue, darkText));

            String[] headers = {
                    "Waybill No.",
                    "Date",
                    "Shipper / Consignor",
                    "Consignee / Receiver",
                    "Carrier",
                    "Driver",
                    "Vehicle / Trailer No.",
                    "Origin / Loading Point",
                    "Destination / Unloading Point",
                    "Estimated Delivery",
                    "Created By",
                    "Created At"
            };

            CellStyle headerStyle = headerStyle(wb, navy, white, border);
            Row header = sheet.createRow(HEADER_ROW);
            header.setHeightInPoints(32);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            CellStyle oddStyle = dataStyle(wb, white, darkText, border);
            CellStyle evenStyle = dataStyle(wb, lighterBlue, darkText, border);
            CellStyle dateStyleOdd = dataStyle(wb, white, darkText, border);
            dateStyleOdd.setDataFormat(wb.createDataFormat().getFormat("dd-mmm-yyyy"));
            CellStyle dateStyleEven = dataStyle(wb, lighterBlue, darkText, border);
            dateStyleEven.setDataFormat(wb.createDataFormat().getFormat("dd-mmm-yyyy"));

            int rowIndex = FIRST_DATA_ROW;
            for (WaybillService.WaybillExportRow row : exportRows) {
                Row excelRow = sheet.createRow(rowIndex);
                excelRow.setHeightInPoints(42);

                CellStyle base = ((rowIndex - FIRST_DATA_ROW) % 2 == 0) ? oddStyle : evenStyle;
                CellStyle date = ((rowIndex - FIRST_DATA_ROW) % 2 == 0) ? dateStyleOdd : dateStyleEven;

                writeText(excelRow, 0, row.waybillNumber(), base);
                writeDate(excelRow, 1, row.waybillDate(), date);
                writeText(excelRow, 2, row.shipperName(), base);
                writeText(excelRow, 3, row.consigneeName(), base);
                writeText(excelRow, 4, row.carrierName(), base);
                writeText(excelRow, 5, row.driverName(), base);
                writeText(excelRow, 6, row.vehicleTrailerNo(), base);
                writeText(excelRow, 7, row.origin(), base);
                writeText(excelRow, 8, row.destination(), base);
                writeDate(excelRow, 9, row.estimatedDeliveryDate(), date);
                writeText(excelRow, 10, row.createdBy(), base);
                writeText(excelRow, 11, row.createdAt(), base);

                rowIndex++;
            }

            // Auto-filter across the actual exported table.
            int lastDataRow = Math.max(HEADER_ROW, rowIndex - 1);
            sheet.setAutoFilter(new CellRangeAddress(HEADER_ROW, lastDataRow, 0, COLUMN_COUNT - 1));

            // Keep title/subtitle/summary/header visible while scrolling.
            sheet.createFreezePane(0, FIRST_DATA_ROW);

            // Explicit widths work better than autoSizeColumn for long addresses and
            // prevent a single long value from creating an unusably wide worksheet.
            int[] widths = {
                    24, // Waybill
                    14, // Date
                    28, // Shipper
                    28, // Consignee
                    27, // Carrier
                    22, // Driver
                    23, // Vehicle
                    32, // Origin
                    32, // Destination
                    20, // Estimated delivery
                    18, // Created by
                    24  // Created at
            };
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }

            // Print setup: landscape, fit all columns to one page width, repeat the
            // header row, and provide sensible margins.
            PrintSetup print = sheet.getPrintSetup();
            print.setLandscape(true);
            print.setFitWidth((short) 1);
            print.setFitHeight((short) 0);
            sheet.setFitToPage(true);
            sheet.setRepeatingRows(new CellRangeAddress(HEADER_ROW, HEADER_ROW, 0, COLUMN_COUNT - 1));
            sheet.setAutobreaks(true);
            sheet.setMargin(Sheet.LeftMargin, 0.30);
            sheet.setMargin(Sheet.RightMargin, 0.30);
            sheet.setMargin(Sheet.TopMargin, 0.45);
            sheet.setMargin(Sheet.BottomMargin, 0.45);
            sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);

            // Footer with page numbers.
            Footer footer = sheet.getFooter();
            footer.setLeft("W.A.S.P. - Saved Waybills");
            footer.setRight("Page &P of &N");

            // Active filter/header view when the workbook is opened.
            sheet.setActiveCell(new org.apache.poi.ss.util.CellAddress(HEADER_ROW, 0));

            if (target.toAbsolutePath().getParent() != null) {
                Files.createDirectories(target.toAbsolutePath().getParent());
            }
            try (OutputStream out = Files.newOutputStream(target)) {
                wb.write(out);
            }
            return target;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to export Excel file: " + e.getMessage(), e);
        }
    }

    private static CellStyle titleStyle(Workbook wb, XSSFColor fill, XSSFColor fontColor) {
        XSSFCellStyle style = (XSSFCellStyle) wb.createCellStyle();
        style.setFillForegroundColor(fill);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont font = (XSSFFont) wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 15);
        font.setColor(fontColor);
        style.setFont(font);
        return style;
    }

    private static CellStyle subtitleStyle(Workbook wb, XSSFColor fill, XSSFColor fontColor) {
        XSSFCellStyle style = (XSSFCellStyle) wb.createCellStyle();
        style.setFillForegroundColor(fill);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont font = (XSSFFont) wb.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setColor(fontColor);
        style.setFont(font);
        return style;
    }

    private static CellStyle summaryStyle(Workbook wb, XSSFColor fill, XSSFColor fontColor) {
        XSSFCellStyle style = (XSSFCellStyle) wb.createCellStyle();
        style.setFillForegroundColor(fill);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont font = (XSSFFont) wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 9);
        font.setColor(fontColor);
        style.setFont(font);
        return style;
    }

    private static CellStyle headerStyle(Workbook wb, XSSFColor fill, XSSFColor fontColor, XSSFColor border) {
        XSSFCellStyle style = (XSSFCellStyle) wb.createCellStyle();
        style.setFillForegroundColor(fill);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        applyBorders(style, border);
        XSSFFont font = (XSSFFont) wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        font.setColor(fontColor);
        style.setFont(font);
        return style;
    }

    private static CellStyle dataStyle(Workbook wb, XSSFColor fill, XSSFColor fontColor, XSSFColor border) {
        XSSFCellStyle style = (XSSFCellStyle) wb.createCellStyle();
        style.setFillForegroundColor(fill);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        applyBorders(style, border);
        XSSFFont font = (XSSFFont) wb.createFont();
        font.setFontHeightInPoints((short) 9);
        font.setColor(fontColor);
        style.setFont(font);
        return style;
    }

    private static void applyBorders(CellStyle style, XSSFColor borderColor) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        // POI 5.4.1 exposes the border-colour setters on CellStyle using
        // indexed colours. Keep the custom W.A.S.P. border colour for the
        // XSSF palette elsewhere, but use a subtle indexed grey here so the
        // export remains compatible with the project's POI version.
        short borderIndex = IndexedColors.GREY_25_PERCENT.getIndex();
        style.setTopBorderColor(borderIndex);
        style.setBottomBorderColor(borderIndex);
        style.setLeftBorderColor(borderIndex);
        style.setRightBorderColor(borderIndex);
    }

    private static void writeText(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(s(value));
        cell.setCellStyle(style);
    }

    private static void writeDate(Row row, int column, java.time.LocalDate value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value == null) {
            cell.setCellValue("");
        } else {
            cell.setCellValue(java.sql.Date.valueOf(value));
        }
        cell.setCellStyle(style);
    }

    private static XSSFColor rgb(int red, int green, int blue) {
        return new XSSFColor(new byte[]{(byte) red, (byte) green, (byte) blue}, new DefaultIndexedColorMap());
    }

    private static String s(String value) {
        return value == null ? "" : value;
    }
}
