package org.thingsboard.trendz.generator.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Attribute<T> {

    private String key;
    private T value;
}
