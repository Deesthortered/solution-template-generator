package org.thingsboard.trendz.generator.model;

public enum NodeConnectionType {
    SUCCESS("Success"),
    FAILURE("Failure"),
    ;

    private final String type;

    NodeConnectionType(String type) {
        this.type = type;
    }

    public String getType() {
        return this.type;
    }
}
