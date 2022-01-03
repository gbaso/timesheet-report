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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.util.UriBuilder;

import com.github.gbaso.timesheet.csv.WorklogRow;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * @author Giacomo Baso
 */
@Service
@RequiredArgsConstructor
public class TimesheetService {

    private static final ParameterizedTypeReference<Map<String, Object>> STRING_OBJECT_MAP  = new ParameterizedTypeReference<>() {};

    private static final int                                             HOURS_PER_WORK_DAY = 8;
    private static final int                                             MINUTES_PER_HOUR   = 60;

    private static final DateTimeFormatter                               dayOfWeekFormatter = DateTimeFormatter.ofPattern("EE");
    private static final DateTimeFormatter                               dateFormatter      = DateTimeFormatter.ofPattern("dd/MM/yy");

    private String                                                       cloudId;

    private final WebClient                                              webClient;

    public File generateReportFromInputSteam(InputStream inputStream, String author, LocalDate from, LocalDate to) throws IOException {
        List<WorklogRow> rows = readWorklogFromInputSteam(inputStream, author, from, to);
        return generateReport(rows, author, from, to);
    }

    private List<WorklogRow> readWorklogFromInputSteam(InputStream inputStream, String author, LocalDate from, LocalDate to) throws IOException {
        try (var reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            CsvToBeanBuilder<WorklogRow> beanBuilder = new CsvToBeanBuilder<>(reader);
            beanBuilder.withType(WorklogRow.class);
            CsvToBean<WorklogRow> build = beanBuilder.withFilter(lines -> StringUtils.isNotBlank(lines[3])).build();
            return build.stream().filter(r -> StringUtils.equals(r.getAuthor(), author)).filter(r -> between(r.getStarted(), from, to)).toList();
        }
    }

    public File generateReportFromAPI(Set<String> projects, String author, LocalDate from, LocalDate to) throws IOException {
        List<WorklogRow> rows = readWorklogFromAPI(projects, author, from, to);
        return generateReport(rows, author, from, to);
    }

    @SuppressWarnings("unchecked")
    private List<WorklogRow> readWorklogFromAPI(Set<String> projects, String author, LocalDate from, LocalDate to) {
        List<Integer> worklogIds = getWorklogIds(from);
        List<Map<String, Object>> worklogs = getWorklogs(worklogIds);
        List<WorklogRow> rows = worklogs.stream()
                .map(map -> {
                    var row = new WorklogRow();
                    String started = (String) map.get("started");
                    String startedDate = started.split("T")[0];
                    row.setStarted(LocalDate.parse(startedDate));
                    row.setTimeSpent((String) map.get("timeSpent"));
                    Map<String, Object> authorProperty = (Map<String, Object>) map.get("author");
                    row.setAuthor((String) authorProperty.get("displayName"));
                    row.setIssueId((String) map.get("issueId"));
                    return row;
                })
                .filter(row -> StringUtils.equals(row.getAuthor(), author))
                .filter(row -> between(row.getStarted(), from, to))
                .toList();
        Set<String> issueIds = rows.stream().map(WorklogRow::getIssueId).collect(Collectors.toUnmodifiableSet());
        Map<String, Issue> issues = getIssues(projects, issueIds);
        return rows.stream().filter(row -> issues.containsKey(row.getIssueId())).map(row -> {
            var issue = issues.get(row.getIssueId());
            row.setType(issue.type());
            row.setKey(issue.key());
            row.setSummary(issue.summary());
            return row;
        }).toList();
    }

    private List<Integer> getWorklogIds(LocalDate from) {
        Function<UriBuilder, URI> uriFunction = uriBuilder -> uriBuilder
                .path("/ex/jira/{cloudid}/rest/api/3/worklog/updated")
                .queryParam("since", toEpochMillis(from))
                .queryParam("expand", "issueId,started,timeSpent,author")
                .build(getCloudId());
        Map<String, Object> response = executeRequest(HttpMethod.GET, uriFunction).blockFirst();
        Assert.notNull(response, "Cannot read worklogs");
        @SuppressWarnings("unchecked")
        var values = (List<Map<String, Object>>) response.get("values");
        return values.stream().map(it -> (Integer) it.get("worklogId")).toList();
    }

