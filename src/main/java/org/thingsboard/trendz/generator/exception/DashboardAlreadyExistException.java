package org.thingsboard.trendz.generator.exception;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.thingsboard.server.common.data.Dashboard;

@Getter
@EqualsAndHashCode(callSuper = true)
public class DashboardAlreadyExistException extends SolutionTemplateGeneratorException {

    private final Dashboard dashboard;

    public DashboardAlreadyExistException(Dashboard dashboard) {
        super("Dashboard is already exists: " + dashboard.getName());
        this.dashboard = dashboard;
    }
}
