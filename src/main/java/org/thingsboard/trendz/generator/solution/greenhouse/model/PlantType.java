package org.thingsboard.trendz.generator.solution.greenhouse.model;

public enum PlantType {
    TOMATO("Tomato"),
    CUCUMBER("Cucumber"),
    ONION("Onion"),
    ;


    private final String name;

    PlantType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
