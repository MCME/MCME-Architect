package com.mcmiddleearth.architect.biomeTuning;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a submitted section form. Only the fields the builder changed become edits, so an untouched
 * slider never overwrites a value. Pure.
 */
final class EditorForms {

    /** The choice that removes a value, so the dimension's value applies. */
    static final String NOT_SET = "not_set";

    private EditorForms() {
    }

    /**
     * The edits a submitted form asks for. A slider counts when it moved more than half a step; a toggle or a choice
     * when it differs from how it started, and choosing {@link #NOT_SET} removes the value (a null edit). Text boxes
     * are ignored: the colour picker and the JSON editor read their own. The answers come from the client unchecked,
     * so a missing answer means unchanged, a slider answer must be finite and is kept inside the slider's range, and
     * a choice must be one of its options.
     */
    static List<BiomeTuningService.Edit> changes(List<EditorView.Input> inputs, EditorView.Answers answers) {
        List<BiomeTuningService.Edit> edits = new ArrayList<>();
        for (EditorView.Input input : inputs) {
            switch (input) {
                case EditorView.Slider slider -> {
                    Float answer = answers.number(slider.key());
                    if (answer != null && Float.isFinite(answer)) {
                        float value = Math.max(slider.min(), Math.min(slider.max(), answer));
                        if (Math.abs(value - slider.initial()) > slider.step() / 2) {
                            edits.add(new BiomeTuningService.Edit(slider.key(),
                                    number(value, slider.step(), slider.scale())));
                        }
                    }
                }
                case EditorView.Toggle toggle -> {
                    Boolean value = answers.flag(toggle.key());
                    if (value != null && value != toggle.initial()) {
                        edits.add(new BiomeTuningService.Edit(toggle.key(), value.toString()));
                    }
                }
                case EditorView.Choice choice -> {
                    String value = answers.text(choice.key());
                    String initial = choice.options().stream().filter(EditorView.Option::initial)
                            .map(EditorView.Option::id).findFirst().orElse(null);
                    boolean offered = choice.options().stream().anyMatch(option -> option.id().equals(value));
                    if (offered && !value.equals(initial)) {
                        edits.add(new BiomeTuningService.Edit(choice.key(), value.equals(NOT_SET) ? null : value));
                    }
                }
                case EditorView.TextBox _ -> {
                    // the colour picker and the JSON editor read their own text boxes
                }
            }
        }
        return edits;
    }

    /** A slider value on its step grid, as plain decimal text: 0.35000002 becomes "0.35", 231.0 becomes "231". */
    static String number(float value, float step) {
        return number(value, step, 1);
    }

    /**
     * A slider value on its step grid, as plain decimal text in the value's own units: the slider shows it times
     * {@code scale}, so 85 on a slider with scale 100 becomes "0.85". The scale must be a power of ten (1 or 100
     * today): with 3, a third would be written as "0.3333333333333333".
     */
    static String number(float value, float step, float scale) {
        BigDecimal steps = BigDecimal.valueOf(Math.round(value / step));
        return steps.multiply(new BigDecimal(Float.toString(step)))
                .divide(new BigDecimal(Float.toString(scale)), MathContext.DECIMAL64)
                .stripTrailingZeros().toPlainString();
    }

    /**
     * Where a slider starts: on its step grid and inside its range (Paper refuses a start outside the range). It is
     * computed in float on purpose, the way the client computes each step from it, so its steps land exactly on 0.
     * Do not round it to the nearest decimal for a cleaner label: from a rounded start, the client's float steps can
     * miss 0 by a hair, and the label then shows noise such as "-2.9802322E-8".
     */
    static float initial(float value, float min, float max, float step) {
        float onGrid = Math.round(value / step) * step;
        return Math.max(min, Math.min(max, onGrid));
    }
}
