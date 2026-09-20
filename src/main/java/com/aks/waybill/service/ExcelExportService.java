package com.aks.waybill.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ExcelExportService {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private ExcelExportService(){}
    public static Path export(Path target,List<WaybillService.WaybillExportRow> rows){
        if(target==null)throw new IllegalArgumentException("Export file is required.");
        try(Workbook wb=new XSSFWorkbook()){
            Sheet sheet=wb.createSheet("Waybills");
            String[] headers={"Waybill No.","Date","Shipper / Consignor","Consignee / Receiver","Carrier","Driver","Vehicle / Trailer No.","Origin / Loading Point","Destination / Unloading Point","Estimated Delivery","Created By","Created At"};
            CellStyle hs=wb.createCellStyle();Font f=wb.createFont();f.setBold(true);hs.setFont(f);Row h=sheet.createRow(0);for(int i=0;i<headers.length;i++){Cell c=h.createCell(i);c.setCellValue(headers[i]);c.setCellStyle(hs);}
            int n=1;for(var row:rows){Row r=sheet.createRow(n++);r.createCell(0).setCellValue(s(row.waybillNumber()));r.createCell(1).setCellValue(row.waybillDate()==null?"":DATE.format(row.waybillDate()));r.createCell(2).setCellValue(s(row.shipperName()));r.createCell(3).setCellValue(s(row.consigneeName()));r.createCell(4).setCellValue(s(row.carrierName()));r.createCell(5).setCellValue(s(row.driverName()));r.createCell(6).setCellValue(s(row.vehicleTrailerNo()));r.createCell(7).setCellValue(s(row.origin()));r.createCell(8).setCellValue(s(row.destination()));r.createCell(9).setCellValue(row.estimatedDeliveryDate()==null?"":DATE.format(row.estimatedDeliveryDate()));r.createCell(10).setCellValue(s(row.createdBy()));r.createCell(11).setCellValue(s(row.createdAt()));}
            for(int i=0;i<headers.length;i++)sheet.autoSizeColumn(i);if(target.toAbsolutePath().getParent()!=null)Files.createDirectories(target.toAbsolutePath().getParent());try(OutputStream out=Files.newOutputStream(target)){wb.write(out);}return target;
        }catch(Exception e){throw new IllegalStateException("Unable to export Excel file: "+e.getMessage(),e);}
    }
    private static String s(String v){return v==null?"":v;}
}
