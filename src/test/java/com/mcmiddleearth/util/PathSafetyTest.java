package com.mcmiddleearth.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

class PathSafetyTest {

    @Test void resolvesLegitNameWithExtension(@TempDir File base) {
        File out = PathSafety.resolveInside(base, "myhead", "yml");
        assertEquals(new File(base, "myhead.yml").getPath(), out.getPath());
    }

    @Test void resolvesLegitNameWithoutExtension(@TempDir File base) {
        File out = PathSafety.resolveInside(base, "myhead");
        assertEquals(new File(base, "myhead").getPath(), out.getPath());
    }

    @Test void allowsNestedSubdirectory(@TempDir File base) {
        // A path INSIDE the base (with a separator) is not an escape.
        File out = PathSafety.resolveInside(base, "sub/head", "yml");
        assertTrue(PathSafety.isInside(base, out));
    }

    @Test void rejectsParentTraversal(@TempDir File base) {
        assertThrows(SecurityException.class, () -> PathSafety.resolveInside(base, "../evil", "yml"));
    }

    @Test void rejectsDeepParentTraversal(@TempDir File base) {
        assertThrows(SecurityException.class,
                () -> PathSafety.resolveInside(base, "../../../secret", "yml"));
    }

    @Test void rejectsPlatformSeparatorTraversal(@TempDir File base) {
        assertThrows(SecurityException.class,
                () -> PathSafety.resolveInside(base, ".." + File.separator + "evil", "yml"));
    }

    @Test void rejectsNullAndBlankNames(@TempDir File base) {
        assertThrows(SecurityException.class, () -> PathSafety.resolveInside(base, null));
        assertThrows(SecurityException.class, () -> PathSafety.resolveInside(base, "   "));
    }

    @Test void isInsideAllowsBaseItselfAndDescendants(@TempDir File base) {
        assertTrue(PathSafety.isInside(base, base));
        assertTrue(PathSafety.isInside(base, new File(base, "child.yml")));
    }

    @Test void isInsideRejectsParentAndSiblingPrefix(@TempDir File base) {
        assertFalse(PathSafety.isInside(base, base.getParentFile()));
        // Sibling with a shared string prefix must NOT count as inside (e.g. base "foo" vs "foobar").
        File sibling = new File(base.getParentFile(), base.getName() + "bar");
        assertFalse(PathSafety.isInside(base, sibling));
    }
}
