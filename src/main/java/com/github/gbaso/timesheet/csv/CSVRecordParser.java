package com.github.gbaso.timesheet.csv;

public interface CSVRecordParser<T> {

    T parse(String value);

}
