package com.luxof.srgyarnmapper.mappings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/** use one of the static methods to create one of these. */
public record Mappings(MappingEnum from, MappingEnum to, Map<String, MappingClass> mappings) {
    public MappingClass getClass(String className) {
        return mappings.get(className);
    }
    public String translateClass(String className) {
        return getClass(className).name();
    }
    public MappingMethod getMethod(String className, String method) {
        return getClass(className).methods().computeIfAbsent(
            method,
            s -> s.equals("<clinit>") || s.equals("<init>")
                ? new MappingMethod(
                    "()" + (s.equals("<clinit>") ? "V" : "L" + className + ";"),
                    s,
                    new LinkedHashMap<>(),
                    null,
                    s.equals("<clinit>")
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
            if (outputClass.equals(entry.getValue().name()))
                return entry.getKey();
        }
        // you're not supposed to reach this point, really. at least if you're using actual mappings
        return null;
        // special little snowflake. fuckass
        /*mappings.forEach((key, val) -> {
            if (outputClass.equals(val.name()))
                return key;
        });*/
    }
    public String reverseMethod(String className, String outputMethod) {
        for (var entry : getClass(className).methods().entrySet()) {
            if (outputMethod.equals(entry.getValue().name()))
                return entry.getKey();
        }
        // Intermediary (TinyV2) doesn't encode this but ProGuard does <init> and SRG does both
        // actually the mappings are picky about when they do and don't include these
        // so just. fallback
        if (outputMethod.equals("<clinit>") || outputMethod.equals("<init>"))
            return outputMethod;
        return null;
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
        return "";
    }


    public String toTSRG2() {
        // fucking "can't assign in foreach" FUCK YOU
        List<String> buf = new ArrayList<>(List.of(
            String.format("tsrg2 %s %s id\n", from.toTSRG2(), to.toTSRG2())
        ));

        mappings.forEach((clazzKey, clazz) -> {
            buf.add(String.format("%s %s %d", clazzKey, clazz.name(), clazz.srgID()));

            clazz.fields().forEach((fieldKey, field) -> {
                buf.add(String.format("\t%s %s %d", fieldKey, field.name(), field.srgID()));
            });

            clazz.methods().forEach((methodKey, method) -> {
                buf.add(String.format(
                    "\t%s %s %s %d",
                    methodKey,
                    method.type(),
                    method.name(),
                    method.srgID()
                ));

                if (method.isStatic())
                    buf.add("\t\tstatic");

                int i = 0;
                for (var paramEntry : method.parameters().sequencedEntrySet()) {
                    buf.add(String.format(
                        "\t\t%d %s %s %d",
                        i,
                        paramEntry.getKey(),
                        paramEntry.getValue().name(),
                        paramEntry.getValue().srgID()
                    ));
                    i++;
                }
            });
        });

        return String.join("\n", buf);
    }

    public String toTINYV2() {
        List<String> buf = new ArrayList<>(List.of(
            String.format("tiny\t2\t0\t%s\t%s"),
            from.toTINYV2(),
            to.toTINYV2()
        ));

        mappings.forEach((clazzKey, clazz) -> {
            buf.add(String.format("c\t%s\t%s", clazzKey, clazz.name()));

            clazz.fields().forEach((fieldKey, field) -> {
                buf.add(String.format(
                    "\tf\t%s\t%s\t%s",
                    field.type(),
                    fieldKey,
                    field.name()
                ));
            });

            clazz.methods().forEach((methodKey, method) -> {
                buf.add(String.format(
                    "\tm\t%s\t%s\t%s",
                    method.type(),
                    methodKey,
                    method.name()
                ));

                int i = 0;
                for (var paramEntry : method.parameters().sequencedEntrySet()) {
                    buf.add(String.format(
                        "\t\tp\t%d\t%s\t%s",
                        i,
                        paramEntry.getKey(),
                        paramEntry.getValue().name()
                    ));
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

            if (
                currentMethod != null &&
                (parser.isClassToken() || parser.isMethodToken())
            ) {
                currentClass.methods().put(currentMethodFrom, currentMethod);
                currentMethodFrom = null;
                currentMethod = null;
            }

            if (parser.isClassToken()) {
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
}
