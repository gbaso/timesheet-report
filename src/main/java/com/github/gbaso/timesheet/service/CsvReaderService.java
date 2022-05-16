package com.github.gbaso.timesheet.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.github.gbaso.timesheet.csv.AddLeadingZeroToDateTimeStrings;
import com.github.gbaso.timesheet.csv.CSVRecordParser;
import com.github.gbaso.timesheet.csv.CSVRecordProcessor;
import com.github.gbaso.timesheet.csv.LocalDateParser;
import com.github.gbaso.timesheet.csv.WorklogRow;
import com.github.gbaso.timesheet.utils.TimeUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CsvReaderService {

    private final CSVRecordProcessor<String> dateTimePreprocessor = new AddLeadingZeroToDateTimeStrings();
    private final CSVRecordParser<LocalDate> dateTimeParser       = new LocalDateParser();

    public List<WorklogRow> readWorklog(InputStream inputStream, String author, LocalDate from, LocalDate to) throws IOException {
        try (var reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();
            try (CSVParser parser = format.parse(reader)) {
                return parser.stream()
                        .filter(row -> StringUtils.isNotBlank(row.get(3)))
                        .map(row -> WorklogRow.builder()
                                .type(row.get("Issue Type"))
                                .key(row.get("Key"))
                                .summary(row.get("Summary"))
                                .started(parse(row.get("Log Work.started")))
                                .timeSpent(row.get("Log Work.timeSpent"))
                                .author(row.get("Log Work.authorDisplayName"))
                                .build())
                        .filter(r -> StringUtils.equals(r.getAuthor(), author))
                        .filter(r -> TimeUtils.between(r.getStarted(), from, to))
                        .toList();
            }
        }
    }

    private LocalDate parse(String value) {
        return dateTimeParser.parse(dateTimePreprocessor.process(value));
    }

}
