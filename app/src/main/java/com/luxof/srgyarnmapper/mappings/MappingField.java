package com.luxof.srgyarnmapper.mappings;

import org.jspecify.annotations.Nullable;

public record MappingField(
    @Nullable String type,
    String name,
    @Nullable Integer srgID
) {}
