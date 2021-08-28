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

package com.github.gbaso.timesheet.csv;

import java.time.LocalDate;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvDate;
import com.opencsv.bean.processor.PreAssignmentProcessor;

import lombok.Data;

/**
 * @author Giacomo Baso
 */
@Data
public class WorklogRow {

    @CsvBindByName(column = "Issue Type")
    private String    type;
    @CsvBindByName(column = "Key")
    private String    key;
    @CsvBindByName(column = "Summary")
    private String    summary;
    @PreAssignmentProcessor(processor = AddLeadingZeroToDateTimeStrings.class)
    @CsvDate("dd/MM/yyyy HH.mm.ss")
    @CsvBindByName(column = "Log Work.started")
    private LocalDate started;
    @CsvBindByName(column = "Log Work.timeSpent")
    private String    timeSpent;
    @CsvBindByName(column = "Log Work.authorDisplayName")
    private String    author;

}
