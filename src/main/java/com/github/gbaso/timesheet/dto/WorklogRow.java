package com.github.gbaso.timesheet.dto;

import java.time.LocalDate;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvDate;

import lombok.Data;

@Data
public class WorklogRow {

    @CsvBindByName(column = "Issue Type")
    private String    type;
    @CsvBindByName(column = "Key")
    private String    key;
    @CsvDate("dd/MM/yyyy HH.mm.ss")
    @CsvBindByName(column = "Log Work.started")
    private LocalDate started;
    @CsvBindByName(column = "Log Work.timeSpent")
    private String    timeSpent;
    @CsvBindByName(column = "Log Work.authorDisplayName")
    private String    author;

}
