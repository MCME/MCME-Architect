package com.mcmiddleearth.architect.slabSmooth;

import com.fastasyncworldedit.core.internal.exception.FaweException;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.command.tool.BrushTool;
import com.sk89q.worldedit.command.tool.NavigationWand;
import com.sk89q.worldedit.command.tool.SelectionWand;
import com.sk89q.worldedit.command.tool.Tool;
import com.sk89q.worldedit.command.tool.brush.Brush;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.item.ItemType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Opening an edit, on a LocalSession whose createEditSession throws, with the test's stand-in for FAWE's exception;
 * binding the brush, and asking a session which wand it has on an item, on sessions the test makes. The rest of
 * WorldEditSmoothing needs a real WorldEdit, and is checked on a real server.
 */
class WorldEditSmoothingTest {

    private static final ItemType FEATHER = new ItemType("minecraft:feather");

    /** A WorldEdit world that is only a name. */
    private static World world(String name) {
        return (World) Proxy.newProxyInstance(WorldEditSmoothingTest.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isValid" -> true;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> name;
                    default -> throw new AssertionError("unexpected call: " + method.getName());
                });
    }

    /** A session whose createEditSession throws failure, after noting the world it was asked to edit. */
    private static LocalSession refusing(RuntimeException failure, List<World> asked) {
        return new LocalSession() {
            @Override
            public EditSession createEditSession(Actor actor) {
                asked.add(getWorldOverride());
                throw failure;
            }
        };
    }

    @Test
    void aRefusalWhileOpeningIsARefusalAndTheOverrideIsPutBack() {
        World before = world("the player's own override"), target = world("the job's world");
        List<World> asked = new ArrayList<>();
        FaweException noRegion = new FaweException("No allowed region");
        LocalSession session = refusing(noRegion, asked);
        session.setWorldOverride(before);

        BlockTarget.Refused refused = assertThrows(BlockTarget.Refused.class, () -> WorldEditSmoothing.open(session, null, target));

        assertEquals("No allowed region", refused.getMessage());
        assertSame(noRegion, refused.getCause(), "FAWE's own exception stays the cause");
        assertEquals(List.of(target), asked, "the edit was asked for in the job's world");
        assertSame(before, session.getWorldOverride(), "the player's own override is back");
    }

    @Test
    void anythingElseWhileOpeningStaysAsItIs() {
        IllegalStateException bug = new IllegalStateException("not a refusal");
        LocalSession session = refusing(bug, new ArrayList<>());

        assertSame(bug, assertThrows(IllegalStateException.class, () -> WorldEditSmoothing.open(session, null, world("the job's world"))));
        assertNull(session.getWorldOverride(), "no override was set before, and none is left");
    }

    /** A logger that keeps every record, FINE ones included, and passes none on. */
    private static Logger keeping(List<LogRecord> records) {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.setLevel(Level.ALL);
        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return log;
    }

    /** A session whose getBrushTool gives tool. */
    private static LocalSession giving(BrushTool tool) {
        return new LocalSession() {
            @Override
            public BrushTool getBrushTool(ItemType item) {
                return tool;
            }
        };
    }

    // FAWE 2.16.1's getBrushTool(ItemType) asks about a player it doesn't pass on: in a session whose wands it hasn't
    // set up yet, NavigationWand.canUse(null) throws
    @Test
    void aSessionThatIsNotReadyForTheBrushGetsAPlainAnswer() {
        NullPointerException fawe = new NullPointerException("Cannot invoke \"Actor.hasPermission(String)\" because \"actor\" is null");
        List<ItemType> asked = new ArrayList<>();
        LocalSession session = new LocalSession() {
            @Override
            public BrushTool getBrushTool(ItemType item) {
                asked.add(item);
                throw fawe;
            }
        };
        List<LogRecord> logged = new ArrayList<>();

        Refusal refusal = assertThrows(Refusal.class,
                () -> WorldEditSmoothing.bind(session, FEATHER, 5, null, "architect.slabSmooth.brush", keeping(logged)));

        assertEquals("WorldEdit isn't ready for that item yet; try /sls brush again.", refusal.getMessage());
        assertEquals(List.of(FEATHER), asked);
        assertEquals(List.of(Level.FINE), logged.stream().map(LogRecord::getLevel).toList(), "one line, at FINE");
        assertNull(logged.get(0).getThrown(), "without a stack trace");
    }

    @Test
    void aFailureAfterGetBrushToolStaysAsItIs() {
        NullPointerException bug = new NullPointerException("test: not the session's");
        BrushTool tool = new BrushTool("worldedit.brush.sphere") {
            @Override
            public void setBrush(Brush brush, String permission) {
                throw bug;
            }
        };
        List<LogRecord> logged = new ArrayList<>();

        assertSame(bug, assertThrows(NullPointerException.class,
                () -> WorldEditSmoothing.bind(giving(tool), FEATHER, 5, null, "architect.slabSmooth.brush", keeping(logged))));
        assertEquals(List.of(), logged);
    }

    @Test
    void theBrushGoesOnTheItemsToolWithItsRadius() throws Exception {
        BrushTool tool = new BrushTool("worldedit.brush.sphere");
        Brush brush = (edit, position, pattern, size) -> { };

        WorldEditSmoothing.bind(giving(tool), FEATHER, 4, brush, "architect.slabSmooth.brush", keeping(new ArrayList<>()));

        assertSame(brush, tool.getBrush());
        assertEquals(4.0, tool.getSize());
    }

    @Test
    void theWandsAreNamedAsTheRefusalNamesThem() {
        assertEquals("WorldEdit wand", WorldEditSmoothing.wandOf(new SelectionWand()));
        assertEquals("WorldEdit navigation wand", WorldEditSmoothing.wandOf(new NavigationWand()));
        assertNull(WorldEditSmoothing.wandOf(new BrushTool("worldedit.brush.sphere")));
        assertNull(WorldEditSmoothing.wandOf(null));
    }

    // getTool(ItemType) is, on FAWE too, a plain look in the session's tools, with no player, so it can't fail where
    // getBrushTool does
    @Test
    void theSessionIsAskedWhatIsOnTheItemAndNothingIsBound() {
        List<ItemType> asked = new ArrayList<>();
        LocalSession session = new LocalSession() {
            @Override
            public Tool getTool(ItemType item) {
                asked.add(item);
                return new NavigationWand();
            }

            @Override
            public BrushTool getBrushTool(ItemType item) {
                throw new AssertionError("nothing is bound");
            }
        };

        assertEquals("WorldEdit navigation wand", WorldEditSmoothing.boundWand(session, FEATHER));
        assertEquals(List.of(FEATHER), asked);
    }

    @Test
    void aConfiguredItemIdIsReadInFull() {
        assertEquals("minecraft:wooden_axe", WorldEditSmoothing.itemId("minecraft:wooden_axe"));
        assertEquals("minecraft:wooden_axe", WorldEditSmoothing.itemId(" Wooden_Axe "));
        assertEquals("other:wand", WorldEditSmoothing.itemId("other:wand"));
        assertNull(WorldEditSmoothing.itemId(null));
        assertNull(WorldEditSmoothing.itemId("  "));
    }
}
