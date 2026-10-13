package com.mcmiddleearth.architect.biomeTuning;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.entity.Player;

/**
 * One window of the biome editor as plain data: a title, lines of text, inputs, buttons in two or three columns, and
 * the footer button that Escape also presses ({@code exit}, may be null). BiomeEditor builds these and a
 * DialogPresenter shows them, so the editor can be tested without a client. The checks are at least as strict as the
 * ones Paper's dialog builders make, so a bad window fails in a test rather than in the game.
 */
public record EditorView(Component title, List<Component> body, List<Input> inputs, List<Button> buttons, Button exit,
                         int columns) {

    /** A window with its buttons in two columns. */
    public EditorView(Component title, List<Component> body, List<Input> inputs, List<Button> buttons, Button exit) {
        this(title, body, inputs, buttons, exit, 2);
    }

    public EditorView {
        Objects.requireNonNull(title, "title");
        body = List.copyOf(body);
        inputs = List.copyOf(inputs);
        buttons = List.copyOf(buttons);
        Set<String> keys = new HashSet<>();
        for (Input input : inputs) {
            if (!keys.add(input.key())) {
                throw new IllegalArgumentException("two inputs share the key " + input.key());
            }
        }
        if (buttons.isEmpty()) {
            throw new IllegalArgumentException("a window needs at least one button");
        }
        if (columns != 2 && columns != 3) {
            throw new IllegalArgumentException("buttons sit in 2 or 3 columns, not " + columns);
        }
    }

    /**
     * The body as one block of lines. The client puts 18 px of padding and spacing around each separate block, and
     * windows are tall enough to scroll as it is.
     */
    public Component text() {
        return Component.join(JoinConfiguration.newlines(), body);
    }

    /**
     * What a click sends back: each input's value by key, or null when it is missing. Answers come from the client
     * unchecked (Paper does not validate them), so a value can also be out of range or not one of the options.
     */
    public interface Answers {

        Float number(String key);

        Boolean flag(String key);

        String text(String key);
    }

    /** What a button does. It runs on the main thread, with the player who clicked. */
    @FunctionalInterface
    public interface Action {
        void run(Player player, Answers answers);
    }

    /** {@code tooltip} may be null. */
    public record Button(Component label, Component tooltip, Action action) {

        public Button {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(action, "action");
        }
    }

    public sealed interface Input permits Slider, Toggle, Choice, TextBox {

        String key();

        Component label();
    }

    /**
     * A number slider. {@code format} is its label format, for example "%s: %s blocks". It shows its value times
     * {@code scale}, a power of ten, and min, max, step and initial are what it shows: with scale 100, a downfall of
     * 0.8 shows as 80.
     */
    public record Slider(String key, Component label, float min, float max, float step, float initial, String format,
                         float scale) implements Input {

        public Slider {
            checkKey(key);
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(format, "format");
            if (!Float.isFinite(min) || !Float.isFinite(max) || !(min < max)) {
                throw new IllegalArgumentException(key + ": " + min + ".." + max + " is not a range");
            }
            if (!Float.isFinite(step) || !(step > 0)) {
                throw new IllegalArgumentException(key + ": the step must be above 0, not " + step);
            }
            // as in Paper, which compares with Float.compare: NaN lies above every number, and -0.0 below 0.0
            if (Float.compare(initial, min) < 0 || Float.compare(initial, max) > 0) {
                throw new IllegalArgumentException(key + ": " + initial + " must lie in " + min + ".." + max);
            }
            if (!Float.isFinite(scale) || !(scale > 0)) {
                throw new IllegalArgumentException(key + ": the scale must be above 0, not " + scale);
            }
        }

        /** A slider that shows its value as it is. */
        public Slider(String key, Component label, float min, float max, float step, float initial, String format) {
            this(key, label, min, max, step, initial, format, 1);
        }
    }

    public record Toggle(String key, Component label, boolean initial) implements Input {

        public Toggle {
            checkKey(key);
            Objects.requireNonNull(label, "label");
        }
    }

    /** A button that cycles through options. Exactly one starts selected, on purpose: Paper also allows none. */
    public record Choice(String key, Component label, List<Option> options) implements Input {

        public Choice {
            checkKey(key);
            Objects.requireNonNull(label, "label");
            options = List.copyOf(options);
            Set<String> ids = new HashSet<>();
            for (Option option : options) {
                if (!ids.add(option.id())) {
                    throw new IllegalArgumentException(key + ": two options share the id " + option.id());
                }
            }
            if (options.stream().filter(Option::initial).count() != 1) {
                throw new IllegalArgumentException(key + ": exactly one option must start selected");
            }
        }
    }

    /** {@code label} may be null. */
    public record Option(String id, Component label, boolean initial) {

        public Option {
            Objects.requireNonNull(id, "id");
        }
    }

    public record TextBox(String key, Component label, String initial, int maxLength, boolean multiline)
            implements Input {

        public TextBox {
            checkKey(key);
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(initial, "initial");
            if (maxLength < 1) {
                throw new IllegalArgumentException(key + ": the length limit must be at least 1, not " + maxLength);
            }
            if (initial.length() > maxLength) {
                throw new IllegalArgumentException(key + ": the text is longer than " + maxLength);
            }
        }
    }

    /**
     * Paper takes letters, digits and underscores in input keys. "id" is taken: Paper keeps a click's callback id
     * under that key, and the client writes every input's value next to it, so an input named id breaks every button.
     */
    private static void checkKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9_]+") || key.equals("id")) {
            throw new IllegalArgumentException("not a valid input key: " + key);
        }
    }
}
