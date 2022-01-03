package com.github.gbaso.timesheet.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.github.gbaso.timesheet.csv.WorklogRow;
import com.github.gbaso.timesheet.utils.TimeUtils;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CsvReaderService {

    public List<WorklogRow> readWorklog(InputStream inputStream, String author, LocalDate from, LocalDate to) throws IOException {
        try (var reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            CsvToBeanBuilder<WorklogRow> beanBuilder = new CsvToBeanBuilder<>(reader);
            beanBuilder.withType(WorklogRow.class);
            CsvToBean<WorklogRow> build = beanBuilder.withFilter(lines -> StringUtils.isNotBlank(lines[3])).build();
            return build.stream().filter(r -> StringUtils.equals(r.getAuthor(), author)).filter(r -> TimeUtils.between(r.getStarted(), from, to)).toList();
        }
    }

}
