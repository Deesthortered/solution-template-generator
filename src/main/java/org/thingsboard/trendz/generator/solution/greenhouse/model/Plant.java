package org.thingsboard.trendz.generator.solution.greenhouse.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.thingsboard.trendz.generator.model.ModelEntity;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Plant extends ModelEntity {

    @Override
    public String entityType() {
        return "GR) plant";
    }

    private String systemName;
    private String systemLabel;

    // special asset for saving plant properties
}