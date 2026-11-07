package com.mcmiddleearth.architect.slabSmooth.core;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A pack's block inventory as MCME-Architect reads it: every .yml file in the pack's folder
 * (plugins/MCME-Architect/inventories/block/&lt;pack&gt;/), entries under "Items".
 */
public final class Inventory {

    private Inventory() {
    }

    /**
     * An inventory entry.
     *
     * @param type   Architect's block type: VANILLA, BLOCK, BISECTED, FOUR_DIRECTIONS, ...
     * @param states for VANILLA the block id ("minecraft:stone_stairs"), otherwise every blockData* state, by key
     * @param parent the entry whose collection this one is shown in ("collection: indirect: ..."), or ""; such
     *               entries have no category of their own and take the parent's
     */
    public record Entry(String key, String file, String category, String type, String display,
                        Map<String, String> states, String parent) {

        public boolean vanilla() {
            return "VANILLA".equals(type);
        }

        /** VANILLA only: the block id. */
        public String block() {
            return states.get("block");
        }
    }

    /** Reads every .yml file in a folder, like Architect does (file names don't matter). */
    public static List<Entry> read(Path folder, Consumer<String> log) throws IOException {
        List<Path> files;
        try (Stream<Path> list = Files.list(folder)) {
            files = list.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml")
                    && !p.getFileName().toString().equalsIgnoreCase("categories.yml")).sorted().toList();
        }
        List<Entry> out = new ArrayList<>();
        for (Path file : files) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                out.addAll(parse(file.getFileName().toString(), new Yaml(new QuietConstructor()).load(r)));
            } catch (RuntimeException ex) {
                log.accept(file.getFileName() + ": can't read it (" + ex.getMessage().lines().findFirst().orElse("") + ")");
            }
        }
        return inheritCategories(out);
    }

    /** Collection grids sometimes repeat a cell; the last one wins, as in Architect, without a warning per file. */
    private static final class QuietConstructor extends SafeConstructor {
        QuietConstructor() {
            super(new LoaderOptions());
        }

        @Override
        protected void processDuplicateKeys(org.yaml.snakeyaml.nodes.MappingNode node) {
        }
    }

    /** Entries shown in another's collection take its category (as they appear under it in the inventory). */
    public static List<Entry> inheritCategories(List<Entry> entries) {
        Map<String, Entry> byKey = new LinkedHashMap<>();
        entries.forEach(e -> byKey.putIfAbsent(e.key(), e));
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            Entry p = e;
            for (int i = 0; i < 10 && p.category().isEmpty() && !p.parent().isEmpty() && byKey.containsKey(p.parent()); i++) {
                p = byKey.get(p.parent());
            }
            out.add(p == e ? e : new Entry(e.key(), e.file(), p.category(), e.type(), e.display(), e.states(), e.parent()));
        }
        return out;
    }

    /** Entries of one parsed file. */
    public static List<Entry> parse(String file, Object yaml) {
        List<Entry> out = new ArrayList<>();
        if (!(yaml instanceof Map<?, ?> root) || !(root.get("Items") instanceof Map<?, ?> items)) {
            return out;
        }
        for (Map.Entry<?, ?> e : items.entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?> item)) {
                continue;
            }
            String type = string(item.get("type"));
            Map<String, String> states = new LinkedHashMap<>();
            if ("VANILLA".equals(type)) {
                String material = string(item.get("itemMaterial"));
                if (material.isEmpty()) {
                    continue;
                }
                material = material.toLowerCase(Locale.ROOT);
                states.put("block", material.contains(":") ? material : "minecraft:" + material);
            } else {
                for (Map.Entry<?, ?> f : item.entrySet()) {
                    String k = String.valueOf(f.getKey());
                    if (k.startsWith("blockData") && f.getValue() instanceof String s && !s.isBlank()) {
                        try {
                            states.put(k, BlockStates.normalize(s));
                        } catch (RuntimeException ignored) {
                            // not a block state: Architect can't place it either
                        }
                    }
                }
            }
            String parent = item.get("collection") instanceof Map<?, ?> c ? string(c.get("indirect")) : "";
            out.add(new Entry(String.valueOf(e.getKey()), file, string(item.get("category")), type,
                    string(item.get("display")), states, parent));
        }
        return out;
    }

    private static String string(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
