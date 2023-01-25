package org.thingsboard.trendz.generator.model.anomaly;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyInfo implements Comparable<AnomalyInfo> {

    private ZonedDateTime startDate;
    private ZonedDateTime endDate;
    private AnomalyType type;
    private long value;


    @Override
    public int compareTo(@NotNull AnomalyInfo that) {
        return this.startDate.compareTo(that.startDate);
    }
}
