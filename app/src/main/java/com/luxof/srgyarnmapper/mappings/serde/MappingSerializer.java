package com.luxof.srgyarnmapper.mappings.serde;

import com.luxof.srgyarnmapper.mappings.MappingClass;
import com.luxof.srgyarnmapper.mappings.MappingEnum;
import com.luxof.srgyarnmapper.mappings.MappingField;
import com.luxof.srgyarnmapper.mappings.MappingMethod;
import com.luxof.srgyarnmapper.mappings.MappingParameter;

import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/** Any of the methods here returning <code>null</code> mean "don't put a line here". */
public interface MappingSerializer {
    public String getHeading(MappingEnum from, MappingEnum to);
    @Nullable public String serialize(String clazzKey, MappingClass clazz);
    @Nullable public String serialize(String fieldKey, MappingField field);
    @Nullable public String serialize(String methodKey, MappingMethod method);
    @Nullable public String serialize(int no, String paramKey, MappingParameter param);

    public static MappingSerializer TINYV2_SERIALIZER = new MappingSerializer() {
        @Override public String getHeading(MappingEnum from, MappingEnum to) {
            return String.format("tiny\t2\t0\t%s\t%s", from.toTINYV2(), to.toTINYV2());
        }
        @Override public @Nullable String serialize(String clazzKey, MappingClass clazz) {
            return String.format("c\t%s\t%s", clazzKey, clazz.name());
        }
        @Override public @Nullable String serialize(String fieldKey, MappingField field) {
            return String.format("\tf\t%s\t%s\t%s", field.type(), fieldKey, field.name());
        }
        @Override public @Nullable String serialize(String methodKey, MappingMethod method) {
            return String.format("\tm\t%s\t%s\t%s", method.type(), methodKey, method.name());
        }
        @Override public @Nullable String serialize(int no, String paramKey, MappingParameter param) {
            return String.format("\t\tp\t%d\t%s\t%s", no, paramKey, param.name());
        }
    };

    public static MappingSerializer TSRG2_SERIALIZER = new MappingSerializer() {
        @Override public String getHeading(MappingEnum from, MappingEnum to) {
            return String.format(
                "tsrg2 %s %s id",
                from.toTSRG2(),
                to.toTSRG2()
            );
        }
        @Override public @Nullable String serialize(String clazzKey, MappingClass clazz) {
            return String.format("%s %s %d", clazzKey, clazz.name(), clazz.srgID());
        }
        @Override public @Nullable String serialize(String fieldKey, MappingField field) {
            return String.format("\t%s %s %d", fieldKey, field.name(), field.srgID());
        }
        @Override public @Nullable String serialize(String methodKey, MappingMethod method) {
            return String.format(
                "\t%s %s %s %d%s",
                methodKey,
                method.type(),
                method.name(),
                method.srgID(),
                method.isStatic() ? "\n\t\tstatic" : ""
            );
        }
        @Override public @Nullable String serialize(int no, String paramKey, MappingParameter param) {
            return String.format(
                "\t\t%d %s %s %d",
                no,
                paramKey,
                param.name(),
                param.srgID()
            );
        }
    };

    public static MappingSerializer PROGUARD_SERIALIZER = new MappingSerializer() {
        @Override public String getHeading(MappingEnum from, MappingEnum to) {
            return from == MappingEnum.OBF && to == MappingEnum.MOJMAP
                ? "# (c) 2020 Microsoft Corporation. These mappings are provided \"as-is\" and you bear the risk of using them. You may copy and use the mappings for development purposes, but you may not redistribute the mappings complete and unmodified. Microsoft makes no warranties, express or implied, with respect to the mappings provided here.  Use and modification of this document or the source code (in any form) of Minecraft: Java Edition is governed by the Minecraft End User License Agreement available at https://account.mojang.com/documents/minecraft_eula."
                : "# heblo. lux here. use my mappings. sonic exe here. play my game";
        }
        @Override public @Nullable String serialize(String clazzKey, MappingClass clazz) {
            return String.format("%s -> %s:", decompileToWeirdType(clazz.name()), clazzKey);
        }
        @Override public @Nullable String serialize(String fieldKey, MappingField field) {
            return String.format(
                "    %s %s -> %s",
                decompileToWeirdType(field.type()),
                field.name(),
                fieldKey
            );
        }
        @Override public @Nullable String serialize(String methodKey, MappingMethod method) {
            String[] argsAndReturnType = separateArgsAndReturnType(method.type());
            return String.format(
                "    %s:%s %s%s -> %s",
                method.lineAndColumn(),
                argsAndReturnType[1],
                method.name(),
                argsAndReturnType[0],
                methodKey
            );
        }
        @Override public @Nullable String serialize(int no, String paramKey, MappingParameter param) {
            return null;
        }

        public String[] separateArgsAndReturnType(String argsAndReturnType) {
            String[] separated = argsAndReturnType.substring(1).split(Pattern.quote(")"));
            return new String[] {
                "(" + decompileToWeirdArgs(separated[0]) + ")",
                decompileToWeirdType(separated[1])
            };
        }
        public final Pattern ARGS_SEPERATOR_REGEX = Pattern.compile("\\[*([BCDFIJSZ]|L[^;]+;)");
        public String decompileToWeirdArgs(String args) {
            return String.join(
                ",",
                ARGS_SEPERATOR_REGEX.matcher(args).results()
                    .map(res -> decompileToWeirdType(res.group()))
                    .toList()
            );
        }
        public String decompileToWeirdType(String normal) {
            String suffix = "";
            String trueNormal = normal;
            while (trueNormal.startsWith("[")) {
                suffix += "[]";
                trueNormal = trueNormal.substring(1);
            }

            return switch (trueNormal) {
                case "B" -> "byte";
                case "C" -> "char";
                case "D" -> "double";
                case "F" -> "float";
                case "I" -> "int";
                case "J" -> "long";
                case "S" -> "short";
                case "Z" -> "boolean";
                case "V" -> "void";
                default -> trueNormal.substring(1, trueNormal.length() - 1)
                        .replaceAll("/", ".");
            } + suffix;
        }
    };
}
