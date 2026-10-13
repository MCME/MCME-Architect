package com.mcmiddleearth.architect.biomeTuning;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Answers as a client sends them: every input's value by key. untouched() is a client where nothing was changed,
 * and with() changes one value.
 */
final class FakeAnswers implements EditorView.Answers {

    private final Map<String, Object> values;

    private FakeAnswers(Map<String, Object> values) {
        this.values = values;
    }

    static FakeAnswers of(Map<String, ?> values) {
        return new FakeAnswers(new HashMap<>(values));
    }

    static FakeAnswers untouched(List<EditorView.Input> inputs) {
        Map<String, Object> values = new HashMap<>();
        for (EditorView.Input input : inputs) {
            switch (input) {
                case EditorView.Slider slider -> values.put(slider.key(), slider.initial());
                case EditorView.Toggle toggle -> values.put(toggle.key(), toggle.initial());
                case EditorView.Choice choice -> choice.options().stream().filter(EditorView.Option::initial)
                        .findFirst().ifPresent(option -> values.put(choice.key(), option.id()));
                case EditorView.TextBox text -> values.put(text.key(), text.initial());
            }
        }
        return new FakeAnswers(values);
    }

    static FakeAnswers untouched(EditorView view) {
        return untouched(view.inputs());
    }

    FakeAnswers with(String key, Object value) {
        Map<String, Object> copy = new HashMap<>(values);
        copy.put(key, value);
        return new FakeAnswers(copy);
    }

    @Override
    public Float number(String key) {
        return values.get(key) instanceof Number number ? number.floatValue() : null;
    }

    @Override
    public Boolean flag(String key) {
        return values.get(key) instanceof Boolean flag ? flag : null;
    }

    @Override
    public String text(String key) {
        return values.get(key) instanceof String text ? text : null;
    }
}
