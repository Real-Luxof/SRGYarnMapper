package com.luxof.srgyarnmapper.mappings;

import java.util.Map;

import org.jspecify.annotations.Nullable;

public record MappingMethod(
    @Nullable String type,
    String name,
    Map<String, MappingParameter> parameters,
    @Nullable Integer srgID,
    Boolean isStatic
) implements SRGIDHolder, TypeHolder {}
