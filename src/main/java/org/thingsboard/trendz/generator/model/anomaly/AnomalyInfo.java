package org.thingsboard.trendz.generator.model.anomaly;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.thingsboard.trendz.generator.model.tb.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyInfo implements Comparable<AnomalyInfo> {

    private Timestamp startTs;
    private Timestamp endTs;
    private AnomalyType type;


    @Override
    public int compareTo(@NotNull AnomalyInfo that) {
        return this.startTs.compareTo(that.startTs);
    }
}
