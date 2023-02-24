package org.thingsboard.trendz.generator.solution.greenhouse.model;

public enum PlantName {
    TOMATO("Tomato"),
    CUCUMBER("Cucumber"),
    ONION("Onion"),
    ;


    private final String name;

    PlantName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
