package com.mcmiddleearth.architect.serverResoucePack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The chat lines of /rp release. Each starts with Architect's prefix and has MessageUtil's colours: news in aqua,
 * errors in red. Buttons are gold: they fill in a command for the releaser to send, or open the GitHub release.
 */
final class RpReleaseChat {

    private RpReleaseChat() {
    }

    /** "Releasing Human v4.1.3 ("Better leaves"). Last time this took about 5 minutes. The result comes here." */
    static Component start(String prefix, String pack, String tag, String title, Duration lastTime) {
        return info(prefix, "Releasing " + pack + " " + tag + " (\"" + title + "\")." + lastTimeTook(lastTime)
                + " The result comes here.");
    }

    /** "Human: Sodium zips done", or "Human: publishing on GitHub…". */
    static Component step(String prefix, String pack, RpReleaseReport.Step step) {
        String text = switch (step) {
            case SODIUM_DONE -> "Sodium zips done";
            case VANILLA_DONE -> "Vanilla zips done";
            case LITE_DONE -> "Lite zips done";
            case ZIPS_DONE -> "zips done";
            case PUBLISHING -> "publishing on GitHub…";
        };
        return info(prefix, pack + ": " + text);
    }

    /** The closing message: its headline, the lines under it and, for a release on GitHub, the buttons to put it live. */
    static List<Component> verdict(String prefix, RpReleaseVerdict verdict, String pack, String tag) {
        List<Component> lines = new ArrayList<>();
        lines.add(line(prefix, verdict.headline(), verdict.ok()));
        verdict.details().forEach(detail -> lines.add(line(prefix, detail, verdict.ok())));
        if (verdict.released()) {
            lines.add(putItLive(prefix, verdict, pack, tag));
        }
        return lines;
    }

    /** The line before the results a releaser missed while they were away. */
    static Component awayHeader(String prefix) {
        return info(prefix, "While you were away:");
    }

    // " Last time this took about 5 minutes.", rounded to the nearest minute; nothing without a record
    static String lastTimeTook(Duration lastTime) {
        if (lastTime == null) {
            return "";
        }
        if (lastTime.compareTo(Duration.ofMinutes(1)) < 0) {
            return " Last time this took less than a minute.";
        }
        long minutes = Math.round(lastTime.toSeconds() / 60.0);
        return " Last time this took about " + RpReleaseReport.count(minutes, "minute") + ".";
    }

    // "Put it live: [26.2] [26.3] [Open on GitHub]"
    private static Component putItLive(String prefix, RpReleaseVerdict verdict, String pack, String tag) {
        Component line = Component.text(prefix + "Put it live: ", NamedTextColor.AQUA);
        String server = "/rp server " + pack + " " + tag + " ";
        if (verdict.mcVersions().isEmpty()) {
            line = line.append(button("/rp server …", server, "Fill in " + server + "<Minecraft version>"));
        }
        for (String version : verdict.mcVersions()) {
            line = line.append(button(version, server + version, "Fill in " + server + version));
        }
        if (verdict.releaseUrl() != null) {
            line = line.append(Component.text("[Open on GitHub] ", NamedTextColor.GOLD)
                    .clickEvent(ClickEvent.openUrl(verdict.releaseUrl()))
                    .hoverEvent(HoverEvent.showText(Component.text(verdict.releaseUrl()))));
        }
        return line;
    }

    private static Component button(String label, String command, String hover) {
        return Component.text("[" + label + "] ", NamedTextColor.GOLD)
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    private static Component line(String prefix, String text, boolean news) {
        return news ? info(prefix, text) : error(prefix, text);
    }

    private static Component info(String prefix, String text) {
        return Component.text(prefix + text, NamedTextColor.AQUA);
    }

    private static Component error(String prefix, String text) {
        return Component.text(prefix + text, NamedTextColor.RED);
    }
}
