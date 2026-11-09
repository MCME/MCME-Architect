package com.mcmiddleearth.architect.slabSmooth;

import com.fastasyncworldedit.core.internal.exception.FaweException;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.world.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Opening an edit, on a LocalSession whose createEditSession throws, with the test's stand-in for FAWE's exception.
 * The rest of WorldEditSmoothing needs a real WorldEdit, and is checked on a real server.
 */
class WorldEditSmoothingTest {

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

    @Test
    void aConfiguredItemIdIsReadInFull() {
        assertEquals("minecraft:wooden_axe", WorldEditSmoothing.itemId("minecraft:wooden_axe"));
        assertEquals("minecraft:wooden_axe", WorldEditSmoothing.itemId(" Wooden_Axe "));
        assertEquals("other:wand", WorldEditSmoothing.itemId("other:wand"));
        assertNull(WorldEditSmoothing.itemId(null));
        assertNull(WorldEditSmoothing.itemId("  "));
    }
}
