package org.thingsboard.trendz.generator.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Attribute<T> {

    public enum Scope {
        CLIENT_SCOPE,
        SERVER_SCOPE,
        SHARED_SCOPE,
    }

    private String key;
    private T value;
}
