package com.github.gbaso.timesheet.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.github.gbaso.timesheet.csv.WorklogRow;

class CsvReaderServiceTests {

    @Test
    void readWorklog() throws IOException {
        var service = new CsvReaderService();
        var testFile = new ClassPathResource("test.csv");
        List<WorklogRow> worklogs = service.readWorklog(testFile.getInputStream(), "Giacomo Baso", LocalDate.now(), LocalDate.now());
        assertThat(worklogs).hasSize(2);
    }

}
