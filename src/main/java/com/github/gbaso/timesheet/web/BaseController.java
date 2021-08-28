package com.github.gbaso.timesheet.web;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.springframework.http.HttpHeaders;

public abstract class BaseController {

    protected LocalDate parseDate(String date) {
        return date != null ? LocalDate.parse(date) : LocalDate.now();
    }

    protected void inputStreamToResponse(InputStream inputStream, String fileName, String contentType, long length, HttpServletResponse response) throws IOException {
        response.setContentLengthLong(length);
        inputStreamToResponse(inputStream, fileName, contentType, response);
    }

    protected void inputStreamToResponse(InputStream inputStream, String fileName, String contentType, HttpServletResponse response) throws IOException {
        response.setContentType(contentType);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment; filename=\"%s\"", fileName));
        try (var outStream = response.getOutputStream()) {
            IOUtils.copy(inputStream, outStream);
        }
    }

}
