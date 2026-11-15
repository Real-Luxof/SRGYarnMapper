package com.luxof.srgyarnmapper;

public class App {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Please provide only a Minecraft version to generate SRG -> Yarn mappings for.");
            return;
        }

        String version = args[0];
    }
}
