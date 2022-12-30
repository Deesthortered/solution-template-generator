package org.thingsboard.trendz.generator.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.thingsboard.trendz.generator.utils.JsonUtils;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleNodeAdditionalInfo {

    private String description;
    private int layoutX;
    private int layoutY;


    public static final int CELL_SIZE = 25;

    public JsonNode toJsonNode() {
        return JsonUtils.makeNodeFromPojo(this);
    }
}
