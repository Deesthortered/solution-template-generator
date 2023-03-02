package org.thingsboard.trendz.generator.solution.greenhouse.configuration;

public enum WorkerInChargeName {
    // Stuttgart
    DANIEL_KRUGGER("Daniel Krugger"),
    MARK_ZELTER("Mark Zelter"),
    LUIS_WITT("Luis Witt"),

    // Krakow
    ANJEY_MANISKII("Anjey Maniskii"),
    LECH_PAWLOWSKI("Lech Pawłowski"),
    BOGUSLAW_VISHNEVSKII("Bogusław Vishnevskii"),

    // Warszawa
    MIROSLAW_MORACHEVSKII("Mirosław Morachevskii"),
    ZIEMOWIT_YANKOVSKII("Ziemowit Yankovskii"),
    WOJCIECH_DUNAEVSKII("Wojciech Dunaevskii"),

    // Kyiv
    IGOR_PETROVICH("Ігор Петрович"),
    PETRO_VYNNYCHENKO("Петро Винниченко"),
    KYRYLLO_BONDARENKO("Кирило Богдаренко"),
    ;

    private final String name;

    WorkerInChargeName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
