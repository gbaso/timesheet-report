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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Giacomo Baso
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class WorklogRow {

    @JsonProperty("Issue Type")
    private String    type;
    @JsonProperty("Key")
    private String    key;
    @JsonProperty("Summary")
    private String    summary;
    @JsonDeserialize(using = LocalDateProcessorDeserializer.class)
    @JsonProperty("Log Work.started")
    private LocalDate started;
    @JsonProperty("Log Work.timeSpent")
    private String    timeSpent;
    @JsonProperty("Log Work.authorDisplayName")
    private String    author;

    @JsonIgnore
    private String    issueId;

}
