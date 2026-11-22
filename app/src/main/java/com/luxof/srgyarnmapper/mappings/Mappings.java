package com.luxof.srgyarnmapper.mappings;

import com.luxof.srgyarnmapper.mappings.serde.MappingParser;
import com.luxof.srgyarnmapper.mappings.serde.MappingSerializer;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** use one of the static methods to create one of these. */
public record Mappings(MappingEnum from, MappingEnum to, Map<String, MappingClass> mappings) {
    public MappingClass getClass(String className) {
        return mappings.get(String.valueOf(className));
    }
    public String translateClass(String className) {
        return getClass(className).name();
    }
    public MappingMethod getMethod(String className, String method) {
        return getClass(className).methods().getOrDefault(
            method,
            method.equals("<clinit>") || method.equals("<init>")
                // sometimes they don't provide these two...
                ? new MappingMethod(
                    null,
                    "()V",
                    method,
                    new LinkedHashMap<>(),
                    null,
                    method.equals("<clinit>")
                )
                : null 
        );
    }
    public String translateMethod(String className, String method) {
        return getMethod(className, method).name();
    }
    public MappingField getField(String className, String field) {
        return getClass(className).fields().get(field);
    }
    public String translateField(String className, String field) {
        return getField(className, field).name();
    }
    public MappingParameter getParameter(String clazz, String method, String param) {
        return getMethod(clazz, method).parameters().getOrDefault(
            param,
            new MappingParameter("", null)
        );
    }
    public String translateParameter(String clazz, String method, String param) {
        return getParameter(clazz, method, param).name();
    }

    public String reverseClass(String outputClass) {
        for (var entry : mappings.entrySet()) {
            if (outputClass.equals(entry.getValue().name())) {
                return entry.getKey();
            }
        }
        // you're not supposed to reach this point, really. at least if you're using actual mappings
        throw new RuntimeException("You're not supposed to reach this point, but here we are. " + outputClass);
        // special little snowflake. fuckass
        /*mappings.forEach((key, val) -> {
            if (outputClass.equals(val.name()))
                return key;
        });*/
    }
    public String reverseMethod(String className, String outputMethod) {
        for (var entry : getClass(className).methods().entrySet()) {
            System.out.println(entry.getValue().name());
            if (outputMethod.equals(entry.getValue().name()))
                return entry.getKey();
        }
        // the mappings are picky about when they do and don't include these
        if (outputMethod.equals("<clinit>") || outputMethod.equals("<init>"))
            return outputMethod;
        throw new RuntimeException("Wtf " + className + ":" + outputMethod);
    }
    public String reverseField(String className, String outputField) {
        for (var entry : getClass(className).fields().entrySet()) {
            if (outputField.equals(entry.getValue().name()))
                return entry.getKey();
        }
        return null;
    }
    public String reverseParameter(String clazz, String method, String outputParam) {
        for (var entry : getMethod(clazz, method).parameters().entrySet()) {
            if (outputParam.equals(entry.getValue().name()))
                return entry.getKey();
        }
        // i return empty here because the mappings i deal with don't necessarily do all params
        // but they *do* do all fields and methods
        // which makes sense if you think about it. params are referenced by number.
        // ^^^ two entirely unrelated thoughts? wtf do you mean?
        return "";
    }


    public String to(MappingSerializer serializer) {
        List<String> buf = new ArrayList<>(List.of(serializer.getHeading(from, to)));

        mappings.forEach((clazzKey, clazz) -> {
            String cL = serializer.serialize(clazzKey, clazz);
            if (cL != null)
                buf.add(cL);

            clazz.fields().forEach((fieldKey, field) -> {
                String fL = serializer.serialize(fieldKey, field);
                if (fL != null)
                    buf.add(fL);
            });

            clazz.methods().forEach((methodKey, method) -> {
                String mL = serializer.serialize(methodKey, method);
                if (mL != null)
                    buf.add(mL);

                int i = 0;
                for (var paramEntry : method.parameters().sequencedEntrySet()) {
                    String pL = serializer.serialize(i, paramEntry.getKey(), paramEntry.getValue());
                    if (pL != null)
                        buf.add(pL);
                    i += 1;
                }
            });
        });

        return String.join("\n", buf);
    }

    public static Mappings of(String lines, MappingParser parser) {
        parser.beginParsing(lines);
        MappingEnum left = parser.getLeft();
        MappingEnum right = parser.getRight();

        Map<String, MappingClass> mappings = new HashMap<>();

        String currentClassFrom = null;
        MappingClass currentClass = null;
        String currentMethodFrom = null;
        MappingMethod currentMethod = null;

        while (parser.nextTokenExists()) {
            String from = parser.extractFrom();

            if (parser.isClassToken()) {
                if (currentMethod != null) {
                    currentClass.methods().put(currentMethodFrom, currentMethod);
                    currentMethodFrom = null;
                    currentMethod = null;
                }
                if (currentClass != null)
                    mappings.put(currentClassFrom, currentClass.withImmutableMaps());

                currentClassFrom = from;
                currentClass = parser.extractClass();

            } else if (parser.isFieldToken())
                currentClass.fields().put(
                    from,
                    parser.extractField()
                );

            else if (parser.isMethodToken()) {
                if (currentMethod != null) {
                    currentClass.methods().put(currentMethodFrom, currentMethod);
                    currentMethodFrom = null;
                    currentMethod = null;
                }
                currentMethodFrom = from;
                currentMethod = parser.extractMethod();

            } else if (parser.isParamToken())
                currentMethod.parameters().put(
                    from,
                    parser.extractParam()
                );
        }


        if (currentMethod != null)
            currentClass.methods().put(currentMethodFrom, currentMethod);
        if (currentClass != null)
            mappings.put(currentClassFrom, currentClass.withImmutableMaps());
        parser.finishParsing();

        return new Mappings(left, right, Map.copyOf(mappings));
    }

    @Nullable
    public static Integer yoinkSRGID(SRGIDHolder... holders) {
        for (SRGIDHolder holder : holders) {
            if (holder.srgID() != null) return holder.srgID();
        }
        return null;
    }

    @Nullable
    public static String yoinkType(TypeHolder... holders) {
        for (TypeHolder holder : holders) {
            if (holder.type() != null) return holder.type();
        }
        return null;
    }

    @Nullable
    public static Boolean yoinkIsStatic(MappingMethod... methods) {
        for (MappingMethod method : methods) {
            if (method.isStatic() != null) return method.isStatic();
        }
        return null;
    }

    @Nullable
    public static String yoinkLineAndColumn(MappingMethod... methods) {
        for (MappingMethod method : methods) {
            if (method.lineAndColumn() != null) return method.lineAndColumn();
        }
        return null;
    }
}
