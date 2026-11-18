package com.luxof.srgyarnmapper.mappings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        return getClass(className).methods().get(method);
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


    private static final Pattern TSRG2_PATTERN = Pattern.compile("(?<from>\\S+) (?<to>((?<className>net\\S+) (?<srgcid>\\d+))|((?<fieldName>\\S+) (?<srgfid>\\d+)$)|((?<methodType>\\(\\S*\\)\\S+) (?<methodName>\\S+) (?<srgmid>\\d+)(?<isStatic>\\n\\t+static)?)|((?<parameterActualFrom>\\S+) (?<parameterName>\\S+) (?<srgpid>\\d+)))");
    public static Mappings ofTSRG2(String lines) {
        String[] header = lines.split("\n")[0].split(" ");
        MappingEnum left = MappingEnum.fromTSRG2(header[1]);
        MappingEnum right = MappingEnum.fromTSRG2(header[2]);

        Map<String, MappingClass> mappings = new HashMap<>();

        String currentClassFrom = null;
        MappingClass currentClass = null;
        String currentMethodFrom = null;
        MappingMethod currentMethod = null;
        Matcher matcher = TSRG2_PATTERN.matcher(lines);

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
                        currentMethod.parameters(),
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
                    new LinkedHashMap<>(),
                    Integer.parseInt(matcher.group("srgmid")),
                    matcher.group("isStatic") != null
                );

            } else if (parameterName != null)
                currentMethod.parameters().put(
                    matcher.group("parameterActualFrom"),
                    new MappingParameter(parameterName, Integer.parseInt(matcher.group("srgpid")))
                );
        }


        if (currentMethod != null)
            currentClass.methods().put(
                currentMethodFrom,
                new MappingMethod(
                    currentMethod.type(),
                    currentMethod.name(),
                    currentMethod.parameters(),
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


            return new Mappings(left, right, Map.copyOf(mappings));
    }

    private static final Pattern TINYV2_PATTERN = Pattern.compile("^\\s*((?<qualifier>\\S)\\t((?<type>(?=[BCDFIJLSZ\\[])\\S+)\\t*)?(\\d+)?(?<from>\\S*)\\t+(?<to>\\S+))|(c\\t(?<classFrom>\\S+)\\t(?<classTo>\\S+))");
    public static Mappings ofTinyV2(String lines) {
        String[] header = lines.split("\n")[0].split(" ");
        MappingEnum left = MappingEnum.fromTINYV2(header[1]);
        MappingEnum right = MappingEnum.fromTINYV2(header[2]);

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
                        currentMethod.parameters(),
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
                    new LinkedHashMap<>(),
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
                    currentMethod.parameters(),
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
