package org.thingsboard.trendz.generator.model;

public abstract class ModelEntity implements Comparable<ModelEntity> {

    abstract public String getSystemName();
    abstract public String getSystemLabel();
    abstract public String entityType();

    @Override
    public int compareTo(ModelEntity that) {
        return this.getSystemName().compareTo(that.getSystemName());
    }

    @Override
    public int hashCode() {
        return this.getSystemName().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj.getClass() != this.getClass()) {
            return false;
        }

        final ModelEntity that = (ModelEntity) obj;
        if (this.getSystemName() == null) {
            return that.getSystemName() == null;
        } else {
            return this.getSystemName().equals(that.getSystemName());
        }
    }
}
