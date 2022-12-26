package org.thingsboard.trendz.generator.model;

public enum RelationType {
    CONTAINS("Contains"),
    MANAGES("Manages"),
    ;

    private final String type;

    RelationType(String type) {
        this.type = type;
    }

    public String getType() {
        return this.type;
    }
}
