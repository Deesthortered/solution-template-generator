package org.thingsboard.trendz.generator.model;

public abstract class ConfigurationEntity implements Comparable<ConfigurationEntity>{

    public abstract int getOrder();

    @Override
    public int compareTo(ConfigurationEntity that) {
        return Integer.compare(this.getOrder(), that.getOrder());
    }
}
