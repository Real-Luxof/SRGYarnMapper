package com.luxof.srgyarnmapper;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import com.luxof.srgyarnmapper.mappings.MappingClass;
import com.luxof.srgyarnmapper.mappings.MappingField;
import com.luxof.srgyarnmapper.mappings.MappingMethod;
import com.luxof.srgyarnmapper.mappings.MappingParameter;
import com.luxof.srgyarnmapper.mappings.Mappings;

public class App {
    public static final String SRG_ZIP_DOWNLOAD = "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/%s/mcp_config-%s.zip";
    public static final String INTERMEDIARY_ZIP_DOWNLOAD = "https://maven.fabricmc.net/net/fabricmc/intermediary/%s/%s-v2.jar";
    public static final String YARN_ZIP_DOWNLOAD = "https://maven.fabricmc.net/net/fabricmc/yarn/%s/%s-v2.jar";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: srgyarnmapper [minecraft_version]");
            System.out.println("Example usage: srgyarnmapper 1.20.1");
            System.out.println("Provided the right arguments, this application will generate SRG -> Yarn (and Mojmap -> Yarn) mappings.");
            return;
        }

        String version = args[0];

        Mappings srg = Mappings.ofTSRGV2(readNestedFileInZipFromURI(
            URI.create(String.format(SRG_ZIP_DOWNLOAD, version, version)),
            Path.of("srg.zip"),
            "config/joined.tsrg"
        ));

        Mappings intermediary = Mappings.ofTinyV2(readNestedFileInZipFromURI(
            URI.create(String.format(INTERMEDIARY_ZIP_DOWNLOAD, version, version)),
            Path.of("intermediary.zip"),
            "mappings/mappings.tiny"
        ));

        String yarnVersionAndBuild = findLatestYarnBuildFor(version);
        Mappings intermediary_to_yarn = Mappings.ofTinyV2(readNestedFileInZipFromURI(
            URI.create(String.format(
                YARN_ZIP_DOWNLOAD,
                yarnVersionAndBuild,
                yarnVersionAndBuild
            )),
            Path.of("intermediarytoyarn.zip"),
            "mappings/mappings.tiny"
        ));

        Mappings srg_to_yarn = swapOutInputOfAForOutputOfBUsingCommonLayerC(
            intermediary_to_yarn,
            intermediary,
            srg
        );
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
    public static final Pattern YARN_VERSIONS_REGEX = Pattern.compile("\\d([^\\+/]+\\+build.\\d/)");
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
                int build = Integer.parseInt(versionAndBuild.substring(version.length() + 6));
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
     */
    public static Mappings swapOutInputOfAForOutputOfBUsingCommonLayerC(
        Mappings A,
        Mappings C,
        Mappings B
    ) {
        Map<String, MappingClass> output = new HashMap<>();

        for (var classEntry : A.mappings.entrySet()) {
            String classKey = classEntry.getKey();
            String commonClassKey = C.reverseClass(classKey);
            String newClassKey = B.translateClass(commonClassKey);

            MappingClass clazz = classEntry.getValue();
            Map<String, MappingMethod> methods = new HashMap<>(clazz.methods());
            Map<String, MappingField> fields = new HashMap<>(clazz.fields());


            for (var methodEntry : methods.entrySet()) {
                String methodKey = methodEntry.getKey();
                String commonMethodKey = C.reverseMethod(commonClassKey, methodKey);
                String newMethodKey = B.translateMethod(commonClassKey, commonMethodKey);

                Map<String, MappingParameter> params = new HashMap<>(
                    methodEntry.getValue().parameters()
                );

                for (var paramEntry : params.entrySet()) {
                    String paramKey = paramEntry.getKey();
                    String commonParamKey = C.reverseParameter(commonClassKey, commonMethodKey, paramKey);
                    String newParamKey = B.translateParameter(commonClassKey, commonMethodKey, commonParamKey);

                    MappingParameter paramFromA = paramEntry.getValue();
                    MappingParameter paramFromC = C.getParameter(commonClassKey, commonMethodKey, commonParamKey);
                    MappingParameter paramFromB = B.getParameter(newClassKey, newMethodKey, newParamKey);

                    // scavenge extra data
                    params.remove(paramKey);
                    params.put(
                        newParamKey,
                        new MappingParameter(
                            paramFromA.name(),
                            Mappings.yoinkSRGID(paramFromA, paramFromC, paramFromB)
                        )
                    );
                }


                // scavenge any extra data we can get (like SRG id, type, etcetera)
                MappingMethod methodFromA = methodEntry.getValue();
                MappingMethod methodFromC = C.getMethod(commonClassKey, commonMethodKey);
                MappingMethod methodFromB = B.getMethod(newClassKey, newMethodKey);

                methods.remove(methodKey);
                methods.put(
                    newMethodKey,
                    new MappingMethod(
                        Mappings.yoinkType(methodFromA, methodFromC, methodFromB),
                        methodFromA.name(),
                        Map.copyOf(params),
                        Mappings.yoinkSRGID(methodFromA, methodFromC, methodFromB),
                        Mappings.yoinkIsStatic(methodFromA, methodFromC, methodFromB)
                    )
                );
            }

            for (var fieldEntry : fields.entrySet()) {
                String fieldKey = fieldEntry.getKey();

                String commonFieldKey = C.reverseField(commonClassKey, fieldKey);
                String newFieldKey = B.translateField(newClassKey, commonFieldKey);

                MappingField fieldFromA = fieldEntry.getValue();
                MappingField fieldFromC = C.getField(commonClassKey, commonFieldKey);
                MappingField fieldFromB = B.getField(newClassKey, newFieldKey);

                // mmmm data...
                fields.remove(fieldKey);
                fields.put(
                    newFieldKey,
                    new MappingField(
                        Mappings.yoinkType(fieldFromA, fieldFromC, fieldFromB),
                        fieldFromA.name(),
                        Mappings.yoinkSRGID(fieldFromA, fieldFromC, fieldFromB)
                    )
                );
            }
        }

        return new Mappings(Map.copyOf(output));
    }
}
