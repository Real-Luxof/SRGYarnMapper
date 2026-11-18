package com.luxof.srgyarnmapper;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

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
}
