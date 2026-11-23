package com.luxof.srgyarnmapper.mappings;

import org.jspecify.annotations.Nullable;

public record MappingField(
    String type,
    String name,
    @Nullable Integer srgID
) implements SRGIDHolder {}
