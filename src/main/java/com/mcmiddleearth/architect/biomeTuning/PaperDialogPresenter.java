package com.mcmiddleearth.architect.biomeTuning;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.type.MultiActionType;
import java.time.Duration;
import java.util.List;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

/**
 * Shows editor windows as Paper Dialogs. A click leaves the window open (after-action NONE) until the
 * server shows the next one or closes it, so the client's "waiting for response" screen never appears. The footer
 * button is the window's exit: the client sends it for Escape too, so the editor hears when a builder closes a
 * window. A death or a world change closes it without a word, and the editor forgets it then. Each button's callback
 * is good for one use, and for a minute longer than the editor keeps a window open, so the editor closes a window
 * before its buttons stop working.
 */
final class PaperDialogPresenter implements DialogPresenter {

    private static final int WIDTH = 300;
    /** The client pads body text 4 px a side, so this gives it a text area exactly as wide as the sliders. */
    private static final int BODY_WIDTH = WIDTH + 8;
    /** Two columns of these buttons are as wide as the sliders. The footer's Close has this width in every window. */
    private static final int BUTTON_WIDTH = 150;
    /** For three columns: 96 px for the label, and "Water & vegetation" is 94 px in the stock font. */
    private static final int NARROW_BUTTON_WIDTH = 100;
    private static final int TEXT_HEIGHT = 160;
    private static final ClickCallback.Options ONE_USE = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMillis(BiomeEditor.WINDOW_LIFETIME_MILLIS).plusMinutes(1))
            .build();

    @Override
    public void show(Player player, EditorView view) {
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(view.title())
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(view.body().isEmpty() ? List.of()
                                : List.of(DialogBody.plainMessage(view.text(), BODY_WIDTH)))
                        .inputs(view.inputs().stream().map(PaperDialogPresenter::input).toList())
                        .build())
                .type(type(view)));
        player.showDialog(dialog);
    }

    @Override
    public void close(Player player) {
        player.closeDialog();
    }

    private static MultiActionType type(EditorView view) {
        int width = switch (view.columns()) {
            case 2 -> BUTTON_WIDTH;
            case 3 -> NARROW_BUTTON_WIDTH;
            default -> throw new IllegalArgumentException("no button width for " + view.columns() + " columns");
        };
        MultiActionType.Builder type = DialogType.multiAction(view.buttons().stream()
                        .map(b -> button(b, width)).toList())
                .columns(view.columns());
        if (view.exit() != null) {
            type.exitAction(button(view.exit(), BUTTON_WIDTH));
        }
        return type.build();
    }

    private static DialogInput input(EditorView.Input input) {
        return switch (input) {
            case EditorView.Slider slider -> DialogInput.numberRange(slider.key(), slider.label(), slider.min(),
                            slider.max())
                    .step(slider.step())
                    .initial(slider.initial())
                    .labelFormat(slider.format())
                    .width(WIDTH)
                    .build();
            case EditorView.Toggle toggle -> DialogInput.bool(toggle.key(), toggle.label())
                    .initial(toggle.initial())
                    .build();
            case EditorView.Choice choice -> DialogInput.singleOption(choice.key(), choice.label(),
                            choice.options().stream()
                                    .map(option -> SingleOptionDialogInput.OptionEntry.create(option.id(),
                                            option.label(), option.initial()))
                                    .toList())
                    .width(WIDTH)
                    .build();
            case EditorView.TextBox text -> {
                TextDialogInput.Builder builder = DialogInput.text(text.key(), text.label())
                        .initial(text.initial())
                        .maxLength(text.maxLength())
                        .width(WIDTH);
                if (text.multiline()) {
                    builder.multiline(TextDialogInput.MultilineOptions.create(null, TEXT_HEIGHT));
                }
                yield builder.build();
            }
        };
    }

    private static ActionButton button(EditorView.Button button, int width) {
        ActionButton.Builder builder = ActionButton.builder(button.label())
                .width(width)
                .action(DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player player) {
                        button.action().run(player, answers(response));
                    }
                }, ONE_USE));
        if (button.tooltip() != null) {
            builder.tooltip(button.tooltip());
        }
        return builder.build();
    }

    private static EditorView.Answers answers(DialogResponseView response) {
        return new EditorView.Answers() {
            @Override
            public Float number(String key) {
                return response.getFloat(key);
            }

            @Override
            public Boolean flag(String key) {
                return response.getBoolean(key);
            }

            @Override
            public String text(String key) {
                return response.getText(key);
            }
        };
    }
}
