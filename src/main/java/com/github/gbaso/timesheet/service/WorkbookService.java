package com.github.gbaso.timesheet.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.github.gbaso.timesheet.utils.TimeUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkbookService {

    private static final DateTimeFormatter dayOfWeekFormatter = DateTimeFormatter.ofPattern("EE");
    private static final DateTimeFormatter dateFormatter      = DateTimeFormatter.ofPattern("dd/MM/yy");

    public Workbook convertReport(Map<String, Map<LocalDate, Integer>> reportMap, Map<String, String> summaryMap, String author, LocalDate from, LocalDate to) {
        Map<LocalDate, Integer> totalByDate = reportMap.values()
                .stream()
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.reducing(0, Map.Entry::getValue, Integer::sum)));
        Map<String, Integer> totalByKey = reportMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().values().stream().reduce(0, Integer::sum)));
        List<LocalDate> dates = Stream.iterate(from, date -> !date.isAfter(to), date -> date.plusDays(1)).toList();
        List<String> keys = totalByKey.keySet().stream().sorted().toList();

        var workbook = new XSSFWorkbook();
        CellStyle bold = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        bold.setFont(font);
        Sheet sheet = workbook.createSheet();

        int rowNum = 0;
        addAuthorRow(sheet, rowNum++, author, bold);
        addTotalsRow(sheet, rowNum++, dates, totalByDate, bold);
        addDayOfWeekRow(sheet, rowNum++, dates, bold);
        addHeadersRow(sheet, rowNum++, dates, bold);
        for (String key : keys) {
            addKeyRow(sheet, rowNum++, key, summaryMap.get(key), dates, reportMap.get(key), totalByKey.get(key));
        }
        addTotalsRow(sheet, rowNum, dates, totalByDate, bold);
        return workbook;
    }

    private void addAuthorRow(Sheet sheet, int rowNum, String author, CellStyle bold) {
        Row row = sheet.createRow(rowNum);
        Cell titleCell = row.createCell(0, CellType.STRING);
        titleCell.setCellStyle(bold);
        titleCell.setCellValue("User:");
        Cell cell = row.createCell(1, CellType.STRING);
        cell.setCellStyle(bold);
        cell.setCellValue(author);
    }

    private void addTotalsRow(Sheet sheet, int rowNum, List<LocalDate> dates, Map<LocalDate, Integer> totalByDate, CellStyle bold) {
        int colNum = 0;
        Row row = sheet.createRow(rowNum);
        Cell titleCell = row.createCell(colNum++, CellType.STRING);
        titleCell.setCellStyle(bold);
        titleCell.setCellValue("Total");
        colNum++;
        for (LocalDate date : dates) {
            int minutes = totalByDate.getOrDefault(date, 0);
            Cell cell = row.createCell(colNum++, CellType.STRING);
            cell.setCellValue(TimeUtils.formatMinutes(minutes));
        }
        Cell totalCell = row.createCell(colNum, CellType.STRING);
        totalCell.setCellValue(TimeUtils.formatMinutes(totalByDate.values().stream().reduce(0, Integer::sum)));
    }

    private void addDayOfWeekRow(Sheet sheet, int rowNum, List<LocalDate> dates, CellStyle bold) {
        int colNum = 2;
        Row row = sheet.createRow(rowNum);
        for (LocalDate date : dates) {
            Cell cell = row.createCell(colNum++, CellType.STRING);
            cell.setCellStyle(bold);
            cell.setCellValue(dayOfWeekFormatter.format(date));
        }
    }

    private void addHeadersRow(Sheet sheet, int rowNum, List<LocalDate> dates, CellStyle bold) {
        int colNum = 0;
        Row row = sheet.createRow(rowNum);
        Cell keyCell = row.createCell(colNum++, CellType.STRING);
        keyCell.setCellStyle(bold);
        keyCell.setCellValue("Issue");
        Cell summaryCell = row.createCell(colNum++, CellType.STRING);
        summaryCell.setCellStyle(bold);
        summaryCell.setCellValue("Summary");
        for (LocalDate date : dates) {
            Cell cell = row.createCell(colNum++, CellType.STRING);
            cell.setCellStyle(bold);
            cell.setCellValue(dateFormatter.format(date));
        }
        Cell totalCell = row.createCell(colNum, CellType.STRING);
        totalCell.setCellStyle(bold);
        totalCell.setCellValue("Total");
    }

    private void addKeyRow(Sheet sheet, int rowNum, String key, String summary, List<LocalDate> dates, Map<LocalDate, Integer> reportByKey, Integer total) {
        int colNum = 0;
        Row row = sheet.createRow(rowNum);
        Cell keyCell = row.createCell(colNum++, CellType.STRING);
        keyCell.setCellValue(key);
        Cell summaryCell = row.createCell(colNum++, CellType.STRING);
        summaryCell.setCellValue(summary);
        for (LocalDate date : dates) {
            int minutes = reportByKey.getOrDefault(date, 0);
            Cell cell = row.createCell(colNum++, CellType.STRING);
            cell.setCellValue(TimeUtils.formatMinutes(minutes));
        }
        Cell totalCell = row.createCell(colNum, CellType.STRING);
        totalCell.setCellValue(TimeUtils.formatMinutes(total));
    }

}
