package com.mcmiddleearth.architect.mapLayers.customBiomes;

import com.mcmiddleearth.architect.mapLayers.MapShape;

import java.util.List;
import java.util.zip.Deflater;

/**
 * How much a layer's area markers add to a world's marker file as the map serves it. Every visit to the web map
 * downloads the marker file of the world it shows, hidden layers included, and the map's server sends it gzipped at
 * level 1. So the markers are measured that way: written as JSON in the shape of dynmap's file, then gzipped at level
 * 1. A plain computation for a background thread: a layer the size of mainworld's takes some milliseconds.
 */
final class MarkerSize {

    /** The most one world's markers may add to every map visit, in gzipped bytes: a world over it is not drawn. */
    static final int TARGET = 512_000;

    /** A gzip header without a file name, and the trailer, which holds a checksum and the length. */
    private static final int GZIP_HEADER = 10;
    private static final int GZIP_TRAILER = 8;
    /** Every area is drawn flat at this height, as the dynmap backend sets it. */
    private static final double FLAT_Y = 64;
    /** The text is compressed a piece of this many characters at a time. */
    private static final int PIECE = 64 * 1024;
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final char[] UPPER_HEX = "0123456789ABCDEF".toCharArray();

    private MarkerSize() {
    }

    /** The size of the areas' {@link #json JSON} gzipped at level 1, header and trailer included. */
    static int gzipLevel1(List<MapShape.Area> areas) {
        StringBuilder json = write(areas);
        Deflater deflater = new Deflater(Deflater.BEST_SPEED, true);
        try {
            char[] chars = new char[PIECE];
            byte[] in = new byte[PIECE];
            byte[] out = new byte[PIECE];
            // a piece at a time, as a stream is compressed; every character is ASCII, so it is one byte
            for (int start = 0; start < json.length(); start += PIECE) {
                int length = Math.min(PIECE, json.length() - start);
                json.getChars(start, start + length, chars, 0);
                for (int i = 0; i < length; i++) {
                    in[i] = (byte) chars[i];
                }
                deflater.setInput(in, 0, length);
                while (!deflater.needsInput()) {
                    deflater.deflate(out);
                }
            }
            deflater.finish();
            while (!deflater.finished()) {
                deflater.deflate(out);
            }
            return Math.toIntExact(GZIP_HEADER + deflater.getBytesWritten() + GZIP_TRAILER);
        } finally {
            deflater.end();
        }
    }

    /**
     * The areas as JSON in the shape of dynmap's marker file, with its keys in its order:
     * {@code {"areas":{"<id>":{"fillcolor":"#rrggbb","ytop":64.0,"color":"#rrggbb","markup":false,"x":[...],
     * "weight":2,"z":[...],"ybottom":64.0,"label":"...","opacity":0.6,"fillopacity":0.3,"desc":"..."}}}}. Strings are
     * escaped as dynmap escapes them: a slash as well, and each character outside printable ASCII as a backslash, a
     * {@code u} and four hex digits. So the text is all ASCII.
     */
    static String json(List<MapShape.Area> areas) {
        return write(areas).toString();
    }

    private static StringBuilder write(List<MapShape.Area> areas) {
        StringBuilder json = new StringBuilder("{\"areas\":{");
        boolean first = true;
        for (MapShape.Area area : areas) {
            if (!first) {
                json.append(',');
            }
            first = false;
            MapShape.Style style = area.style();
            string(json, area.id());
            json.append(":{\"fillcolor\":");
            colour(json, style.fillColor());
            json.append(",\"ytop\":");
            number(json, FLAT_Y);
            json.append(",\"color\":");
            colour(json, style.lineColor());
            json.append(",\"markup\":false,\"x\":");
            numbers(json, area.x());
            json.append(",\"weight\":").append(style.lineWidth()).append(",\"z\":");
            numbers(json, area.z());
            json.append(",\"ybottom\":");
            number(json, FLAT_Y);
            json.append(",\"label\":");
            string(json, area.label());
            json.append(",\"opacity\":");
            number(json, style.lineOpacity());
            json.append(",\"fillopacity\":");
            number(json, style.fillOpacity());
            json.append(",\"desc\":");
            string(json, area.description());
            json.append('}');
        }
        return json.append("}}");
    }

    /** A colour as {@code "#rrggbb"}. */
    private static void colour(StringBuilder json, int rgb) {
        json.append("\"#");
        for (int shift = 20; shift >= 0; shift -= 4) {
            json.append(HEX[(rgb >> shift) & 0xf]);
        }
        json.append('"');
    }

    private static void numbers(StringBuilder json, double[] values) {
        json.append('[');
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            number(json, values[i]);
        }
        json.append(']');
    }

    /** A number as Java writes a double, such as 0.6; a whole one, such as 64.0, never with an exponent. */
    private static void number(StringBuilder json, double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            json.append((long) value).append(".0");
        } else {
            json.append(value);
        }
    }

    /** A string in quotes, escaped as dynmap's marker file escapes it. */
    private static void string(StringBuilder json, String text) {
        json.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '/' -> json.append("\\/");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < ' ' || c > '~') {
                        json.append('\\').append('u');
                        for (int shift = 12; shift >= 0; shift -= 4) {
                            json.append(UPPER_HEX[(c >> shift) & 0xf]);
                        }
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        json.append('"');
    }
}
