package com.luxof.srgyarnmapper.mappings;

public enum MappingEnum {
    OBF,
    SRG,
    INTERMEDIARY,
    YARN,
    MOJMAP;

    public String toTSRG2() {
        return switch (this) {
            case OBF -> "obf";
            case SRG -> "srg";
            case INTERMEDIARY -> "intermediary";
            case YARN -> "named";
            case MOJMAP -> "mojmap";
        };
    }
    public static MappingEnum fromTSRG2(String headerNamespace) {
        return switch (headerNamespace) {
            case "obf", "obfuscated", "official" -> OBF;
            case "srg", "searge" -> SRG;
            case "intermediary" -> INTERMEDIARY;
            case "named", "mojmap", "mcp" -> MOJMAP;
            default -> null;
        };
    }

    public String toTINYV2() {
        return switch (this) {
            case OBF -> "official";
            case SRG -> "srg";
            case INTERMEDIARY -> "intermediary";
            case YARN, MOJMAP -> "named";
        };
    }
    public static MappingEnum fromTINYV2(String headerNamespace) {
        return switch (headerNamespace) {
            case "official" -> OBF;
            case "srg", "searge" -> SRG;
            case "intermediary" -> INTERMEDIARY;
            case "named" -> YARN;
            case "mojmap" -> MOJMAP;
            default -> null;
        };
    }
}
