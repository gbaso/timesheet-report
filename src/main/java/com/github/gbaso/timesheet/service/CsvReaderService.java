package com.github.gbaso.timesheet.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.gbaso.timesheet.csv.WorklogRow;
import com.github.gbaso.timesheet.utils.TimeUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CsvReaderService {

    public List<WorklogRow> readWorklog(InputStream inputStream, String author, LocalDate from, LocalDate to) throws IOException {
        var mapper = new CsvMapper();
        mapper.registerModule(new JavaTimeModule());
        CsvSchema headerSchema = CsvSchema.emptySchema().withHeader();
        try (var reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            MappingIterator<WorklogRow> rows = mapper
                    .readerFor(WorklogRow.class)
                    .with(headerSchema)
                    .readValues(reader);
            return stream(rows)
                    .filter(r -> r.getStarted() != null)
                    .filter(r -> StringUtils.equals(r.getAuthor(), author))
                    .filter(r -> TimeUtils.between(r.getStarted(), from, to))
                    .toList();
        }
    }

    private <T> Stream<T> stream(Iterator<T> iter) {
        Iterable<T> iterable = () -> iter;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

}
