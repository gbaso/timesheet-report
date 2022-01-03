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

package com.github.gbaso.timesheet.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.github.gbaso.timesheet.service.TimesheetService;

import lombok.RequiredArgsConstructor;

/**
 * @author Giacomo Baso
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class TimesheetController extends BaseController {

    private final TimesheetService timesheetService;

    @PostMapping(path = "/report-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public void reportFromFile(@RequestParam MultipartFile file, @RequestParam String author, @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            HttpServletResponse response) throws IOException {
        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);
        Assert.isTrue(!fromDate.isAfter(toDate), "Invalid date interval: from " + from + " to " + to);
        File report = timesheetService.generateReportFromInputSteam(file.getInputStream(), author, fromDate, toDate);
        if (report != null) {
            try (var inputStream = new FileInputStream(report)) {
                downloadFile(inputStream, "TimePO User timesheet report.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", report.length(), response);
            }
        }
    }

    @PostMapping(path = "/report-api", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public void reportFromAPI(@RequestParam Set<String> projects, @RequestParam String author, @RequestParam(required = false) String from, @RequestParam(required = false) String to, HttpServletResponse response) throws IOException {
        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);
        Assert.isTrue(!fromDate.isAfter(toDate), "Invalid date interval: from " + from + " to " + to);
        File report = timesheetService.generateReportFromAPI(projects, author, fromDate, toDate);
        if (report != null) {
            try (var inputStream = new FileInputStream(report)) {
                downloadFile(inputStream, "TimePO User timesheet report.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", report.length(), response);
            }
        }
    }

}
