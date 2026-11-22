package com.luxof.srgyarnmapper.mappings;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

public record MappingMethod(
    @Nullable String lineAndColumn,
    @Nullable String type,
    String name,
    List<Map.Entry<String, MappingParameter>> parameters,
    @Nullable Integer srgID,
    Boolean isStatic
) implements SRGIDHolder, TypeHolder {
    public MappingMethod immut() {
        return new MappingMethod(
            lineAndColumn,
            type,
            name,
            List.copyOf(parameters),
            srgID,
            isStatic
        );
    }
}
