package com.luxof.srgyarnmapper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;

import com.luxof.srgyarnmapper.mappings.MappingClass;
import com.luxof.srgyarnmapper.mappings.MappingField;
import com.luxof.srgyarnmapper.mappings.MappingMethod;
import com.luxof.srgyarnmapper.mappings.MappingParameter;
import com.luxof.srgyarnmapper.mappings.Mappings;
import com.luxof.srgyarnmapper.mappings.serde.MappingParser;
import com.luxof.srgyarnmapper.mappings.serde.MappingSerializer;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

public class App {
    public static final String SRG_ZIP_DOWNLOAD = "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/%s/mcp_config-%s.zip";
    public static final String INTERMEDIARY_ZIP_DOWNLOAD = "https://maven.fabricmc.net/net/fabricmc/intermediary/%s/intermediary-%s-v2.jar";
    public static final String YARN_ZIP_DOWNLOAD = "https://maven.fabricmc.net/net/fabricmc/yarn/%s/yarn-%s-v2.jar";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: srgyarnmapper [minecraft_version]");
            System.out.println("Example usage: srgyarnmapper 1.20.1");
            System.out.println("Provided the right arguments, this application will generate SRG -> Yarn (and Mojmap -> Yarn) mappings.");
            return;
        }

        String version = args[0];

        Mappings srg = Mappings.of(
            readNestedFileInZipFromURI(
                URI.create(String.format(SRG_ZIP_DOWNLOAD, version, version)),
                Path.of("srg.zip"),
                "config/joined.tsrg"
            ),
            MappingParser.TSRG2_PARSER
        );

        Mappings intermediary = Mappings.of(
            readNestedFileInZipFromURI(
                URI.create(String.format(INTERMEDIARY_ZIP_DOWNLOAD, version, version)),
                Path.of("intermediary.zip"),
                "mappings/mappings.tiny"
            ),
            MappingParser.TINYV2_PARSER
        );

        String yarnVersionAndBuild = findLatestYarnBuildFor(version);
        Mappings intermediary_to_yarn = Mappings.of(
            readNestedFileInZipFromURI(
                URI.create(String.format(
                    YARN_ZIP_DOWNLOAD,
                    yarnVersionAndBuild,
                    yarnVersionAndBuild
                )),
                Path.of("intermediarytoyarn.zip"),
                "mappings/mappings.tiny"
            ),
            MappingParser.TINYV2_PARSER
        );

        Mappings mojmap = Mappings.of(yoinkClientTxt(version), MappingParser.PROGUARD_PARSER);

        Mappings srg_to_yarn = swapOutInputOfAForOutputOfBUsingCommonLayerC(
            intermediary_to_yarn,
            intermediary,
            srg
        );
        Mappings mojmap_to_yarn = swapOutInputOfAForOutputOfBUsingCommonLayerC(
            intermediary_to_yarn,
            intermediary,
            mojmap
        );

        try {
            /*Files.writeString(
                Path.of("joined.tsrg2"),
                srg.to(MappingSerializer.TSRG2_SERIALIZER)
            );
            Files.writeString(
                Path.of("intermediary.tiny"),
                intermediary.to(MappingSerializer.TINYV2_SERIALIZER)
            );
            Files.writeString(
                Path.of("yarn.tiny"),
                intermediary_to_yarn.to(MappingSerializer.TINYV2_SERIALIZER)
            );
            Files.writeString(
                Path.of("client.txt"),
                mojmap.to(MappingSerializer.PROGUARD_SERIALIZER)
            );*/

            Files.writeString(
                Path.of("srgtoyarn.tsrg2"),
                srg_to_yarn.to(MappingSerializer.TSRG2_SERIALIZER)
            );
            Files.writeString(
                Path.of("mojmaptoyarn.txt"),
                mojmap_to_yarn.to(MappingSerializer.PROGUARD_SERIALIZER)
            );
        } catch (IOException e) {
            // shouldn't happen
            throw new RuntimeException(e);
        }
    }

    public static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    public static String readNestedFileInZipFromURI(URI uri, Path saveZipTo, String nestedPath) {
        try {
            ZipFile zip = new ZipFile(downloadFromURI(uri, saveZipTo));
            String fileContents = new String(
                zip.getInputStream(zip.getEntry(nestedPath)).readAllBytes()
            );
            zip.close();
            return fileContents;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static File downloadFromURI(URI uri, Path path) {
        path.toFile().delete();
        try {
            HttpResponse<Path> response = HTTP_CLIENT.send(
                HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofFile(path)
            );

            if (response.statusCode() != 200)
                throw new RuntimeException(String.format(
                    "Response status code: %d -- are you sure that's a Minecraft version? If so, Intermediary or SRG mappings for it may not be available at the moment.",
                    response.statusCode()
                ));

            return response.body().toFile();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static final URI YARN_VERSIONS_LIST = URI.create("https://maven.fabricmc.net/net/fabricmc/yarn/");
    public static final Pattern YARN_VERSIONS_REGEX = Pattern.compile("\\d([^\\+/]+\\+build.(?<build>\\d+)/)");
    public static String findLatestYarnBuildFor(String version) {
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(
                HttpRequest.newBuilder(YARN_VERSIONS_LIST).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200)
                throw new RuntimeException(String.format(
                    "Response status code: %d -- Fabric maven (Yarn servers) unavailable.",
                    response.statusCode()
                ));

            Matcher matcher = YARN_VERSIONS_REGEX.matcher(response.body());

            Integer biggestBuild = null;
            while (matcher.find()) {
                String versionAndBuild = matcher.group();
                if (!versionAndBuild.startsWith(version)) continue;
                int build = Integer.parseInt(matcher.group("build"));
                biggestBuild = biggestBuild == null
                    ? build
                    : biggestBuild < build ? build : biggestBuild;
            }

            if (biggestBuild == null)
                throw new RuntimeException("Couldn't find a Yarn build for your input version!");

            return String.format("%s+build.%d", version, biggestBuild);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** usage example:
     * <br><code>swapOutInputOfAForOutputOfBUsingCommonLayerC(
     * <br>____intermediary_to_yarn,
     * <br>____intermediary,
     * <br>____srg
     * <br>);</code>
     * 
     * <p>i feel like i won't remember how this works after 2 weeks
     * <p>basically,
     * <br><code>A =      Y -> Z
     * <br><code>B = X -> Y
     * <br><code>C = X -> W
     * <br><code>O =      W -> Z (what this method returns)
     */
    public static Mappings swapOutInputOfAForOutputOfBUsingCommonLayerC(
        Mappings A,//     Y -> Z
        Mappings C,//X -> Y
        Mappings B //X -> W
    ) {
        //                W -> Z
        Map<String, MappingClass> O = new HashMap<>();

        for (var clazzEntryA : A.mappings().entrySet()) {
            String clazzNameY = clazzEntryA.getKey();
            String clazzNameX = C.reverseClass(clazzNameY);
            String clazzNameW = B.translateClass(clazzNameX);
            MappingClass clazzA = clazzEntryA.getValue();
            MappingClass clazzC = C.getClass(clazzNameX);
            MappingClass clazzB = B.getClass(clazzNameX);

            HashMap<String, MappingField> fieldsO = new HashMap<>();
            LinkedList<Map.Entry<String, MappingMethod>> methodsO = new LinkedList<>();

            for (var fieldEntryA : clazzA.fields().entrySet()) {
                String fieldNameY = fieldEntryA.getKey();
                String fieldNameX = C.reverseField(clazzNameX, fieldNameY);
                String fieldNameW;
                MappingField fieldA = fieldEntryA.getValue();
                MappingField fieldC = C.getField(clazzNameX, fieldNameX);
                MappingField fieldB;
                try {
                    fieldNameW = B.translateField(clazzNameX, fieldNameX);
                    fieldB = B.getField(clazzNameX, fieldNameX);;
                } catch (NullPointerException npe) {
                    fieldNameW = fieldNameX;
                    fieldB = new MappingField(null, "", null);
                }

                fieldsO.put(
                    fieldNameW,
                    new MappingField(
                        Mappings.yoinkType(fieldA, fieldC, fieldB),
                        fieldA.name(),
                        Mappings.yoinkSRGID(fieldA, fieldC, fieldB)
                    )
                );
            }

            for (var methodEntryA : clazzA.methods()) {
                String methodNameY = methodEntryA.getKey();
                String methodNameX = C.reverseMethod(clazzNameX, methodNameY);
                String methodNameW;
                MappingMethod methodA = methodEntryA.getValue();
                MappingMethod methodC = C.getMethod(clazzNameX, methodNameX);
                MappingMethod methodB;
                try {
                    methodNameW = B.translateMethod(clazzNameX, methodNameX);
                    methodB = B.getMethod(clazzNameX, methodNameX);
                } catch (NullPointerException npe) {
                    methodNameW = methodNameX;
                    methodB = new MappingMethod(null, null, "", new LinkedList<>(), null, null);
                }

                LinkedList<Map.Entry<String, MappingParameter>> paramsO = new LinkedList<>();
                var paramsA = methodA.parameters();

                for (int paramIdx = 0; paramIdx < paramsA.size(); paramIdx++) {
                    MappingParameter paramA = A.getParameter(clazzNameY, methodNameY, paramIdx);
                    MappingParameter paramC = C.getParameter(clazzNameX, methodNameX, paramIdx);
                    MappingParameter paramB = B.getParameter(clazzNameX, methodNameX, paramIdx);

                    paramsO.add(Map.entry(
                        paramB.name(),
                        new MappingParameter(
                            paramA.name(),
                            Mappings.yoinkSRGID(paramA, paramC, paramB)
                        )
                    ));
                }

                methodsO.add(Map.entry(
                    methodNameW,
                    new MappingMethod(
                        Mappings.yoinkLineAndColumn(methodA, methodC, methodB),
                        Mappings.yoinkType(methodA, methodC, methodB),
                        methodA.name(),
                        List.copyOf(paramsO),
                        Mappings.yoinkSRGID(methodA, methodC, methodB),
                        Mappings.yoinkIsStatic(methodA, methodC, methodB)
                    )
                ));
            }

            O.put(
                clazzNameW,
                new MappingClass(
                    clazzA.name(),
                    Map.copyOf(fieldsO),
                    List.copyOf(methodsO),
                    Mappings.yoinkSRGID(clazzA, clazzC, clazzB)
                )
            );
        }

        return new Mappings(B.from(), A.to(), Map.copyOf(O));
    }

    public static final URI VERSION_MANIFEST_V2_URI = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    public static String yoinkClientTxt(String version) {
        try {
            URI VERSION_JSON_URI = null;
            for (
                JsonElement ele :
                    parseStringToObj(
                        HTTP_CLIENT.send(
                            HttpRequest.newBuilder(VERSION_MANIFEST_V2_URI).GET().build(),
                            HttpResponse.BodyHandlers.ofString()
                        ).body()
                    ).getAsJsonObject().getAsJsonArray("versions")
            ) {
                JsonObject obj = ele.getAsJsonObject();
                if (!obj.get("id").getAsString().equals(version)) continue;

                VERSION_JSON_URI = URI.create(obj.get("url").getAsString());
                break;
            }

            return HTTP_CLIENT.send(
                HttpRequest.newBuilder(
                    URI.create(parseStringToObj(
                        HTTP_CLIENT.send(
                            HttpRequest.newBuilder(VERSION_JSON_URI).GET().build(),
                            HttpResponse.BodyHandlers.ofString()
                        ).body()
                    ).getAsJsonObject()
                        .getAsJsonObject("downloads")
                        .getAsJsonObject("client_mappings")
                        .get("url")
                        .getAsString())
                ).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            ).body();
        } catch (Exception e) {
            /* surely no exceptions could ever be here... */
            throw new RuntimeException(e);
        }
    }

    public static JsonElement parseStringToObj(String str) {
        JsonReader jReader = new JsonReader(StringReader.of(str));
        jReader.setStrictness(Strictness.LENIENT);
        return JsonParser.parseReader(jReader);
    }
}
