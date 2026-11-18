package com.luxof.srgyarnmapper.mappings;

import org.jspecify.annotations.Nullable;

public record MappingParameter(
    String name,
    @Nullable Integer srgID
) implements SRGIDHolder {}
