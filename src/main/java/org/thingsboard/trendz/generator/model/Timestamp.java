package org.thingsboard.trendz.generator.model;

import lombok.EqualsAndHashCode;
import org.thingsboard.trendz.generator.utils.DateTimeUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;

@EqualsAndHashCode
public class Timestamp implements Comparable<Timestamp> {

    private final long ts;

    private Timestamp(long ts) {
        this.ts = ts;
    }


    @Override
    public String toString() {
        return toString(ZoneId.systemDefault());
    }

    public String toString(ZoneId zoneId) {
        ZonedDateTime dateTime = DateTimeUtils.fromTs(this.ts, zoneId);
        return String.format("Timestamp(%d, %s)", this.ts, dateTime);
    }

    public long get() {
        return this.ts;
    }

    public static Timestamp of(long ts) {
        return new Timestamp(ts);
    }

    @Override
    public int compareTo(Timestamp timestamp) {
        return (int) Math.signum(this.ts - timestamp.ts);
    }
}