    private List<Map<String, Object>> getWorklogs(List<Integer> worklogIds) {
        Function<UriBuilder, URI> uriFunction = uriBuilder -> uriBuilder
                .path("/ex/jira/{cloudid}/rest/api/3/worklog/list")
                .build(getCloudId());
        return executeRequest(HttpMethod.POST, uriFunction, Map.of("ids", worklogIds)).toStream().toList();
    }

    private Map<String, Issue> getIssues(Set<String> projects, Set<String> issueIds) {
        List<Map<String, Object>> issues = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Function<UriBuilder, URI> uriFunction = uriBuilder -> uriBuilder
                    .path("/ex/jira/{cloudid}/rest/api/3/search")
                    .queryParam("startAt", issues.size())
                    .queryParam("jql", "project in ({projects}) and id in ({issueIds})")
                    .queryParam("fields", "summary,issuetype")
                    .build(getCloudId(), String.join(",", projects), String.join(",", issueIds));
            Map<String, Object> response = executeRequest(HttpMethod.GET, uriFunction).blockFirst();
            Assert.notNull(response, "Cannot read issues");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> page = (List<Map<String, Object>>) response.get("issues");
            if (page.isEmpty()) {
                break;
            }
            issues.addAll(page);
        }
        return issues.stream()
                .map(map -> Pair.of((String) map.get("id"), map))
                .filter(p -> issueIds.contains(p.getKey()))
                .collect(Collectors.toMap(Pair::getKey, p -> toIssue(p.getValue())));
    }

    @SuppressWarnings("unchecked")
    private Issue toIssue(Map<String, Object> map) {
        String key = (String) map.get("key");
        Map<String, Object> fields = (Map<String, Object>) map.get("fields");
        String summary = (String) fields.get("summary");
        Map<String, Object> issuetype = (Map<String, Object>) fields.get("issuetype");
        String type = (String) issuetype.get("name");
        return new Issue(key, summary, type);
    }

    static record Issue(String key, String summary, String type) {}

    private long toEpochMillis(LocalDate when) {
        return when.toEpochSecond(LocalTime.MIDNIGHT, ZoneOffset.UTC) * 1000;
    }

    private String getCloudId() {
        if (this.cloudId == null) {
            Map<String, Object> resource = executeRequest(HttpMethod.GET, uriBuilder -> uriBuilder.path("/oauth/token/accessible-resources").build()).blockFirst();
            Assert.notNull(resource, "Could not read accessile resources");
            this.cloudId = (String) resource.get("id");
        }
        return this.cloudId;
    }

    Flux<Map<String, Object>> executeRequest(HttpMethod method, Function<UriBuilder, URI> uriFunction) {
        return executeRequest(method, uriFunction, null);
    }

    Flux<Map<String, Object>> executeRequest(HttpMethod method, Function<UriBuilder, URI> uriFunction, Object body) {
        UnaryOperator<UriBuilder> host = uriBuilder -> uriBuilder.scheme("https").host("api.atlassian.com");
        RequestBodySpec reqestHeaderSpec = webClient.method(method).uri(host.andThen(uriFunction));
        if (body != null) {
            reqestHeaderSpec.bodyValue(body);
        }
        return reqestHeaderSpec
                .accept(MediaType.APPLICATION_JSON)
                .attributes(ServletOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId("jira"))
                .retrieve()
                .bodyToFlux(STRING_OBJECT_MAP);
    }

    private File generateReport(List<WorklogRow> rows, String author, LocalDate from, LocalDate to) throws IOException {
        Map<String, String> summaryMap = rows.stream().map(r -> new Issue(r.getKey(), r.getSummary(), r.getType())).distinct().collect(Collectors.toMap(Issue::key, Issue::summary));
        Map<String, Map<LocalDate, Integer>> reportMap = rows.stream()
                .collect(Collectors.groupingBy(WorklogRow::getKey, Collectors.groupingBy(WorklogRow::getStarted, Collectors.reducing(0, this::toMinutes, Integer::sum))));
        return saveToFile(reportMap, summaryMap, author, from, to);
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
