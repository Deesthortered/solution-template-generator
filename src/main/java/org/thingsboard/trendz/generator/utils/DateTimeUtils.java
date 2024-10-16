package org.thingsboard.trendz.generator.utils;

import org.apache.commons.lang3.tuple.Pair;
import org.thingsboard.trendz.generator.model.tb.Timestamp;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class DateTimeUtils {

    public static ZonedDateTime fromTs(long ts, ZoneId zoneId) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(ts), zoneId);
    }

    public static ZonedDateTime fromTs(long ts) {
        return fromTs(ts, ZoneId.systemDefault());
    }

    public static ZonedDateTime fromTs(Timestamp ts) {
        return fromTs(ts.get(), ZoneId.systemDefault());
    }

    public static ZonedDateTime fromTs(Timestamp ts, ZoneId zoneId) {
        return fromTs(ts.get(), zoneId);
    }

    public static long toTs(ZonedDateTime zonedDateTime) {
        return zonedDateTime.toInstant().toEpochMilli();
    }


    public static Pair<Long, Long> getDatesIntersection(
            long startDate, long endDate, long startGenerationDate, long endGenerationDate
    ) {
        final long latestStartDate = Math.max(startDate, startGenerationDate);
        final long earliestEndDate = Math.min(endDate, endGenerationDate);

        if (earliestEndDate < latestStartDate) {
            throw new IllegalStateException("The earliestEndDate is less than the latestStartDate");
        }

        return Pair.of(latestStartDate, earliestEndDate);
    }

    public static Pair<Long, Long> calculateNewDateRange(
            long from, long to, long startGenerationTime, long endGenerationTime, boolean fullTelemetryGeneration
    ) throws IllegalStateException {
        if (!fullTelemetryGeneration) {
            return DateTimeUtils.getDatesIntersection(from, to, startGenerationTime, endGenerationTime);
        }

        return Pair.of(startGenerationTime, endGenerationTime);
    }
}
