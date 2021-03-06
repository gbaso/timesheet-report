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

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;

/**
 * @author Giacomo Baso
 */
public abstract class BaseController {

    protected LocalDate parseDate(String date) {
        return StringUtils.isNotBlank(date) ? LocalDate.parse(date) : LocalDate.now();
    }

    protected void downloadFile(InputStream inputStream, String fileName, String contentType, long length, HttpServletResponse response) throws IOException {
        response.setContentLengthLong(length);
        downloadFile(inputStream, fileName, contentType, response);
    }

    protected void downloadFile(InputStream inputStream, String fileName, String contentType, HttpServletResponse response) throws IOException {
        response.setContentType(contentType);
        var disposition = ContentDisposition.attachment().filename(fileName).build();
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition.toString());
        try (var outStream = response.getOutputStream()) {
            IOUtils.copy(inputStream, outStream);
        }
    }

}
