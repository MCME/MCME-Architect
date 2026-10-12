package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.serverResoucePack.RpReleaseReport.Step;
import com.mcmiddleearth.architect.serverResoucePack.RpReleaseVerdict.Kind;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The chat lines of /rp release, as Adventure components. No Bukkit server is needed.
class RpReleaseChatTest {

    private static final String PREFIX = "[Architect] ";
    private static final String URL = "https://github.com/MCME/RP-Human/releases/tag/v4.1.3";

    private static String text(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static List<String> texts(List<Component> components) {
        return components.stream().map(RpReleaseChatTest::text).toList();
    }

    @Test
    void theStartLineNamesTheReleaseAndHowLongTheLastOneTook() {
        assertEquals("[Architect] Releasing Human v4.1.3 (\"Better leaves\"). Last time this took about 5 minutes."
                + " The result comes here.",
                text(RpReleaseChat.start(PREFIX, "Human", "v4.1.3", "Better leaves", Duration.ofSeconds(312))));
        assertEquals("[Architect] Releasing Rohan v1.0 (\"x\"). The result comes here.",
                text(RpReleaseChat.start(PREFIX, "Rohan", "v1.0", "x", null)));
    }

    @Test
    void theLastTimeIsRoundedToTheNearestMinute() {
        assertEquals(" Last time this took less than a minute.", RpReleaseChat.lastTimeTook(Duration.ofSeconds(59)));
        assertEquals(" Last time this took about 1 minute.", RpReleaseChat.lastTimeTook(Duration.ofSeconds(60)));
        assertEquals(" Last time this took about 1 minute.", RpReleaseChat.lastTimeTook(Duration.ofSeconds(89)));
        assertEquals(" Last time this took about 2 minutes.", RpReleaseChat.lastTimeTook(Duration.ofSeconds(90)));
        assertEquals("", RpReleaseChat.lastTimeTook(null));
    }

    // Review focus 3: quotes, and section signs, which colour legacy text, show as typed
    @Test
    void aTitleIsShownAsTyped() {
        Component start = RpReleaseChat.start(PREFIX, "Human", "v4", "It's \"new\" §cred", null);
        assertEquals("[Architect] Releasing Human v4 (\"It's \"new\" §cred\"). The result comes here.", text(start));
        assertEquals(NamedTextColor.AQUA, start.color());
        assertEquals(List.of(), start.children(), "one plain text: no colour code was parsed");
    }

    @Test
    void eachStepReadsAsItsGroupDone() {
        assertEquals("[Architect] Human: Sodium zips done", text(RpReleaseChat.step(PREFIX, "Human", Step.SODIUM_DONE)));
        assertEquals("[Architect] Human: Vanilla zips done", text(RpReleaseChat.step(PREFIX, "Human", Step.VANILLA_DONE)));
        assertEquals("[Architect] Human: Lite zips done", text(RpReleaseChat.step(PREFIX, "Human", Step.LITE_DONE)));
        assertEquals("[Architect] Rohan: zips done", text(RpReleaseChat.step(PREFIX, "Rohan", Step.ZIPS_DONE)));
        assertEquals("[Architect] Rohan: publishing on GitHub…",
                text(RpReleaseChat.step(PREFIX, "Rohan", Step.PUBLISHING)));
    }

    @Test
    void aReleaseGetsAButtonPerMinecraftVersionAndOneForGitHub() {
        RpReleaseVerdict verdict = new RpReleaseVerdict(Kind.RELEASED, "Human v4.1.3 is released.", List.of(),
                List.of("26.2", "26.3"), URL, null);
        List<Component> lines = RpReleaseChat.verdict(PREFIX, verdict, "Human", "v4.1.3");
        assertEquals(List.of("[Architect] Human v4.1.3 is released.",
                "[Architect] Put it live: [26.2] [26.3] [Open on GitHub] "), texts(lines));
        List<Component> buttons = lines.get(1).children();
        assertEquals(ClickEvent.suggestCommand("/rp server Human v4.1.3 26.2"), buttons.get(0).clickEvent());
        assertEquals(ClickEvent.suggestCommand("/rp server Human v4.1.3 26.3"), buttons.get(1).clickEvent());
        assertEquals(ClickEvent.openUrl(URL), buttons.get(2).clickEvent());
        assertEquals(NamedTextColor.GOLD, buttons.get(0).color());
        assertEquals(NamedTextColor.AQUA, lines.get(0).color());
    }

    @Test
    void withoutAReleaseNoteOneButtonFillsInTheCommandToFinish() {
        RpReleaseVerdict verdict = new RpReleaseVerdict(Kind.RELEASED, "Human v4.1.3 is released.", List.of(),
                List.of(), null, null);
        List<Component> lines = RpReleaseChat.verdict(PREFIX, verdict, "Human", "v4.1.3");
        assertEquals("[Architect] Put it live: [/rp server …] ", text(lines.get(1)));
        assertEquals(ClickEvent.suggestCommand("/rp server Human v4.1.3 "), lines.get(1).children().get(0).clickEvent());
    }

    @Test
    void theDetailsFollowTheHeadlineInItsColour() {
        RpReleaseVerdict verdict = new RpReleaseVerdict(Kind.FAILED, "Rohan v1.0 failed.",
                List.of("Last output: fatal: x", "Couldn't fetch Rohan-Vanilla from GitHub."), List.of(), null, null);
        List<Component> lines = RpReleaseChat.verdict(PREFIX, verdict, "Rohan", "v1.0");
        assertEquals(List.of("[Architect] Rohan v1.0 failed.", "[Architect] Last output: fatal: x",
                "[Architect] Couldn't fetch Rohan-Vanilla from GitHub."), texts(lines), "no buttons after a failure");
        assertTrue(lines.stream().allMatch(line -> NamedTextColor.RED.equals(line.color())));
    }

    @Test
    void aReleaseWhoseChecksFailedIsRedButStillOffersTheButtons() {
        RpReleaseVerdict verdict = new RpReleaseVerdict(Kind.RELEASED_CHECKS_FAILED,
                "Human v4.1.3 is released, but its checks found 1 error: see the dashboard, run r.", List.of(),
                List.of("26.2"), URL, "r");
        List<Component> lines = RpReleaseChat.verdict(PREFIX, verdict, "Human", "v4.1.3");
        assertEquals(NamedTextColor.RED, lines.get(0).color());
        assertEquals("[Architect] Put it live: [26.2] [Open on GitHub] ", text(lines.get(1)));
    }

    @Test
    void theAwayHeaderComesBeforeHeldResults() {
        assertEquals("[Architect] While you were away:", text(RpReleaseChat.awayHeader(PREFIX)));
    }
}
