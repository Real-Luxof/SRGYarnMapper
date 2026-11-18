package com.luxof.srgyarnmapper.mappings;

import java.util.LinkedHashMap;

import org.jspecify.annotations.Nullable;

public record MappingMethod(
    @Nullable String type,
    String name,
    LinkedHashMap<String, MappingParameter> parameters,
    @Nullable Integer srgID,
    Boolean isStatic
) implements SRGIDHolder, TypeHolder {}
