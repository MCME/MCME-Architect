package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.serverResoucePack.RpReleaseReport.Group;
import com.mcmiddleearth.architect.serverResoucePack.RpReleaseReport.Step;
import com.mcmiddleearth.architect.serverResoucePack.RpReleaseUtil.ScriptResult;
import com.mcmiddleearth.architect.serverResoucePack.RpReleaseVerdict.Kind;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// What Architect makes of a release's output: the lines ResourcePackScripts documents in "What MCME-Architect reads"
// (docs/release-pipeline.md), read from inline lines and from outputs captured from real releases
// (src/test/resources/rp-release). No Bukkit server is needed.
class RpReleaseReportTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(30);
    private static final String RUN_ID = "\\d{8}-\\d{6}-";

    private final List<Step> steps = new ArrayList<>();
    private final RpReleaseReport report = new RpReleaseReport(steps::add);

    private void read(String... lines) {
        for (String line : lines) {
            report.accept(line);
        }
    }

    private RpReleaseVerdict ended(int exitCode, String pack, String tag) {
        return report.verdict(new ScriptResult(true, exitCode, true), pack, tag, false, TIMEOUT);
    }

    private static List<String> fixture(String name) throws IOException {
        try (InputStream in = RpReleaseReportTest.class.getResourceAsStream("/rp-release/" + name)) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }

    private RpReleaseVerdict replay(String fixture, int exitCode, String pack, String tag, boolean dynmap)
            throws IOException {
        fixture(fixture).forEach(report::accept);
        return report.verdict(new ScriptResult(true, exitCode, true), pack, tag, dynmap, TIMEOUT);
    }

    // ── steps ────────────────────────────────────────────────────────

    @Test
    void aSodiumReleaseReportsEachGroupOfZipsDoneThenPublishing() {
        read("compiling Human RP zips", "Human-Sodium", "Human-Sodium-Footprints", "Human-Vanilla",
                "Human-Vanilla-Footprints", "Human-Lite", "Human-Lite-Footprints", "releasing Human RP zips");
        assertEquals(List.of(Step.SODIUM_DONE, Step.VANILLA_DONE, Step.LITE_DONE, Step.PUBLISHING), steps);
    }

    @Test
    void aVanillaOnlyReleaseReportsItsZipsDoneThenPublishing() {
        read("compiling Rohan RP zips", "releasing Rohan RP zips");
        assertEquals(List.of(Step.ZIPS_DONE, Step.PUBLISHING), steps);
    }

    @Test
    void theDynmapBuildReportsNoSteps() {
        read("compiling Gondor RP zips");
        assertEquals(List.of(), steps);
    }

    @Test
    void stepLinesAfterPublishingAreIgnoredAsTheWrapperIgnoresThem() {
        read("compiling Rohan RP zips", "releasing Rohan RP zips", "Rohan-Lite", "Rohan-Sodium");
        assertEquals(List.of(Step.ZIPS_DONE, Step.PUBLISHING), steps);
    }

    @Test
    void aTagThatEndsLikeAStepLineStillGivesTheReleaseUrl() {
        read("compiling Rohan RP zips", "releasing Rohan RP zips",
                "https://github.com/MCME/RP-Rohan/releases/tag/v1.0-Lite");
        assertEquals(List.of(Step.ZIPS_DONE, Step.PUBLISHING), steps);
        assertEquals("https://github.com/MCME/RP-Rohan/releases/tag/v1.0-Lite",
                ended(0, "Rohan", "v1.0-Lite").releaseUrl());
    }

    // A pack's name can hold the words a step line ends with
    @Test
    void aStepsGroupComesFromTheEndOfItsLine() {
        assertAll(
                () -> assertEquals(Group.SODIUM, RpReleaseReport.groupOf("Foo-Lite-Sodium")),
                () -> assertEquals(Group.SODIUM, RpReleaseReport.groupOf("Foo-Lite-Sodium-Footprints")),
                () -> assertEquals(Group.VANILLA, RpReleaseReport.groupOf("Foo-Sodium-Vanilla")),
                () -> assertEquals(Group.LITE, RpReleaseReport.groupOf("Foo-Vanilla-Lite-Footprints")),
                () -> assertEquals(Group.ZIPS, RpReleaseReport.groupOf("compiling Foo-Lite RP zips")),
                () -> assertEquals(Group.PUBLISHING, RpReleaseReport.groupOf("releasing Foo-Lite RP zips")));
    }

    // ── the release note ─────────────────────────────────────────────

    @Test
    void theReleaseNoteNamesTheMinecraftVersionsAfterForMinecraft() {
        assertAll(
                () -> assertEquals(List.of("26.2"), RpReleaseReport.versions("Version v1 for Minecraft 26.2")),
                () -> assertEquals(List.of("26.2", "26.3"),
                        RpReleaseReport.versions("Version v4.1.3 for Minecraft 26.2 and 26.3")),
                () -> assertEquals(List.of("1.21.4", "26.2", "26.3"),
                        RpReleaseReport.versions("Version v2.0 for Minecraft 1.21.4, 26.2 and 26.3")),
                () -> assertEquals(List.of(), RpReleaseReport.versions("Version v4.1.3")));
    }

    @Test
    void aReleaseWithoutANoteHasNoVersions() {
        read("compiling Rohan RP zips", "releasing Rohan RP zips", "https://github.com/MCME/RP-Rohan/releases/tag/v1.0");
        RpReleaseVerdict verdict = ended(0, "Rohan", "v1.0");
        assertEquals(List.of(), verdict.mcVersions());
        assertEquals("https://github.com/MCME/RP-Rohan/releases/tag/v1.0", verdict.releaseUrl());
    }

    // ── warnings and stops, from inline lines ────────────────────────

    @Test
    void warningsAreCountedByTheWrappersRuleAndTheFirstThreeShownWithoutTheirPrefix() {
        read("WARNING!!! release note: the zips support different Minecraft versions",
                "        WARNING!!! Missing model file: mcme:block/a",
                "WARNING! one",
                "WARNING!!! two",
                "Missing texture for mcme:block/b",
                "not a WARNING!!! at the start");
        RpReleaseVerdict verdict = ended(0, "Rohan", "v1.0");
        assertEquals("Rohan v1.0 is released, with 4 warnings:", verdict.headline());
        assertEquals(List.of(
                "  release note: the zips support different Minecraft versions",
                "  Missing model file: mcme:block/a",
                "  one",
                "…and 1 more: Architect's log on this server (ask an admin)."), verdict.details());
        assertTrue(verdict.ok());
        assertTrue(verdict.released());
    }

    @Test
    void oneWarningIsOneWarning() {
        read("WARNING!!! only one");
        assertEquals("Rohan v1.0 is released, with 1 warning:", ended(0, "Rohan", "v1.0").headline());
    }

    @Test
    void noMoreWarningsAreListedThanTheWrapperCounted() {
        read("WARNING!!! a", "WARNING!!! b", "WARNING!!! c",
                "Release run 20261012-101500-Rohan-v1.0: OK with warnings, released. 1 warning(s), 0 error(s).");
        RpReleaseVerdict verdict = ended(0, "Rohan", "v1.0");
        assertEquals("Rohan v1.0 is released, with 1 warning:", verdict.headline());
        assertEquals(List.of("  a"), verdict.details());
    }

    @Test
    void theWrappersCountOfWarningsWinsOverTheWarningsSeen() {
        read("WARNING!!! a", "WARNING!!! b",
                "Release run 20261012-101500-Human-v4.1.3: OK with warnings, released. 7 warning(s), 0 error(s).");
        RpReleaseVerdict verdict = ended(0, "Human", "v4.1.3");
        assertEquals("Human v4.1.3 is released, with 7 warnings:", verdict.headline());
        assertEquals(List.of("  a", "  b", "…and 5 more: the Resource packs page on the dashboard, run"
                + " 20261012-101500-Human-v4.1.3."), verdict.details());
    }

    @Test
    void theDynmapPackBuiltWithWarningsListsThem() {
        read("compiling Gondor RP zips", "WARNING!!! 7-Zip had a problem with some files of Gondor.zip (its report is"
                + " above); they may be missing from it.");
        RpReleaseVerdict verdict = report.verdict(new ScriptResult(true, 0, true), "Gondor", "v1.0", true, TIMEOUT);
        assertEquals(Kind.DYNMAP_BUILT, verdict.kind());
        assertEquals("Gondor's dynmap pack is built and in place, with 1 warning:", verdict.headline());
        assertEquals(List.of("  7-Zip had a problem with some files of Gondor.zip (its report is above); they may be"
                + " missing from it."), verdict.details());
        assertFalse(verdict.released(), "nothing to put live");
    }

    @Test
    void aStopShowsItsErrorLineAndTheLineBeforeWhenItSaysAbove() {
        read("fatal: unable to access 'https://github.com/MCME/RP-Rohan.git/': Could not resolve host: github.com",
                "ERROR: Couldn't fetch Rohan-Vanilla from GitHub (git's reason is above), so the release would build old files.");
        RpReleaseVerdict verdict = ended(1, "Rohan", "v1.0");
        assertEquals(Kind.FAILED, verdict.kind());
        assertEquals("Rohan v1.0 failed.", verdict.headline());
        assertEquals(List.of(
                "Last output: fatal: unable to access 'https://github.com/MCME/RP-Rohan.git/': Could not resolve host: github.com",
                "Couldn't fetch Rohan-Vanilla from GitHub (git's reason is above), so the release would build old files."),
                verdict.details());
        assertFalse(verdict.ok());
        assertFalse(verdict.released());
    }

    @Test
    void blankLinesAreNotTheLineBeforeAnErrorThatPointsAbove() {
        read("fatal: x", "", "   ",
                "ERROR: Couldn't fetch Rohan-Vanilla from GitHub (git's reason is above).");
        assertEquals("Last output: fatal: x", ended(1, "Rohan", "v1.0").details().get(0));
    }

    @Test
    void aStopThatDoesNotPointAboveShowsOnlyItsErrorLine() {
        read("compiling Rohan RP zips", "ERROR: The tag 'v1 0' can't be used.");
        assertEquals(List.of("The tag 'v1 0' can't be used."), ended(1, "Rohan", "v1 0").details());
    }

    @Test
    void anotherExitCodeIsNamedWithTheLastOutput() {
        read("sh: 1: releaseGeneral.sh: not found");
        RpReleaseVerdict verdict = ended(127, "Rohan", "v1.0");
        assertEquals(Kind.FAILED, verdict.kind());
        assertEquals("Rohan v1.0 failed with exit code 127.", verdict.headline());
        assertEquals(List.of("Last output: sh: 1: releaseGeneral.sh: not found"), verdict.details());
    }

    @Test
    void aRefusalWithoutLauncherLinesShowsTheLastOutput() {
        read("sh: 0: cannot open releaseGeneral.sh: No such file");
        RpReleaseVerdict verdict = ended(2, "Rohan", "v1.0");
        assertEquals(Kind.NOT_STARTED, verdict.kind());
        assertEquals(List.of("Last output: sh: 0: cannot open releaseGeneral.sh: No such file"), verdict.details());
    }

    @Test
    void theWrappersOwnLinesAreNeverTheLastOutput() {
        read("releasing Human RP zips", "release not found",
                "Release run 20261012-101500-Human-v4.1.3: FAILED, nothing released. 0 warning(s), 1 error(s).",
                "WARNING!!! No GitHub release was created.");
        assertEquals(List.of("Last output: release not found"), ended(1, "Human", "v4.1.3").details());
    }

    @Test
    void aReleaseStillRunningAtItsTimeoutWasStoppedDuringItsLastStep() {
        read("compiling Human RP zips", "Human-Sodium", "Human-Vanilla", "Human-Lite");
        RpReleaseVerdict verdict = report.verdict(new ScriptResult(false, -1, true), "Human", "v4.1.3", false,
                Duration.ofMinutes(45));
        assertEquals(Kind.TIMED_OUT, verdict.kind());
        assertEquals("Human v4.1.3 was stopped after 45 minutes, during the Lite zips. Check the release on GitHub,"
                + " or ask an admin.", verdict.headline());
        assertFalse(verdict.headline().contains("exit"), "no exit code was observed");
    }

    @Test
    void aTimeoutOfOneMinuteIsOneMinute() {
        RpReleaseVerdict verdict = report.verdict(new ScriptResult(false, -1, true), "Human", "v4.1.3", false,
                Duration.ofMinutes(1));
        assertEquals("Human v4.1.3 was stopped after 1 minute, during the start. Check the release on GitHub, or ask"
                + " an admin.", verdict.headline());
    }

    @Test
    void aReleaseStoppedBeforeAnyStepWasStoppedDuringTheStart() {
        RpReleaseVerdict verdict = report.verdict(new ScriptResult(false, -1, true), "Human", "v4.1.3", false, TIMEOUT);
        assertTrue(verdict.headline().contains("during the start."), verdict.headline());
    }

    @Test
    void aScriptThatCouldNotStartSaysWhy() {
        RpReleaseVerdict verdict = RpReleaseVerdict.couldNotStart("Human", "v4.1.3",
                "Cannot run program \"sh\" (in directory \"/nowhere\"): error=2, No such file or directory");
        assertEquals(Kind.COULD_NOT_START, verdict.kind());
        assertEquals("Human v4.1.3 couldn't be started: Cannot run program \"sh\" (in directory \"/nowhere\"):"
                + " error=2, No such file or directory. Ask an admin to check its gitHubRpReleases path.",
                verdict.headline());
    }

    @Test
    void theWrappersCountsWinAndItsFailedChecksAreNamed() {
        read("WARNING!!! a", "Release run 20261012-101500-Human-v4.1.3: FAILED, released. 2 warning(s), 3 error(s)."
                + " Human-Vanilla.zip 1,234 files");
        RpReleaseVerdict verdict = ended(0, "Human", "v4.1.3");
        assertEquals(Kind.RELEASED_CHECKS_FAILED, verdict.kind());
        assertEquals("Human v4.1.3 is released, but its checks found 3 errors and 2 warnings: see the dashboard,"
                + " run 20261012-101500-Human-v4.1.3.", verdict.headline());
        assertEquals(List.of("  a"), verdict.details());
        assertEquals("20261012-101500-Human-v4.1.3", verdict.runId());
        assertTrue(verdict.released());
        assertFalse(verdict.ok());
    }

    @Test
    void failedChecksWithoutCountsFoundProblems() {
        read("Release run 20261012-101500-Rohan-v1.0: FAILED, released. 0 warning(s), 0 error(s).",
                "WARNING!!! Rohan.zip is empty.");
        RpReleaseVerdict verdict = ended(0, "Rohan", "v1.0");
        assertEquals("Rohan v1.0 is released, but its checks found problems: see the dashboard,"
                + " run 20261012-101500-Rohan-v1.0.", verdict.headline());
        assertEquals(List.of("  Rohan.zip is empty."), verdict.details());
    }

    // ── whole outputs, captured from real releases ───────────────────

    @Test
    void aSodiumReleaseThroughTheWrapper() throws IOException {
        RpReleaseVerdict verdict = replay("sodium-wrapper.txt", 0, "Human", "v4.1.3", false);
        assertEquals(List.of(Step.SODIUM_DONE, Step.VANILLA_DONE, Step.LITE_DONE, Step.PUBLISHING), steps);
        assertEquals(Kind.RELEASED, verdict.kind());
        assertEquals("Human v4.1.3 is released, with 5 warnings:", verdict.headline());
        assertEquals(List.of("  Missing model file: mcme:block/oak_table_1", "  Missing model file: mcme:block/oak_table_2",
                "  Missing model file: mcme:block/oak_table_3"), verdict.details().subList(0, 3));
        assertTrue(verdict.details().get(3).matches("…and 2 more: the Resource packs page on the dashboard, run "
                + RUN_ID + "Human-v4\\.1\\.3\\."), verdict.details().get(3));
        assertEquals(List.of("26.2", "26.3"), verdict.mcVersions());
        assertEquals("https://github.com/MCME/RP-Human/releases/tag/v4.1.3", verdict.releaseUrl());
        assertTrue(verdict.runId().matches(RUN_ID + "Human-v4\\.1\\.3"), verdict.runId());
    }

    @Test
    void aVanillaOnlyReleaseWithoutTheWrapper() throws IOException {
        RpReleaseVerdict verdict = replay("general-no-wrapper.txt", 0, "Rohan", "v1.0", false);
        assertEquals(List.of(Step.ZIPS_DONE, Step.PUBLISHING), steps);
        assertEquals(Kind.RELEASED, verdict.kind());
        assertEquals("Rohan v1.0 is released.", verdict.headline());
        assertEquals(List.of(), verdict.details());
        assertEquals(List.of("26.2", "26.3"), verdict.mcVersions());
        assertEquals("https://github.com/MCME/RP-Rohan/releases/tag/v1.0", verdict.releaseUrl());
        assertNull(verdict.runId());
    }

    @Test
    void theDynmapPackThroughTheWrapper() throws IOException {
        RpReleaseVerdict verdict = replay("dynmap-wrapper.txt", 0, "Gondor", "v1.0", true);
        assertEquals(List.of(), steps);
        assertEquals(Kind.DYNMAP_BUILT, verdict.kind());
        assertEquals("Gondor's dynmap pack is built and in place.", verdict.headline());
        assertFalse(verdict.released(), "nothing to put live");
    }

    @Test
    void aStopOnThePacksUpdateThroughTheWrapper() throws IOException {
        RpReleaseVerdict verdict = replay("pull-stop-wrapper.txt", 1, "Rohan", "v1.0", false);
        assertEquals(Kind.FAILED, verdict.kind());
        assertEquals("Rohan v1.0 failed.", verdict.headline());
        assertTrue(verdict.details().get(0).startsWith("Last output: fatal: unable to access 'https://127.0.0.1:9/RP-Rohan.git/'"),
                verdict.details().get(0));
        assertEquals("Couldn't fetch Rohan-Vanilla from GitHub (git's reason is above), so the release would build old"
                + " files. Nothing was built. Try again in a few minutes. If it keeps failing, an admin fixes what"
                + " git's reason names.", verdict.details().get(1));
    }

    @Test
    void a7ZipFailureShowsTheScriptsReasonAfter7ZipsOwn() throws IOException {
        RpReleaseVerdict verdict = replay("7zip-failure.txt", 1, "Rohan", "v1.0", false);
        assertEquals(List.of("Last output: ERROR: the tests told 7-Zip to fail",
                "7-Zip couldn't make Rohan-Footprints.zip (its report is above). Nothing was published."),
                verdict.details());
    }

    @Test
    void aReleaseRefusedByTheWrapperWhileAnotherRuns() throws IOException {
        RpReleaseVerdict verdict = replay("busy-wrapper.txt", 75, "Rohan", "v1.1", false);
        assertEquals(Kind.BUSY, verdict.kind());
        assertEquals("Rohan v1.1 didn't start: another release is running.", verdict.headline());
        assertEquals(List.of("Another resource pack release is running: Human v4.1.3 by Builder since 12:00:00, now at"
                + " Human-Vanilla."), verdict.details());
    }

    @Test
    void aReleaseRefusedByTheLauncherWhileAnotherRuns() throws IOException {
        RpReleaseVerdict verdict = replay("busy-launcher.txt", 75, "Rohan", "v1.1", false);
        assertEquals(List.of("Another release is running: releaseVanillaSodium.sh Human MCME RP-Human v4.1.3 Leaves"
                + " (started 2026-10-12 12:00:00). Start this one again once it has finished."), verdict.details());
    }

    // The refusal's sentence comes before the files it lists
    @Test
    void aLauncherRefusalShowsItsSentenceBeforeTheFiles() throws IOException {
        RpReleaseVerdict verdict = replay("launcher-edited.txt", 3, "Rohan", "v1.0", false);
        assertEquals(Kind.NOT_STARTED, verdict.kind());
        assertEquals("Rohan v1.0 didn't start: the release system on this server needs an admin.", verdict.headline());
        assertEquals(List.of("Someone edited ResourcePackScripts on the server. That change must go through a pull"
                + " request; until then no release runs here. What git status shows:", "  ?? extra1.txt",
                "  ?? extra2.txt"), verdict.details());
    }

    @Test
    void aReleaseWhoseChecksFailedThroughTheWrapper() throws IOException {
        RpReleaseVerdict verdict = replay("checks-failed-wrapper.txt", 0, "Human", "v4.1.3", false);
        assertEquals(Kind.RELEASED_CHECKS_FAILED, verdict.kind());
        assertTrue(verdict.headline().matches("Human v4\\.1\\.3 is released, but its checks found 1 error: see the"
                + " dashboard, run " + RUN_ID + "Human-v4\\.1\\.3\\."), verdict.headline());
        assertEquals(List.of("26.2", "26.3"), verdict.mcVersions());
    }
}
