package org.thingsboard.trendz.generator.utils;

import java.util.Set;
import java.util.TreeSet;

public class MySortedSet<E> extends TreeSet<E> {

    @SafeVarargs
    private MySortedSet(E... elements) {
        super(Set.of(elements));
    }

    @SafeVarargs
    public static <E> MySortedSet<E> of(E... elements) {
        return new MySortedSet<>(elements);
    }
}
