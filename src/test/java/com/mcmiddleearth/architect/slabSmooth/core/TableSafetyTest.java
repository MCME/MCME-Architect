package com.mcmiddleearth.architect.slabSmooth.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** States the smoother must leave alone, whatever the table says about their block. */
class TableSafetyTest {

    private Table table;

    @BeforeEach
    void loadTable() throws Exception {
        table = TestTables.handMade();
    }

    /** Smoothing would rewrite the stair dry, so the water would be gone. */
    @Test
    void waterloggedStairsAreLeftAlone() {
        String s = "minecraft:stone_stairs[facing=north,half=bottom,shape=straight,waterlogged=true]";
        assertEquals(Table.OTHER, table.kind(s).category());
        assertEquals(Table.OTHER, table.kind3(s).category());
    }

    /** The table's "minecraft:brain_coral" names no properties, so it matched the coral under water too. */
    @Test
    void waterloggedCoralIsLeftAlone() {
        assertEquals(Table.OTHER, table.kind("minecraft:brain_coral[waterlogged=true]").category());
        assertEquals(Table.DECOR, table.kind("minecraft:brain_coral[waterlogged=false]").category());
    }
}
