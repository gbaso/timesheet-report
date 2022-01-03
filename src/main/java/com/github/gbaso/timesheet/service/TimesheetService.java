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
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;

import com.github.gbaso.timesheet.csv.WorklogRow;
import com.github.gbaso.timesheet.utils.TimeUtils;

import lombok.RequiredArgsConstructor;

/**
 * @author Giacomo Baso
 */
@Service
@RequiredArgsConstructor
public class TimesheetService {

    private final CsvReaderService csvReaderService;
    private final ApiReaderService apiReaderService;
    private final WorkbookService  workbookService;

    public File generateReportFromInputSteam(InputStream inputStream, String author, LocalDate from, LocalDate to) throws IOException {
        List<WorklogRow> rows = csvReaderService.readWorklog(inputStream, author, from, to);
        return generateReport(rows, author, from, to);
    }

    public File generateReportFromAPI(Set<String> projects, String author, LocalDate from, LocalDate to) throws IOException {
        List<WorklogRow> rows = apiReaderService.readWorklog(projects, author, from, to);
        return generateReport(rows, author, from, to);
    }

    private File generateReport(List<WorklogRow> rows, String author, LocalDate from, LocalDate to) throws IOException {
        Map<String, String> summaryMap = rows.stream().map(r -> new Issue(r.getKey(), r.getSummary(), r.getType())).distinct().collect(Collectors.toMap(Issue::key, Issue::summary));
        Map<String, Map<LocalDate, Integer>> reportMap = rows.stream()
                .collect(Collectors.groupingBy(WorklogRow::getKey, Collectors.groupingBy(WorklogRow::getStarted, Collectors.reducing(0, this::toMinutes, Integer::sum))));
        return saveToFile(reportMap, summaryMap, author, from, to);
    }

    private int toMinutes(WorklogRow row) {
        String timeSpent = row.getTimeSpent();
        return TimeUtils.parseMinutes(timeSpent);
    }

    private File saveToFile(Map<String, Map<LocalDate, Integer>> reportMap, Map<String, String> summaryMap, String author, LocalDate from, LocalDate to) throws IOException {
        Workbook workbook = workbookService.convertReport(reportMap, summaryMap, author, from, to);
        File tmpFile = Files.createTempFile(null, "xls").toFile();
        try (var os = new FileOutputStream(tmpFile)) {
            workbook.write(os);
        }
        return tmpFile;
    }

}
