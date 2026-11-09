package com.mcmiddleearth.architect.slabSmooth.core;

import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

/** Block state strings: "minecraft:stone_stairs[facing=east,half=bottom,shape=straight]". */
public final class BlockStates {

    private BlockStates() {
    }

    /** A block id and its properties, sorted by name. */
    public record Parsed(String name, TreeMap<String, String> props) {
    }

    /** Splits a block state string into id and properties. Adds "minecraft:" if missing. */
    public static Parsed parse(String text) {
        text = text.strip();
        TreeMap<String, String> props = new TreeMap<>();
        String name = text;
        int bracket = text.indexOf('[');
        if (bracket >= 0) {
            name = text.substring(0, bracket);
            String rest = text.substring(bracket + 1);
            while (rest.endsWith("]")) {
                rest = rest.substring(0, rest.length() - 1);
            }
            for (String part : rest.split(",")) {
                if (!part.isBlank()) {
                    int eq = part.indexOf('=');
                    props.put(part.substring(0, eq).strip(), part.substring(eq + 1).strip());
                }
            }
        }
        if (!name.contains(":")) {
            name = "minecraft:" + name;
        }
        return new Parsed(name, props);
    }

    public static String format(String name, Map<String, String> props) {
        if (!name.contains(":")) {
            name = "minecraft:" + name;
        }
        if (props.isEmpty()) {
            return name;
        }
        StringJoiner joiner = new StringJoiner(",", name + "[", "]");
        new TreeMap<>(props).forEach((k, v) -> joiner.add(k + "=" + v));
        return joiner.toString();
    }

    public static String format(Parsed parsed) {
        return format(parsed.name(), parsed.props());
    }

    public static String normalize(String text) {
        return format(parse(text));
    }
}
