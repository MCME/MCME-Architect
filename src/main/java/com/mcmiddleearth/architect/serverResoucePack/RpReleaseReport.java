package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.serverResoucePack.RpReleaseUtil.ScriptResult;
import com.mcmiddleearth.architect.serverResoucePack.RpReleaseVerdict.Kind;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a release's output says, read line by line as it comes: the steps it reaches, its warnings, why it stopped,
 * the launcher's lines, the GitHub release and the Minecraft versions its note names, and the pipeline wrapper's
 * verdict. Once the script has ended, {@link #verdict} turns that into the closing message's text. The lines it reads
 * are a contract with ResourcePackScripts, listed in "What MCME-Architect reads" in its docs/release-pipeline.md:
 * change both together. Lines come from the thread that reads the script's output, which can still deliver some after
 * the script has ended, so every method is synchronized.
 */
final class RpReleaseReport {

    /** What a step line reports: a group of zips done, or publishing begun. */
    enum Step { SODIUM_DONE, VANILLA_DONE, LITE_DONE, ZIPS_DONE, PUBLISHING }

    /** The groups of steps of a release, in their order. */
    enum Group { ZIPS, SODIUM, VANILLA, LITE, PUBLISHING }

    /** The pipeline wrapper's verdict line: its run id, status, outcome and counts. */
    record WrapperVerdict(String runId, String status, String outcome, int warnings, int errors) {}

    /** How many warnings the closing message shows. */
    static final int SHOWN_WARNINGS = 3;

    /** How many of the launcher's lines a refusal shows. */
    static final int SHOWN_LAUNCHER_LINES = 3;

    // The wrapper's STEP pattern, in ResourcePackScripts' automation/pipeline/rp-release.py
    private static final Pattern STEP = Pattern.compile(
            "^(compiling \\S+ RP zips|releasing \\S+ RP zips|\\S+-(?:Sodium|Vanilla|Lite)(?:-Footprints)?)$");
    // The wrapper's rule for a warning: the line starts with WARNING!, after any spaces
    private static final Pattern WARNING = Pattern.compile("^\\s*WARNING!+\\s*(.*)$");
    private static final Pattern ERROR = Pattern.compile("^ERROR: (.*)$");
    private static final Pattern LAUNCHER = Pattern.compile("^release launcher: (.*)$");
    // What gh prints once it has created the release: the wrapper's RELEASE_URL pattern
    private static final Pattern RELEASE_URL =
            Pattern.compile("^https://github\\.com/[^/\\s]+/[^/\\s]+/releases/tag/\\S+$");
    private static final Pattern RELEASE_NOTE = Pattern.compile("^release note: (.*)$");
    private static final Pattern VERSION = Pattern.compile("\\d+(?:\\.\\d+)+");
    private static final Pattern WRAPPER_VERDICT = Pattern.compile("^Release run (\\S+): "
            + "(OK|OK with warnings|FAILED|INTERRUPTED), (released|nothing released|cut off)\\. "
            + "(\\d+) warning\\(s\\), (\\d+) error\\(s\\)\\.");
    private static final Pattern WRAPPER_BUSY = Pattern.compile("^Another resource pack release is running: .*$");

    private final Consumer<Step> steps;
    private Group group;
    private int warnings;
    private final List<String> shownWarnings = new ArrayList<>();
    private String lastError;
    private String beforeLastError;
    private final List<String> launcherLines = new ArrayList<>();
    private String releaseUrl;
    private List<String> mcVersions = List.of();
    private WrapperVerdict wrapperVerdict;
    private String wrapperBusy;
    private String lastLine;

    /** steps gets each step as it is reached, on the thread that reads the output. */
    RpReleaseReport(Consumer<Step> steps) {
        this.steps = steps;
    }

    /** Reads one line of the release's output. */
    synchronized void accept(String line) {
        if (line.isBlank()) {
            return;
        }
        Matcher match;
        // After publishing, step lines fall through, as the wrapper's fold() ignores them (after_release)
        if (group != Group.PUBLISHING && STEP.matcher(line).matches()) {
            step(groupOf(line));
        } else if ((match = WARNING.matcher(line)).matches()) {
            warnings++;
            if (shownWarnings.size() < SHOWN_WARNINGS) {
                shownWarnings.add(match.group(1));
            }
        } else if ((match = ERROR.matcher(line)).matches()) {
            lastError = match.group(1);
            beforeLastError = lastLine;
        } else if ((match = LAUNCHER.matcher(line)).matches()) {
            launcherLines.add(match.group(1));
        } else if (RELEASE_URL.matcher(line).matches()) {
            releaseUrl = line;
        } else if ((match = RELEASE_NOTE.matcher(line)).matches()) {
            mcVersions = versions(match.group(1));
        } else if ((match = WRAPPER_VERDICT.matcher(line)).lookingAt()) {
            wrapperVerdict = new WrapperVerdict(match.group(1), match.group(2), match.group(3),
                    Integer.parseInt(match.group(4)), Integer.parseInt(match.group(5)));
        } else if (WRAPPER_BUSY.matcher(line).matches()) {
            wrapperBusy = line;
        }
        // The wrapper's verdict line, and the problem lines it prints after it, are not the release's own output
        if (wrapperVerdict == null) {
            lastLine = line;
        }
    }

    /**
     * The closing message's text, once the script has ended or was stopped: result is how it ended, pack and tag the
     * release, dynmap whether the script builds the dynmap pack, which publishes nothing, and timeout how long it had.
     */
    synchronized RpReleaseVerdict verdict(ScriptResult result, String pack, String tag, boolean dynmap,
                                          Duration timeout) {
        String release = pack + " " + tag;
        if (!result.terminated()) {
            return verdict(Kind.TIMED_OUT, release + " was stopped after " + count(timeout.toMinutes(), "minute")
                    + ", during " + during() + ". Check the release on GitHub, or ask an admin.", List.of());
        }
        int exit = result.exitCode();
        if (exit == 0 && dynmap) {
            return withWarnings(Kind.DYNMAP_BUILT, pack + "'s dynmap pack is built and in place");
        }
        if (exit == 0 && wrapperVerdict != null && wrapperVerdict.status().equals("FAILED")) {
            return verdict(Kind.RELEASED_CHECKS_FAILED, release + " is released, but its checks found " + found()
                    + ": see the dashboard, run " + wrapperVerdict.runId() + ".", indented(shownWarnings));
        }
        if (exit == 0) {
            return withWarnings(Kind.RELEASED, release + " is released");
        }
        if (exit == 75) {
            String running = wrapperBusy != null ? wrapperBusy : launcherLines.stream()
                    .filter(text -> text.startsWith("Another release is running")).findFirst().orElse(null);
            return verdict(Kind.BUSY, release + " didn't start: another release is running.",
                    running == null ? List.of() : List.of(running));
        }
        if (exit == 2 || exit == 3) {
            // A refusal's sentence comes first, before any list of files
            List<String> lines = launcherLines.isEmpty() ? lastOutput()
                    : launcherLines.subList(0, Math.min(SHOWN_LAUNCHER_LINES, launcherLines.size()));
            return verdict(Kind.NOT_STARTED,
                    release + " didn't start: the release system on this server needs an admin.", lines);
        }
        return verdict(Kind.FAILED, exit == 1 ? release + " failed." : release + " failed with exit code " + exit + ".",
                reason());
    }

    /** The group a step line belongs to. A pack's name can hold dashes, so only the end of the line counts. */
    static Group groupOf(String step) {
        if (step.startsWith("compiling ")) {
            return Group.ZIPS;
        }
        if (step.startsWith("releasing ")) {
            return Group.PUBLISHING;
        }
        String zip = step.endsWith("-Footprints") ? step.substring(0, step.length() - "-Footprints".length()) : step;
        if (zip.endsWith("-Sodium")) {
            return Group.SODIUM;
        }
        return zip.endsWith("-Vanilla") ? Group.VANILLA : Group.LITE;
    }

    /** The Minecraft versions a release note names after "for Minecraft": "… for Minecraft 26.2 and 26.3". */
    static List<String> versions(String note) {
        int at = note.indexOf("for Minecraft ");
        if (at < 0) {
            return List.of();
        }
        List<String> found = new ArrayList<>();
        Matcher match = VERSION.matcher(note.substring(at + "for Minecraft ".length()));
        while (match.find()) {
            found.add(match.group());
        }
        return List.copyOf(found);
    }

    /** "1 warning", "14 warnings". */
    static String count(long n, String what) {
        return n + " " + what + (n == 1 ? "" : "s");
    }

    // A group's end is reported when the next one starts. The Sodium script's "compiling" line starts its Sodium
    // zips, so leaving it for a group of zips reports nothing; leaving it for publishing reports a vanilla-only
    // pack's zips done.
    private void step(Group next) {
        if (group == Group.PUBLISHING || next == group) {
            return;
        }
        if (group == Group.SODIUM) {
            steps.accept(Step.SODIUM_DONE);
        } else if (group == Group.VANILLA) {
            steps.accept(Step.VANILLA_DONE);
        } else if (group == Group.LITE) {
            steps.accept(Step.LITE_DONE);
        } else if (group == Group.ZIPS && next == Group.PUBLISHING) {
            steps.accept(Step.ZIPS_DONE);
        }
        if (next == Group.PUBLISHING) {
            steps.accept(Step.PUBLISHING);
        }
        group = next;
    }

    // The headline, with the first warnings under it and where to read the rest
    private RpReleaseVerdict withWarnings(Kind kind, String headline) {
        int total = wrapperVerdict != null ? wrapperVerdict.warnings() : warnings;
        if (total == 0) {
            return verdict(kind, headline + ".", List.of());
        }
        List<String> listed = shownWarnings.subList(0, Math.min(total, shownWarnings.size()));
        List<String> lines = new ArrayList<>(indented(listed));
        int more = total - listed.size();
        if (more > 0) {
            lines.add("…and " + more + " more: " + where() + ".");
        }
        return verdict(kind, headline + ", with " + count(total, "warning") + ":", lines);
    }

    // The wrapper's counts that aren't 0; "problems" when it failed the run on a problem of its summary
    private String found() {
        List<String> parts = new ArrayList<>();
        if (wrapperVerdict.errors() > 0) {
            parts.add(count(wrapperVerdict.errors(), "error"));
        }
        if (wrapperVerdict.warnings() > 0) {
            parts.add(count(wrapperVerdict.warnings(), "warning"));
        }
        return parts.isEmpty() ? "problems" : String.join(" and ", parts);
    }

    private String where() {
        return wrapperVerdict != null ? "the Resource packs page on the dashboard, run " + wrapperVerdict.runId()
                : "Architect's log on this server (ask an admin)";
    }

    // Why the release stopped: the last ERROR: line, and the line before it when the ERROR: line points there
    private List<String> reason() {
        if (lastError == null) {
            return lastOutput();
        }
        List<String> lines = new ArrayList<>();
        if (lastError.contains("above") && beforeLastError != null) {
            lines.add("Last output: " + beforeLastError);
        }
        lines.add(lastError);
        return lines;
    }

    private List<String> lastOutput() {
        return lastLine == null ? List.of() : List.of("Last output: " + lastLine);
    }

    private String during() {
        if (group == null) {
            return "the start";
        }
        return switch (group) {
            case ZIPS -> "the zips";
            case SODIUM -> "the Sodium zips";
            case VANILLA -> "the Vanilla zips";
            case LITE -> "the Lite zips";
            case PUBLISHING -> "publishing";
        };
    }

    private static List<String> indented(List<String> lines) {
        return lines.stream().map(line -> "  " + line).toList();
    }

    private RpReleaseVerdict verdict(Kind kind, String headline, List<String> details) {
        return new RpReleaseVerdict(kind, headline, details, mcVersions, releaseUrl,
                wrapperVerdict == null ? null : wrapperVerdict.runId());
    }
}
