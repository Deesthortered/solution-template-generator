package org.thingsboard.trendz.generator.solution.greenhouse.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.thingsboard.trendz.generator.model.ModelEntity;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Greenhouse implements ModelEntity {

    private String systemName;
    private String systemLabel;

}
