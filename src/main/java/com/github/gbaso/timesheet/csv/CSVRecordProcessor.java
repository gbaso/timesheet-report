package com.github.gbaso.timesheet.csv;

public interface CSVRecordProcessor<T> {

    T process(String value);

}
