package com.github.gbaso.timesheet.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;

import javax.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.github.gbaso.timesheet.service.TimesheetService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class TimesheetController extends BaseController {

    private final TimesheetService timesheetService;

    @GetMapping(path = "/report", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public void report(@RequestParam String file, @RequestParam String author, @RequestParam(required = false) String from, @RequestParam(required = false) String to, HttpServletResponse response)
            throws IOException {
        Path filePath = Path.of(file);
        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);
        Assert.isTrue(!fromDate.isAfter(toDate), "Invalid date interval: from " + from + " to " + to);
        File report = timesheetService.generateReport(filePath, author, fromDate, toDate);
        if (report != null) {
            try (var inputStream = new FileInputStream(report)) {
                inputStreamToResponse(inputStream, "TimePO User timesheet report.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", report.length(), response);
            }
        }
    }

}
