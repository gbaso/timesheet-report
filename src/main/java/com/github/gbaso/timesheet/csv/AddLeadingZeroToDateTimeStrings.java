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

import java.util.regex.Pattern;

/**
 * @author Giacomo Baso
 */
public class AddLeadingZeroToDateTimeStrings implements CSVRecordProcessor<String> {

    private static final Pattern singleDigitHour = Pattern.compile("\\d\\..+");

    @Override
    public String process(String value) {
        String[] split = value.split(" ");
        String date = split[0];
        String time = split[1];
        if (singleDigitHour.matcher(time).matches()) {
            return date + " 0" + time;
        }
        return value;
    }

}
