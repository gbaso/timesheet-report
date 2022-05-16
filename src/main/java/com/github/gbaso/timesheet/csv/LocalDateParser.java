package com.github.gbaso.timesheet.csv;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class LocalDateParser implements CSVRecordParser<LocalDate> {

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH.mm.ss");

    @Override
    public LocalDate parse(String value) {
        return LocalDate.parse(value, formatter);

    }

}
