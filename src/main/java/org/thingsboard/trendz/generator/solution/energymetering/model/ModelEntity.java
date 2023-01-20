package org.thingsboard.trendz.generator.solution.energymetering.model;

import org.jetbrains.annotations.NotNull;

public interface ModelEntity extends Comparable<ModelEntity> {

    String getSystemName();
    void setSystemName(String systemName);

    String getSystemLabel();
    void setSystemLabel(String systemName);

    @Override
    default int compareTo(@NotNull ModelEntity that) {
        return this.getSystemName().compareTo(that.getSystemName());
    }
}
