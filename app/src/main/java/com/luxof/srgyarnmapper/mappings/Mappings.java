package com.luxof.srgyarnmapper.mappings;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Mappings {
    public final Map<String, MappingClass> mappings;

    /** Use one of the static methods instead. */
    public Mappings(
        Map<String, MappingClass> mappings
    ) {
        this.mappings = mappings;
    }

    public String translateClass(String className) {
        return mappings.get(className).name();
    }
    public MappingMethod getMethod(String className, String method) {
        return mappings.get(className).methods().get(method);
    }
    public String translateMethod(String className, String method) {
        return getMethod(className, method).name();
    }
    public MappingField getField(String className, String field) {
        return mappings.get(className).fields().get(field);
    }
    public String translateField(String className, String field) {
        return getField(className, field).name();
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
        for (var entry : mappings.get(className).methods().entrySet()) {
            if (outputMethod.equals(entry.getValue().name()))
                return entry.getKey();
        }
        return null;
    }
    public String reverseField(String className, String outputField) {
        for (var entry : mappings.get(className).fields().entrySet()) {
            if (outputField.equals(entry.getValue().name()))
                return entry.getKey();
        }
        return null;
    }


    private static final Pattern TSRGV2_PATTERN = Pattern.compile("(?<from>\\S+) (?<to>((?<className>net\\S+) (?<srgcid>\\d+))|(o (?<parameterName>\\S+) (?<srgpid>\\d+))|((?<fieldName>\\S+) (?<srgfid>\\d+)$)|((?<methodType>\\(\\S*\\)\\S+) (?<methodName>\\S+) (?<srgmid>\\d+)(?<isStatic>\\n\\s+static)?))");
    public static Mappings ofTSRGV2(String lines) {
        Map<String, MappingClass> mappings = new HashMap<>();

        String currentClassFrom = null;
        MappingClass currentClass = null;
        String currentMethodFrom = null;
        MappingMethod currentMethod = null;
        Matcher matcher = TSRGV2_PATTERN.matcher(lines);

        while (matcher.find()) {
            String from = matcher.group("from");

            String className = matcher.group("className");
            String fieldName = matcher.group("fieldName");
            String methodName = matcher.group("methodName");
            String parameterName = matcher.group("parameterName");


            if ((className != null || methodName != null) && currentMethod != null) {
                currentClass.methods().put(
                    currentMethodFrom,
                    new MappingMethod(
                        matcher.group("methodType"),
                        currentMethod.name(),
                        Map.copyOf(currentMethod.parameters()),
                        currentMethod.srgID(),
                        currentMethod.isStatic()
                    )
                );
                currentMethodFrom = null;
                currentMethod = null;
            }


            if (className != null) {
                if (currentClass != null)
                    mappings.put(
                        currentClassFrom,
                        new MappingClass(
                            currentClass.name(),
                            Map.copyOf(currentClass.fields()),
                            Map.copyOf(currentClass.methods()),
                            currentClass.srgID()
                        )
                    );

                currentClassFrom = from;
                currentClass = new MappingClass(
                    className,
                    new HashMap<>(),
                    new HashMap<>(),
                    Integer.parseInt(matcher.group("srgcid"))
                );

            } else if (fieldName != null)
                currentClass.fields().put(
                    from,
                    new MappingField(
                        null,
                        fieldName,
                        Integer.parseInt(matcher.group("srgfid"))
                    )
                );

            else if (methodName != null) {
                currentMethodFrom = from;
                currentMethod = new MappingMethod(
                    matcher.group("methodType"),
                    methodName,
                    new HashMap<>(),
                    Integer.parseInt(matcher.group("srgmid")),
                    matcher.group("isStatic") != null
                );

            } else if (parameterName != null)
                currentMethod.parameters().put(
                    from,
                    new MappingParameter(parameterName, Integer.parseInt(matcher.group("srgpid")))
                );
        }


        if (currentMethod != null)
            currentClass.methods().put(
                currentMethodFrom,
                new MappingMethod(
                    currentMethod.type(),
                    currentMethod.name(),
                    Map.copyOf(currentMethod.parameters()),
                    currentMethod.srgID(),
                    currentMethod.isStatic()
                )
            );
        if (currentClass != null)
            mappings.put(
                currentClassFrom,
                new MappingClass(
                    currentClass.name(),
                    Map.copyOf(currentClass.fields()),
                    Map.copyOf(currentClass.methods()),
                    currentClass.srgID()
                )
            );


            return new Mappings(Map.copyOf(mappings));
    }

    private static final Pattern TINYV2_PATTERN = Pattern.compile("^\\s*((?<qualifier>\\S)\\t((?<type>(?=[BCDFIJLSZ\\[])\\S+)\\t*)?(?<from>\\S+)\\t+(?<to>\\S+))|(c\\t(?<classFrom>\\S+)\\t(?<classTo>\\S+))");
    public static Mappings ofTinyV2(String lines) {
        Map<String, MappingClass> mappings = new HashMap<>();

        String currentClassFrom = null;
        MappingClass currentClass = null;
        String currentMethodFrom = null;
        MappingMethod currentMethod = null;
        Matcher matcher = TINYV2_PATTERN.matcher(lines);

        while (matcher.find()) {

            String from = matcher.group("from");
            String type = matcher.group("type");
            String to = matcher.group("to");
            String qualifier = matcher.group("qualifier");
            if (qualifier == null) qualifier = "";


            if (
                (qualifier.equals("c") || qualifier.equals("m")) &&
                currentMethod != null
            ) {
                currentClass.methods().put(
                    currentMethodFrom,
                    new MappingMethod(
                        matcher.group("methodType"),
                        currentMethod.name(),
                        Map.copyOf(currentMethod.parameters()),
                        currentMethod.srgID(),
                        currentMethod.isStatic()
                    )
                );
                currentMethodFrom = null;
                currentMethod = null;
            }


            if (qualifier.equals("c")) {
                if (currentClass != null)
                    mappings.put(
                        currentClassFrom,
                        new MappingClass(
                            currentClass.name(),
                            Map.copyOf(currentClass.fields()),
                            Map.copyOf(currentClass.methods()),
                            null
                        )
                    );

                currentClassFrom = from;
                currentClass = new MappingClass(
                    to,
                    new HashMap<>(),
                    new HashMap<>(),
                    null
                );

            } else if (qualifier.equals("f"))
                currentClass.fields().put(
                    from,
                    new MappingField(
                        type,
                        to,
                        null
                    )
                );

            else if (qualifier.equals("m")) {
                currentMethodFrom = from;
                currentMethod = new MappingMethod(
                    type,
                    to,
                    new HashMap<>(),
                    null,
                    null
                );

            } else if (qualifier.equals("p"))
                currentMethod.parameters().put(
                    from,
                    new MappingParameter(to, null)
                );
        }


        if (currentMethod != null)
            currentClass.methods().put(
                currentMethodFrom,
                new MappingMethod(
                    currentMethod.type(),
                    currentMethod.name(),
                    Map.copyOf(currentMethod.parameters()),
                    currentMethod.srgID(),
                    currentMethod.isStatic()
                )
            );
        if (currentClass != null)
            mappings.put(
                currentClassFrom,
                new MappingClass(
                    currentClass.name(),
                    Map.copyOf(currentClass.fields()),
                    Map.copyOf(currentClass.methods()),
                    currentClass.srgID()
                )
            );


        return new Mappings(Map.copyOf(mappings));
    }
}
