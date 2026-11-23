package com.luxof.srgyarnmapper.mappings.serde;

import com.luxof.srgyarnmapper.mappings.MappingClass;
import com.luxof.srgyarnmapper.mappings.MappingEnum;
import com.luxof.srgyarnmapper.mappings.MappingField;
import com.luxof.srgyarnmapper.mappings.MappingMethod;
import com.luxof.srgyarnmapper.mappings.MappingParameter;

import static com.luxof.srgyarnmapper.mappings.Mappings.ARGS_SEPERATOR_REGEX;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface MappingParser {
    /** execute this first. */
    public void beginParsing(String lines);

    public MappingEnum getLeft();
    public MappingEnum getRight();

    /** moves to select the next token if it exists in a way compliant
     * with while-loops rather than do-while loops.
     * <br>Like a <code>Matcher</code>. */
    public boolean nextTokenExists();

    public boolean isClassToken();
    public boolean isFieldToken();
    public boolean isMethodToken();
    public boolean isParamToken();

    public String extractFrom();
    public MappingClass extractClass();
    public MappingField extractField();
    public MappingMethod extractMethod();
    public MappingParameter extractParam();

    /** execute this at last. */
    public void finishParsing();

    default String translateMethodType(String type) {
        String[] argsAndRet = type.split("\\)");
        String args = argsAndRet[0].substring(1);
        String ret = argsAndRet[1];

        return "(" + String.join(
            "",
            ARGS_SEPERATOR_REGEX.matcher(args).results()
                .map(r -> r.group())
                .map(s -> s.startsWith("L")
                        ? "L" + quickAndDirtyMethodTypeTranslation(s.substring(1, s.length() - 1)) + ";"
                        : s
                )
                .toList()
        ) + ")" + (
            ret.startsWith("L")
                ? "L" + quickAndDirtyMethodTypeTranslation(ret.substring(1, ret.length() - 1)) + ";"
                : ret
        );
    }
    abstract String quickAndDirtyMethodTypeTranslation(String clazz);

    public static MappingParser TINYV2_PARSER = new MappingParser() {
        public final Pattern TINYV2_PATTERN = Pattern.compile("(?m)^\\s*((?<qualifier>\\S)\\t((?<type>([BCDFIJLSZ\\[]\\S*)|(\\(\\S+))\\t*)?(\\d+)?(?<from>\\S*)\\t+(?<to>\\S+))");
        public final Pattern TINYV2_ONLYCLASSES_PATTERN = Pattern.compile("(?m)^c\\t(?<from>\\S+)\\t(?<to>\\S+)");
        public Matcher matcher;
        public MappingEnum left;
        public MappingEnum right;
        public Map<String, String> quickAndDirtyClassTranslatorMap = new HashMap<>();

        @Override
        public void beginParsing(String lines) {
            this.matcher = TINYV2_PATTERN.matcher(lines);
            String[] header = lines.split("\n")[0].split("\t");
            this.left = MappingEnum.fromTINYV2(header[3]);
            this.right = MappingEnum.fromTINYV2(header[4]);
            
            TINYV2_ONLYCLASSES_PATTERN.matcher(lines).results().forEach(
                r -> quickAndDirtyClassTranslatorMap.put(r.group("from"), r.group("to"))
            );
        }
        @Override public MappingEnum getLeft() { return this.left; }
        @Override public MappingEnum getRight() { return this.right; }
        @Override public boolean nextTokenExists() {
            return this.matcher.find();
        }
        @Override public boolean isClassToken() {
            return this.matcher.group("qualifier").equals("c");
        }
        @Override public boolean isFieldToken() {
            return this.matcher.group("qualifier").equals("f");
        }
        @Override public boolean isMethodToken() {
            return this.matcher.group("qualifier").equals("m");
        }
        @Override public boolean isParamToken() {
            return this.matcher.group("qualifier").equals("p");
        }
        @Override public String extractFrom() {
            return this.matcher.group("from")
                + (isMethodToken() ? this.matcher.group("type") : "");
        }
        @Override public MappingClass extractClass() {
            return new MappingClass(
                this.matcher.group("to"),
                new HashMap<>(),
                new LinkedList<>(),
                null
            );
        }
        @Override public MappingField extractField() {
            return new MappingField(
                this.matcher.group("type"),
                this.matcher.group("to"),
                null
            );
        }
        @Override public MappingMethod extractMethod() {
            translateMethodType(this.matcher.group("type"));
            return new MappingMethod(
                null,
                this.matcher.group("to") + translateMethodType(this.matcher.group("type")),
                new LinkedList<>(),
                null,
                null
            );
        }
        @Override public MappingParameter extractParam() {
            return new MappingParameter(this.matcher.group("to"), null);
        }
        @Override
        public void finishParsing() {
            this.matcher = null;
            this.left = null;
            this.right = null;
        }

        @Override public String quickAndDirtyMethodTypeTranslation(String clazz) {
            return quickAndDirtyClassTranslatorMap.getOrDefault(clazz, clazz);
        }
    };

    public static MappingParser TSRG2_PARSER = new MappingParser() {
        public final Pattern TSRG2_PATTERN = Pattern.compile("(?m)(?<obviouslyAClass>^(?=\\S))?((?<paramIdx>\\d+) )?(?<from>\\S+) ((?<type>\\(\\S+) )?(?<to>\\S+) (?<srgID>\\d+)(?<isStatic>\\n\\t\\tstatic)?");
        public final Pattern TSRG2_ONLYCLASSES_PATTERN = Pattern.compile("(?m)^(?<from>\\S+) (?<to>\\S+)");
        public Matcher matcher;
        public MappingEnum left;
        public MappingEnum right;
        public Map<String, String> quickAndDirtyClassTranslatorMap = new HashMap<>();

        @Override
        public void beginParsing(String lines) {
            this.matcher = TSRG2_PATTERN.matcher(lines);
            String[] header = lines.split("\n")[0].split(" ");
            this.left = MappingEnum.fromTSRG2(header[1]);
            this.right = MappingEnum.fromTSRG2(header[2]);
            
            TSRG2_ONLYCLASSES_PATTERN.matcher(lines).results().forEach(
                r -> quickAndDirtyClassTranslatorMap.put(r.group("from"), r.group("to"))
            );
        }
        @Override public MappingEnum getLeft() { return this.left; }
        @Override public MappingEnum getRight() { return this.right; }
        @Override public boolean nextTokenExists() { return this.matcher.find(); }
        @Override public boolean isClassToken() {
            return this.matcher.group("obviouslyAClass") != null;
        }
        @Override public boolean isFieldToken() {
            return this.matcher.group("obviouslyAClass") == null && this.matcher.group("type") == null && this.matcher.group("paramIdx") == null;
        }
        @Override public boolean isMethodToken() {
            return this.matcher.group("type") != null;
        }
        @Override public boolean isParamToken() {
            return this.matcher.group("paramIdx") != null;
        }
        @Override public String extractFrom() {
            return this.matcher.group("from")
                + (isMethodToken() ? matcher.group("type") : "");
        }
        @Override public MappingClass extractClass() {
            return new MappingClass(
                this.matcher.group("to"),
                new HashMap<>(),
                new LinkedList<>(),
                Integer.parseInt(matcher.group("srgID"))
            );
        }
        @Override public MappingField extractField() {
            return new MappingField(
                null,
                this.matcher.group("to"),
                Integer.parseInt(matcher.group("srgID"))
            );
        }
        @Override public MappingMethod extractMethod() {
            return new MappingMethod(
                null,
                this.matcher.group("to") + translateMethodType(matcher.group("type")),
                new LinkedList<>(),
                Integer.parseInt(matcher.group("srgID")),
                matcher.group("isStatic") != null
            );
        }
        @Override public MappingParameter extractParam() {
            return new MappingParameter(
                this.matcher.group("to"),
                Integer.parseInt(matcher.group("srgID"))
            );
        }
        @Override
        public void finishParsing() {
            this.matcher = null;
            this.left = null;
            this.right = null;
        }

        @Override public String quickAndDirtyMethodTypeTranslation(String clazz) {
            return quickAndDirtyClassTranslatorMap.getOrDefault(clazz, clazz);
        }
    };

    public static MappingParser PROGUARD_PARSER = new MappingParser() {
        private static final Pattern PROGUARD_PATTERN = Pattern.compile("(?m)^(\\h*((?<lineAndColumn>\\d+:\\d+):)?(?<type>\\S+) )?(?<to>[^\\(\\s]+)(?<arguments>\\([^\\)]*\\))? -> (?<from>[^:\\s]+):?");
        public final Pattern PROGUARD_ONLYCLASSES_PATTERN = Pattern.compile("(?m)^(?<from>\\S+) -> (?<to>\\S+)$");
        public Matcher matcher;
        public MappingEnum left;
        public MappingEnum right;
        public Map<String, String> quickAndDirtyClassTranslatorMap = new HashMap<>();

        @Override
        public void beginParsing(String lines) {
            this.matcher = PROGUARD_PATTERN.matcher(lines);
            this.left = MappingEnum.OBF;
            this.right = MappingEnum.MOJMAP;
            
            PROGUARD_ONLYCLASSES_PATTERN.matcher(lines).results().forEach(
                r -> quickAndDirtyClassTranslatorMap.put(
                    parseThisWeirdType(r.group("from")),
                    r.group("to")
                )
            );
        }
        @Override public MappingEnum getLeft() { return this.left; }
        @Override public MappingEnum getRight() { return this.right; }
        @Override public boolean nextTokenExists() { return this.matcher.find(); }
        @Override public boolean isClassToken() {
            return this.matcher.group("type") == null;
        }
        @Override public boolean isFieldToken() {
            return this.matcher.group("arguments") != null;
        }
        @Override public boolean isMethodToken() {
            return this.matcher.group("lineAndColumn") != null;
        }
        @Override public String extractFrom() {
            return this.matcher.group("from") + (isMethodToken()
                ? parseTheseArguments(this.matcher.group("arguments"))
                    + parseThisWeirdType(this.matcher.group("type"))
                : "");
        }
        @Override public MappingClass extractClass() {
            return new MappingClass(
                parseThisWeirdType(this.matcher.group("to")),
                new HashMap<>(),
                new LinkedList<>(),
                null
            );
        }
        @Override public MappingField extractField() {
            return new MappingField(
                parseThisWeirdType(this.matcher.group("type")),
                this.matcher.group("to"),
                null
            );
        }
        @Override public MappingMethod extractMethod() {
            return new MappingMethod(
                this.matcher.group("lineAndColumn"),
                this.matcher.group("to")
                    + translateMethodType(
                        parseTheseArguments(this.matcher.group("arguments"))
                            + parseThisWeirdType(this.matcher.group("type"))
                    ),
                new LinkedList<>(),
                null,
                null
            );
        }
        @Override
        public void finishParsing() {
            this.matcher = null;
            this.left = null;
            this.right = null;
        }

        @Override public String quickAndDirtyMethodTypeTranslation(String clazz) {
            return quickAndDirtyClassTranslatorMap.getOrDefault(clazz, clazz);
        }

        public String parseTheseArguments(String types) {
            return "(" +
                String.join(
                    "",
                    List.of(types.substring(0, types.length() - 1).split(Pattern.quote(",")))
                        .stream()
                        .map(this::parseThisWeirdType)
                        .toList()
                    )
                + ")";
        }
        public String parseThisWeirdType(String type) {
            String prefix = "";
            String actualType = type;
            while (actualType.endsWith("[]")) {
                prefix += "[";
                actualType = actualType.substring(0, actualType.length() - 2);
            }
            return prefix + switch (type) {
                case "byte" -> "B";
                case "char" -> "C";
                case "double" -> "D";
                case "float" -> "F";
                case "int" -> "I";
                case "long" -> "J";
                case "short" -> "S";
                case "boolean" -> "Z";
                case "void" -> "V";
                default -> "L" + type.replaceAll(Pattern.quote("."), "/") + ";";
            };
        }

        @Override public boolean isParamToken() { return false; }
        @Override public MappingParameter extractParam() { return null; }
    };
}
