package org.thingsboard.trendz.generator.utils;

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
}
