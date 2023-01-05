package org.thingsboard.trendz.generator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerUserAdditionalInfo {

    private boolean defaultDashboardFullscreen;
    private UUID defaultDashboardId;
    private boolean homeDashboardHideToolbar;
    private UUID homeDashboardId;
    private String description;


    public static CustomerUserAdditionalInfo defaultInfo() {
        return CustomerUserAdditionalInfo.builder()
                .defaultDashboardFullscreen(false)
                .homeDashboardHideToolbar(true)
                .description("")
                .build();
    }
}
