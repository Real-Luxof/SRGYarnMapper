package com.luxof.srgyarnmapper.mappings.serde;

import com.luxof.srgyarnmapper.mappings.MappingClass;
import com.luxof.srgyarnmapper.mappings.MappingEnum;
import com.luxof.srgyarnmapper.mappings.MappingField;
import com.luxof.srgyarnmapper.mappings.MappingMethod;
import com.luxof.srgyarnmapper.mappings.MappingParameter;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
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

    public static MappingParser TINYV2_PARSER = new MappingParser() {
        public final Pattern TINYV2_PATTERN = Pattern.compile("(?m)^\\s*((?<qualifier>\\S)\\t((?<type>([BCDFIJLSZ\\[]\\S*)|(\\(\\S+))\\t*)?(\\d+)?(?<from>\\S*)\\t+(?<to>\\S+))");
        public Matcher matcher;
        public MappingEnum left;
        public MappingEnum right;

        @Override
        public void beginParsing(String lines) {
            this.matcher = TINYV2_PATTERN.matcher(lines);
            String[] header = lines.split("\n")[0].split("\t");
            this.left = MappingEnum.fromTINYV2(header[3]);
            this.right = MappingEnum.fromTINYV2(header[4]);
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
        @Override public String extractFrom() { return this.matcher.group("from"); }
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
            return new MappingMethod(
                null,
                this.matcher.group("type"),
                this.matcher.group("to"),
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
    };

    public static MappingParser TSRG2_PARSER = new MappingParser() {
        public final Pattern TSRG2_PATTERN = Pattern.compile("(?<from>\\S+) (?<to>((?<className>net\\S+) (?<srgcid>\\d+))|((?<fieldName>\\S+) (?<srgfid>\\d+)$)|((?<methodType>\\(\\S*\\)\\S+) (?<methodName>\\S+) (?<srgmid>\\d+)(?<isStatic>\\n\\t+static)?)|((?<parameterActualFrom>\\S+) (?<parameterName>\\S+) (?<srgpid>\\d+)))");
        public Matcher matcher;
        public MappingEnum left;
        public MappingEnum right;

        @Override
        public void beginParsing(String lines) {
            this.matcher = TSRG2_PATTERN.matcher(lines);
            String[] header = lines.split("\n")[0].split(" ");
            this.left = MappingEnum.fromTSRG2(header[1]);
            this.right = MappingEnum.fromTSRG2(header[2]);
        }
        @Override public MappingEnum getLeft() { return this.left; }
        @Override public MappingEnum getRight() { return this.right; }
        @Override public boolean nextTokenExists() { return this.matcher.find(); }
        @Override public boolean isClassToken() {
            return this.matcher.group("className") != null;
        }
        @Override public boolean isFieldToken() {
            return this.matcher.group("fieldName") != null;
        }
        @Override public boolean isMethodToken() {
            return this.matcher.group("methodName") != null;
        }
        @Override public boolean isParamToken() {
            return this.matcher.group("parameterName") != null;
        }
        @Override public String extractFrom() {
            return this.matcher.group("from");
        }
        @Override public MappingClass extractClass() {
            return new MappingClass(
                this.matcher.group("className"),
                new HashMap<>(),
                new LinkedList<>(),
                Integer.parseInt(matcher.group("srgcid"))
            );
        }
        @Override public MappingField extractField() {
            return new MappingField(
                null,
                this.matcher.group("fieldName"),
                Integer.parseInt(matcher.group("srgfid"))
            );
        }
        @Override public MappingMethod extractMethod() {
            return new MappingMethod(
                null,
                matcher.group("methodType"),
                this.matcher.group("methodName"),
                new LinkedList<>(),
                Integer.parseInt(matcher.group("srgmid")),
                matcher.group("isStatic") != null
            );
        }
        @Override public MappingParameter extractParam() {
            return new MappingParameter(
                this.matcher.group("parameterName"),
                Integer.parseInt(matcher.group("srgpid"))
            );
        }
        @Override
        public void finishParsing() {
            this.matcher = null;
            this.left = null;
            this.right = null;
        }
    };

    public static MappingParser PROGUARD_PARSER = new MappingParser() {
        private static final Pattern PROGUARD_PATTERN = Pattern.compile("(?m)^(\\h*((?<lineAndColumn>\\d+:\\d+):)?(?<type>\\S+) )?(?<to>[^\\(\\s]+)(?<arguments>\\([^\\)]*\\))? -> (?<from>[^:\\s]+):?");
        public Matcher matcher;
        public MappingEnum left;
        public MappingEnum right;

        @Override
        public void beginParsing(String lines) {
            this.matcher = PROGUARD_PATTERN.matcher(lines);
            this.left = MappingEnum.OBF;
            this.right = MappingEnum.MOJMAP;
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
        @Override public String extractFrom() { return this.matcher.group("from"); }
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
                parseTheseArguments(this.matcher.group("arguments"))
                    + parseThisWeirdType(this.matcher.group("type")),
                this.matcher.group("to"),
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

        private String parseTheseArguments(String types) {
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
        private String parseThisWeirdType(String type) {
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
