package com.github.gbaso.timesheet.utils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import org.springframework.util.Assert;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TimeUtils {

    private static final int HOURS_PER_WORK_DAY = 8;
    private static final int MINUTES_PER_HOUR   = 60;

    public long toEpochMillis(LocalDate when) {
        return when.toEpochSecond(LocalTime.MIDNIGHT, ZoneOffset.UTC) * 1000;
    }

    public boolean between(LocalDate when, LocalDate start, LocalDate end) {
        return !when.isBefore(start) && !when.isAfter(end);
    }

    public int parseMinutes(String timeSpent) {
        return Stream.of(timeSpent.split(" ", 3)).mapToInt(fragment -> {
            int length = fragment.length();
            Assert.isTrue(length >= 2, () -> "Worklog " + timeSpent + " has invalid format: cannot parse " + fragment);
            int prefix = Integer.parseInt(fragment.substring(0, length - 1));
            String suffix = fragment.substring(length - 1);
            return prefix * switch (suffix) {
                case "d" -> HOURS_PER_WORK_DAY * MINUTES_PER_HOUR;
                case "h" -> MINUTES_PER_HOUR;
                case "m" -> 1;
                default -> throw new IllegalArgumentException("Unexpected value: " + suffix);
            };
        }).sum();
    }

    public String formatMinutes(int minutes) {
        var sb = new StringBuilder();
        int hours = minutes / MINUTES_PER_HOUR;
        if (hours > 0) {
            sb.append(hours + "h");
        }
        int rem = minutes % MINUTES_PER_HOUR;
        if (rem > 0) {
            sb.append(rem + "m");
        }
        return sb.toString();
    }

}
