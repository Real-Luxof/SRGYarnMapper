package com.luxof.srgyarnmapper.mappings;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

public record MappingClass(
    String name,
    Map<String, MappingField> fields,
    List<Map.Entry<String, MappingMethod>> methods,
    @Nullable Integer srgID
) implements SRGIDHolder {
    public MappingClass withImmutableMaps() {
        return new MappingClass(name, Map.copyOf(fields), List.copyOf(methods), srgID);
    }
}
