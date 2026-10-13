package com.mcmiddleearth.architect.serverResoucePack;

import java.util.List;

/**
 * How a release ended, as the person who started it reads it: its kind, a headline, the lines under it, and what the
 * next step needs: the Minecraft versions the release note names, the GitHub release and the pipeline wrapper's run
 * id. The release and the run id are null when the output had none.
 */
record RpReleaseVerdict(Kind kind, String headline, List<String> details, List<String> mcVersions, String releaseUrl,
                        String runId) {

    enum Kind { RELEASED, RELEASED_CHECKS_FAILED, DYNMAP_BUILT, FAILED, BUSY, NOT_STARTED, TIMED_OUT, COULD_NOT_START }

    RpReleaseVerdict {
        details = List.copyOf(details);
        mcVersions = List.copyOf(mcVersions);
    }

    /** The verdict for a release script that couldn't be started at all. */
    static RpReleaseVerdict couldNotStart(String pack, String tag, String reason) {
        return new RpReleaseVerdict(Kind.COULD_NOT_START, pack + " " + tag + " couldn't be started: " + reason
                + ". Ask an admin to check its gitHubRpReleases path.", List.of(), List.of(), null, null);
    }

    /** Whether it went as it should, so the headline reads as news rather than as an error. */
    boolean ok() {
        return kind == Kind.RELEASED || kind == Kind.DYNMAP_BUILT;
    }

    /** Whether the release is on GitHub, so the next step is putting it live. */
    boolean released() {
        return kind == Kind.RELEASED || kind == Kind.RELEASED_CHECKS_FAILED;
    }
}
