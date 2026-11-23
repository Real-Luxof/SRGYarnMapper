package com.luxof.srgyarnmapper.mappings;

import com.luxof.srgyarnmapper.mappings.serde.MappingParser;
import com.luxof.srgyarnmapper.mappings.serde.MappingSerializer;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** use one of the static methods to create one of these. */
public record Mappings(MappingEnum from, MappingEnum to, Map<String, MappingClass> mappings) {
    public static final Pattern ARGS_SEPERATOR_REGEX = Pattern.compile("\\[*([BCDFIJSZ]|L[^;]+;)");

    public MappingClass getClass(String className) {
        return mappings.getOrDefault(
            String.valueOf(className),
            // just to satisfy SRG ID wanters
            new MappingClass(className, Map.of(), List.of(), null)
        );
    }
    public String translateClass(String className) {
        return getClass(className).name();
    }
    public MappingMethod getMethod(String className, String method) {
        for (var entry : getClass(className).methods()) {
            String key = entry.getKey();
            MappingMethod val = entry.getValue();
            // stringcmp gets tiring for the 'puter
            if (
                key.hashCode() == method.hashCode() &&
                key.equals(method)
            ) {
                return val;
            }
        }
        // sometimes they don't provide these two...
        return method.startsWith("<clinit>") || method.startsWith("<init>")
            ? new MappingMethod(
                null,
                method,
                List.of(),
                null,
                method.equals("<clinit>")
            )
            // see reverseMethod's reason for doing something like this
            : new MappingMethod(
                null, method, List.of(), null, null
            );
    }
    public String translateMethod(String className, String method) {
        return getMethod(className, method).name();
    }
    public MappingField getField(String className, String field) {
        return getClass(className).fields().get(field);
        //return getClass(className).fields().getOrDefault(
        //    field,
        //    new MappingField(null, field, null)
        //);
    }
    public String translateField(String className, String field) {
        return getField(className, field).name();
    }
    public MappingParameter getParameter(String clazz, String method, int param) {
        var params = getMethod(clazz, method).parameters();
        return param < params.size()
            ? params.get(param).getValue()
            : new MappingParameter("", null);
    }

    public String reverseClass(String outputClass) {
        for (var entry : mappings.entrySet()) {
            if (outputClass.equals(entry.getValue().name())) {
                return entry.getKey();
            }
        }
        // you're not supposed to reach this point, really. at least if you're using actual mappings
        // DontObfuscate is covered in Yarn but not Intermediary...
        return outputClass;
        //throw new RuntimeException("You're not supposed to reach this point, but here we are. " + outputClass);
        // special little snowflake. fuckass
        /*mappings.forEach((key, val) -> {
            if (outputClass.equals(val.name()))
                return key;
        });*/
    }
    public String reverseMethod(String className, String outputMethod) {
        for (var entry : getClass(className).methods()) {
            String methodName = entry.getValue().name();
            if (methodName.hashCode() == outputMethod.hashCode() && methodName.equals(outputMethod))
                return entry.getKey();
        }
        // the mappings are picky about when they do and don't include <init> and <clinit>
        // and sometimes they skip certain methods (like valueOf() and shouldSkip()) because
        // they're already named
        String[] outputMethodPieces = outputMethod.split("\\(|\\)");
        String outputArgs = outputMethodPieces[1];
        String outputReturn = outputMethodPieces[2];

        return outputMethodPieces[0] + "(" +
            String.join("", ARGS_SEPERATOR_REGEX.matcher(outputArgs).results()
                .map(r -> r.group())
                .map(s -> s.startsWith("L") ? "L" + reverseClass(s.substring(1, s.length() - 1)) + ";" : s)
                .toList())
            + ")" + (outputReturn.startsWith("L") ? "L" + reverseClass(outputReturn.substring(1, outputReturn.length() - 1)) + ";" : outputReturn);
        //if (outputMethod.equals("<clinit>") || outputMethod.equals("<init>"))
        //    return outputMethod;
        //throw new RuntimeException("Wtf " + className + ":" + outputMethod);
    }
    public String reverseField(String className, String outputField) {
        for (var entry : getClass(className).fields().entrySet()) {
            String fieldName = entry.getValue().name();
            if (
                fieldName.hashCode() == outputField.hashCode() &&
                fieldName.equals(outputField)
            )
                return entry.getKey();
        }
        // ughh...
        return outputField;
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

            clazz.methods().forEach(entry -> {
                String methodKey = entry.getKey();
                MappingMethod method = entry.getValue();
                String mL = serializer.serialize(methodKey, method);
                if (mL != null)
                    buf.add(mL);

                int i = 0;
                for (var paramEntry : method.parameters()) {
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

        System.out.printf("Parsing mappings %s -> %s!\n", left.toString(), right.toString());

        Map<String, MappingClass> mappings = new HashMap<>();

        String currentClassFrom = null;
        MappingClass currentClass = null;
        String currentMethodFrom = null;
        MappingMethod currentMethod = null;

        while (parser.nextTokenExists()) {
            String from = parser.extractFrom();

            if (parser.isClassToken()) {
                if (currentMethod != null) {
                    currentClass.methods().add(Map.entry(currentMethodFrom, currentMethod.immut()));
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
                    currentClass.methods().add(Map.entry(currentMethodFrom, currentMethod.immut()));
                    currentMethodFrom = null;
                    currentMethod = null;
                }
                currentMethodFrom = from;
                currentMethod = parser.extractMethod();

            } else if (parser.isParamToken())
                currentMethod.parameters().add(Map.entry(
                    from,
                    parser.extractParam()
                ));
        }


        if (currentMethod != null)
            currentClass.methods().add(Map.entry(currentMethodFrom, currentMethod));
        if (currentClass != null)
            mappings.put(currentClassFrom, currentClass.withImmutableMaps());
        parser.finishParsing();

        return new Mappings(left, right, Map.copyOf(mappings));
    }

    @Nullable
    public static Integer yoinkSRGID(@Nullable SRGIDHolder... holders) {
        return yoinkNonNull(Stream.of(holders).flatMap(h -> h != null ? Stream.of(h.srgID()) : Stream.of()));
    }

    @Nullable
    public static Boolean yoinkIsStatic(MappingMethod... methods) {
        return yoinkNonNull(Stream.of(methods).map(m -> m.isStatic()));
    }

    @Nullable
    public static String yoinkLineAndColumn(MappingMethod... methods) {
        return yoinkNonNull(Stream.of(methods).map(m -> m.lineAndColumn()));
    }

    @Nullable
    public static <T extends Object> T yoinkNonNull(Stream<T> objs) {
        for (T obj : objs.toList()) {
            if (obj != null) return obj;
        }
        return null;
    }
}
