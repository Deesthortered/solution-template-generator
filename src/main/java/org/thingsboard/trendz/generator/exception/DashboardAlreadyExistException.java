package org.thingsboard.trendz.generator.exception;

import org.thingsboard.server.common.data.Dashboard;

public class DashboardAlreadyExistException extends SolutionTemplateGeneratorException {

    private final Dashboard dashboard;

    public DashboardAlreadyExistException(Dashboard dashboard) {
        super("Dashboard is already exists: " + dashboard.getName());
        this.dashboard = dashboard;
    }
}
