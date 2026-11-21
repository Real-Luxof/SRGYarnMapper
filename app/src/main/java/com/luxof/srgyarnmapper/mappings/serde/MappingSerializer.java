package com.luxof.srgyarnmapper.mappings.serde;

import com.luxof.srgyarnmapper.mappings.MappingClass;
import com.luxof.srgyarnmapper.mappings.MappingEnum;
import com.luxof.srgyarnmapper.mappings.MappingField;
import com.luxof.srgyarnmapper.mappings.MappingMethod;
import com.luxof.srgyarnmapper.mappings.MappingParameter;

import org.jspecify.annotations.Nullable;

/** Any of the methods here returning <code>null</code> mean "don't put a line here". */
public interface MappingSerializer {
    public String getHeading(MappingEnum left, MappingEnum right);
    @Nullable public String serialize(MappingClass clazz);
    @Nullable public String serialize(MappingField field);
    @Nullable public String serialize(MappingMethod method);
    @Nullable public String serialize(MappingParameter param);
}
