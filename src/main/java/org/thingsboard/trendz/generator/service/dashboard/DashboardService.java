package org.thingsboard.trendz.generator.service.dashboard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.trendz.generator.exception.DashboardAlreadyExistException;
import org.thingsboard.trendz.generator.service.rest.TbRestClient;

import java.util.Set;

@Slf4j
@Service
public class DashboardService {

    private final TbRestClient tbRestClient;

    public DashboardService(
            TbRestClient tbRestClient
    ) {
        this.tbRestClient = tbRestClient;
    }


    public void createDashboardItems(String solutionName, CustomerId customerId) {
        if (tbRestClient.isPe()) {
            EntityGroup dashboardGroup = tbRestClient.createEntityGroup(
                    getDashboardGroupName(solutionName),
                    EntityType.DASHBOARD,
                    customerId.getId(),
                    true
            );

            Dashboard dashboard = tbRestClient.createDashboard(getDashboardName(solutionName), customerId);
            tbRestClient.addEntitiesToTheGroup(dashboardGroup.getUuidId(), Set.of(dashboard.getUuidId()));
        } else {
            Dashboard dashboard = tbRestClient.createDashboard(getDashboardName(solutionName));
        }
    }

    public void validateDashboardItems(String solutionName, CustomerId customerId) {
        if (tbRestClient.isPe()) {
            if (customerId == null) {
                tbRestClient.getEntityGroup(
                        getDashboardGroupName(solutionName),
                        EntityType.DASHBOARD,
                        tbRestClient.getTenantId(),
                        false
                );

                tbRestClient.getAllTenantDashboards()
                        .stream()
                        .filter(dashboard -> getDashboardName(solutionName).equals(dashboard.getName()))
                        .forEach(dashboard -> {
                            throw new DashboardAlreadyExistException(dashboard);
                        });
            } else {
                tbRestClient.getEntityGroup(
                        getDashboardGroupName(solutionName),
                        EntityType.DASHBOARD,
                        customerId.getId(),
                        true
                );

                tbRestClient.getAllCustomerDashboards(customerId.getId())
                        .stream()
                        .filter(dashboard -> getDashboardName(solutionName).equals(dashboard.getName()))
                        .forEach(dashboard -> {
                            throw new DashboardAlreadyExistException(dashboard);
                        });
            }
        } else {
            tbRestClient.getAllTenantDashboards()
                    .stream()
                    .filter(dashboard -> getDashboardName(solutionName).equals(dashboard.getName()))
                    .forEach(dashboard -> {
                        throw new DashboardAlreadyExistException(dashboard);
                    });
        }
    }

    public void deleteDashboardItems(String solutionName, CustomerId customerId) {
        if (tbRestClient.isPe()) {
            if (customerId == null) {
                tbRestClient.getAllTenantDashboards()
                        .stream()
                        .filter(dashboard -> getDashboardName(solutionName).equals(dashboard.getName()))
                        .forEach(dashboard -> tbRestClient.deleteDashboard(dashboard.getUuidId()));
            } else {
                tbRestClient.getAllCustomerDashboards(customerId.getId())
                        .stream()
                        .filter(dashboard -> getDashboardName(solutionName).equals(dashboard.getName()))
                        .forEach(dashboard -> tbRestClient.deleteDashboard(dashboard.getUuidId()));
            }
        } else {
            tbRestClient.getAllTenantDashboards()
                    .stream()
                    .filter(dashboard -> getDashboardName(solutionName).equals(dashboard.getName()))
                    .forEach(dashboard -> tbRestClient.deleteDashboard(dashboard.getUuidId()));
        }
    }


    private String getDashboardGroupName(String solutionName) {
        return solutionName + " Dashboard Group";
    }

    private String getDashboardName(String solutionName) {
        return solutionName + " Dashboard";
    }
}
