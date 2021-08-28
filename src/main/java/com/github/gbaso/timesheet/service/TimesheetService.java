/**
 * Copyright 2021-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.gbaso.timesheet.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

import com.github.gbaso.timesheet.csv.WorklogRow;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;

/**
 * @author Giacomo Baso
 */
@Service
public class TimesheetService {

    private static final int               HOURS_PER_WORK_DAY = 8;
    private static final int               MINUTES_PER_HOUR   = 60;

    private static final DateTimeFormatter dayOfWeekFormatter = DateTimeFormatter.ofPattern("EE");
    private static final DateTimeFormatter dateFormatter      = DateTimeFormatter.ofPattern("dd/MM/yy");

    public File generateReport(InputStream worklog, String author, LocalDate from, LocalDate to) throws IOException {
        List<WorklogRow> rows = readWorklog(worklog, author, from, to);
        record Issue(String key, String summary) {}
        Map<String, String> summaryMap = rows.stream().map(r -> new Issue(r.getKey(), r.getSummary())).distinct().collect(Collectors.toMap(Issue::key, Issue::summary));
        Map<String, Map<LocalDate, Integer>> reportMap = rows.stream()
                .collect(Collectors.groupingBy(WorklogRow::getKey, Collectors.groupingBy(WorklogRow::getStarted, Collectors.reducing(0, this::toMinutes, Integer::sum))));
        return saveToFile(reportMap, summaryMap, author, from, to);
    }

    private List<WorklogRow> readWorklog(InputStream worklog, String author, LocalDate from, LocalDate to) throws IOException {
        try (var reader = new InputStreamReader(worklog, StandardCharsets.UTF_8)) {
            CsvToBeanBuilder<WorklogRow> beanBuilder = new CsvToBeanBuilder<>(reader);
            beanBuilder.withType(WorklogRow.class);
            CsvToBean<WorklogRow> build = beanBuilder.withFilter(lines -> StringUtils.isNotBlank(lines[3])).build();
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

    private File saveToFile(Map<String, Map<LocalDate, Integer>> reportMap, Map<String, String> summaryMap, String author, LocalDate from, LocalDate to) throws IOException {
        Workbook workbook = convertToWorkbook(reportMap, summaryMap, author, from, to);
        File tmpFile = Files.createTempFile(null, "xls").toFile();
        try (var os = new FileOutputStream(tmpFile)) {
            workbook.write(os);
        }
        return tmpFile;
    }

    private Workbook convertToWorkbook(Map<String, Map<LocalDate, Integer>> reportMap, Map<String, String> summaryMap, String author, LocalDate from, LocalDate to) {
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
            cell.setCellValue(formatMinutes(minutes));
        }
        Cell totalCell = row.createCell(colNum, CellType.STRING);
        totalCell.setCellValue(formatMinutes(totalByDate.values().stream().reduce(0, Integer::sum)));
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
