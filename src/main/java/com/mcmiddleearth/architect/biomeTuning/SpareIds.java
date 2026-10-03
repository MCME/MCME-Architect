package com.mcmiddleearth.architect.biomeTuning;

import org.bukkit.NamespacedKey;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ids spare biomes have: a namespace, a prefix and a fixed number of digits, as in mcme:custom_001. A spare is a
 * biome a datapack defines at startup, neutral until a builder claims it and gives it a look. The config's
 * biomeTuning.sparePattern writes them as mcme:custom_###, one # per digit, and must have exactly that form, because
 * /biometune spares add numbers new spares. Pure.
 */
public record SpareIds(String namespace, String prefix, int digits) {

    /** The shape of a pattern; the constructor checks its parts. */
    private static final Pattern FORM = Pattern.compile("([^:#]+):([^:#]*)(#{1,9})");
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PREFIX = Pattern.compile("[a-z0-9_./-]*");
    /** After the patterns, which its constructor uses while the class starts. */
    public static final SpareIds DEFAULT = new SpareIds("mcme", "custom_", 3);

    /**
     * Every id stays a file inside the pack, as spares add writes it there: the parts are what a biome id allows, the
     * namespace is not dots alone, and no part of the prefix's path is empty, "." or "..". At most nine digits, so
     * every number fits an int.
     */
    public SpareIds {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(prefix, "prefix");
        if (digits < 1 || digits > 9) {
            throw new IllegalArgumentException("a spare id has 1 to 9 digits, not " + digits);
        }
        if (!NAMESPACE.matcher(namespace).matches() || namespace.chars().allMatch(c -> c == '.')) {
            throw new IllegalArgumentException("not a namespace for spare ids: '" + namespace + "'");
        }
        // the digits follow the prefix, so "0" stands in for them: a prefix may end in a folder, as in "biomes/"
        if (!PREFIX.matcher(prefix).matches() || Arrays.stream((prefix + "0").split("/", -1))
                .anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."))) {
            throw new IllegalArgumentException("not a prefix for spare ids: '" + prefix + "'");
        }
    }

    /** Reads a pattern such as {@code mcme:custom_###}; empty for any other form. */
    public static Optional<SpareIds> parse(String pattern) {
        if (pattern == null) {
            return Optional.empty();
        }
        Matcher form = FORM.matcher(pattern);
        if (!form.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new SpareIds(form.group(1), form.group(2), form.group(3).length()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The pattern as the config writes it. */
    public String pattern() {
        return namespace + ":" + prefix + "#".repeat(digits);
    }

    /** Whether {@code key} is one of these ids: this namespace and prefix, then the digits, from 1 up. */
    public boolean matches(NamespacedKey key) {
        String id = key.getKey();
        return key.getNamespace().equals(namespace) && id.length() == prefix.length() + digits && id.startsWith(prefix)
                && id.substring(prefix.length()).chars().allMatch(c -> c >= '0' && c <= '9') && number(key) > 0;
    }

    /** The number of a spare id; only for ids that match. */
    public int number(NamespacedKey key) {
        return Integer.parseInt(key.getKey().substring(prefix.length()));
    }

    /** The id of spare number {@code number}, from 1 to the largest the digits allow. */
    public NamespacedKey key(int number) {
        if (number < 1 || String.valueOf(number).length() > digits) {
            throw new IllegalArgumentException("spare numbers run from 1 to " + "9".repeat(digits) + ", not " + number);
        }
        // in some locales String.format writes other digits, which no biome id may hold
        return new NamespacedKey(namespace, prefix + String.format(Locale.ROOT, "%0" + digits + "d", number));
    }
}
