package com.luxof.srgyarnmapper.mappings;

import java.util.Map;

import org.jspecify.annotations.Nullable;

public record MappingClass(
    String name,
    Map<String, MappingField> fields,
    Map<String, MappingMethod> methods,
    @Nullable Integer srgID
) implements SRGIDHolder {}
