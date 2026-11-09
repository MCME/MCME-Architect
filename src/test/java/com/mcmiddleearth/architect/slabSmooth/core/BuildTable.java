package com.mcmiddleearth.architect.slabSmooth.core;

import java.nio.file.Path;
import java.util.List;

/** Builds a table from a pack folder and an inventory folder and prints the report: BuildTable PACK_DIR INVENTORY_DIR. */
public class BuildTable {
    public static void main(String[] a) throws Exception {
        Models m = new Models(Resources.layered(List.of(Resources.dir(Path.of(a[0])), Resources.zip(TableBuilderTest.JAR))));
        TableBuilder.Result r = TableBuilder.build(a[0], Inventory.read(Path.of(a[1]), System.out::println), m, new TableBuilder.Settings());
        r.report().forEach(System.out::println);
    }
}
