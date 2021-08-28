package com.github.gbaso.timesheet.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import com.github.gbaso.timesheet.dto.WorklogRow;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;

@Service
public class TimesheetService {

    private static final int               HOURS_PER_WORK_DAY = 8;
    private static final int               MINUTES_PER_HOUR   = 60;
    private static final DateTimeFormatter DATE_FORMATTER     = DateTimeFormatter.ofPattern("EE dd/MM/yy");

    public File generateReport(Path filePath, String author, LocalDate from, LocalDate to) throws IOException {
        File worklogFile = filePath.toFile();
        List<WorklogRow> rows = readWorklog(worklogFile, author, from, to);
        Map<String, Map<LocalDate, Integer>> reportMap = rows.stream()
                .collect(Collectors.groupingBy(WorklogRow::getKey, Collectors.groupingBy(WorklogRow::getStarted, Collectors.reducing(0, this::toMinutes, Integer::sum))));
        return saveToFile(reportMap, author, from, to);
    }

    private List<WorklogRow> readWorklog(File file, String author, LocalDate from, LocalDate to) throws IOException {
        try (var reader = new FileReader(file, StandardCharsets.UTF_8)) {
            CsvToBeanBuilder<WorklogRow> beanBuilder = new CsvToBeanBuilder<>(reader);
            beanBuilder.withType(WorklogRow.class);
            CsvToBean<WorklogRow> build = beanBuilder.withFilter(lines -> StringUtils.isNotBlank(lines[2])).build();
            return build.stream().filter(r -> StringUtils.equals(r.getAuthor(), author)).filter(r -> between(r.getStarted(), from, to)).toList();
        }
    }

    private boolean between(LocalDate date, LocalDate from, LocalDate to) {
        return !date.isBefore(from) && !date.isAfter(to);
    }

    private int toMinutes(WorklogRow row) {
        String timeSpent = row.getTimeSpent();
        return Stream.of(timeSpent.split(" ", 3)).mapToInt(fragment -> {
            int length = fragment.length();
            Assert.isTrue(length >= 2, () -> "Worklog " + timeSpent + " has invalid format: cannot parse " + fragment);
            int prefix = Integer.parseInt(fragment.substring(0, length - 1));
            String suffix = fragment.substring(length - 1);
            return prefix * switch (suffix) {
                case "d" -> HOURS_PER_WORK_DAY * MINUTES_PER_HOUR;
                case "h" -> MINUTES_PER_HOUR;
                case "m" -> 1;
                default -> throw new IllegalArgumentException("Unexpected value: " + suffix);
            };
        }).sum();
    }

    private File saveToFile(Map<String, Map<LocalDate, Integer>> reportMap, String author, LocalDate from, LocalDate to) throws IOException {
        Workbook workbook = convertToWorkbook(reportMap, author, from, to);
        File tmpFile = Files.createTempFile(null, "xls").toFile();
        try (var os = new FileOutputStream(tmpFile)) {
            workbook.write(os);
        }
        return tmpFile;
    }

    private Workbook convertToWorkbook(Map<String, Map<LocalDate, Integer>> reportMap, String author, LocalDate from, LocalDate to) {
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
        addHeadersRow(sheet, rowNum++, dates, bold);
        for (String key : keys) {
            addKeyRow(sheet, rowNum++, key, dates, reportMap.get(key), totalByKey.get(key));
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
        for (LocalDate date : dates) {
            int minutes = totalByDate.getOrDefault(date, 0);
            Cell cell = row.createCell(colNum++, CellType.STRING);
            cell.setCellValue(formatMinutes(minutes));
        }
        Cell totalCell = row.createCell(colNum, CellType.STRING);
        totalCell.setCellValue(formatMinutes(totalByDate.values().stream().reduce(0, Integer::sum)));
    }

    private void addHeadersRow(Sheet sheet, int rowNum, List<LocalDate> dates, CellStyle bold) {
        int colNum = 0;
        Row row = sheet.createRow(rowNum);
        Cell keyCell = row.createCell(colNum++, CellType.STRING);
        keyCell.setCellStyle(bold);
        keyCell.setCellValue("Issue");
        for (LocalDate date : dates) {
            Cell cell = row.createCell(colNum++, CellType.STRING);
            cell.setCellStyle(bold);
            cell.setCellValue(DATE_FORMATTER.format(date));
        }
        Cell totalCell = row.createCell(colNum, CellType.STRING);
        totalCell.setCellStyle(bold);
        totalCell.setCellValue("Total");
    }

    private void addKeyRow(Sheet sheet, int rowNum, String key, List<LocalDate> dates, Map<LocalDate, Integer> reportByKey, Integer total) {
        int colNum = 0;
        Row row = sheet.createRow(rowNum);
        Cell keyCell = row.createCell(colNum++, CellType.STRING);
        keyCell.setCellValue(key);
        for (LocalDate date : dates) {
            int minutes = reportByKey.getOrDefault(date, 0);
            Cell cell = row.createCell(colNum++, CellType.STRING);
            cell.setCellValue(formatMinutes(minutes));
        }
        Cell totalCell = row.createCell(colNum, CellType.STRING);
        totalCell.setCellValue(formatMinutes(total));
    }

    private String formatMinutes(int minutes) {
        var sb = new StringBuilder();
        int hours = minutes / MINUTES_PER_HOUR;
        if (hours > 0) {
            sb.append(hours + "h");
        }
        int rem = minutes % MINUTES_PER_HOUR;
        if (rem > 0) {
            sb.append(rem + "m");
        }
        return sb.toString();
    }

}