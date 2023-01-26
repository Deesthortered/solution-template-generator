package org.thingsboard.trendz.generator.model;

public interface ModelEntity extends Comparable<ModelEntity> {

    String getSystemName();
    void setSystemName(String systemName);

    String getSystemLabel();
    void setSystemLabel(String systemLabel);

    @Override
    default int compareTo(ModelEntity that) {
        return this.getSystemName().compareTo(that.getSystemName());
    }
}
