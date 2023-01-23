package org.thingsboard.trendz.generator.model.tb;

public enum NodeConnectionType {
    SUCCESS("Success"),
    FAILURE("Failure"),
    ;

    private final String type;

    NodeConnectionType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return this.type;
    }
}
