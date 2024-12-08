package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.add;

import com.mcmiddleearth.pluginutil.NumericUtil;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.ValidatingPrompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ColorPrompt extends ValidatingPrompt {

    @Override
    protected boolean isInputValid(@NotNull ConversationContext conversationContext, @NotNull String input) {
        return input.equalsIgnoreCase("!skip")
                || NumericUtil.isInt(input) && NumericUtil.getInt(input)>-1;
    }

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext conversationContext, @NotNull String input) {
        if(!input.equalsIgnoreCase("!skip")) {
            conversationContext.setSessionData("color", NumericUtil.getInt(input));
        }
        return new DisplayPrompt();
    }

    @Override
    public @NotNull String getPromptText(@NotNull ConversationContext conversationContext) {
        return "Current color is "+conversationContext.getSessionData("color")+". Type in a new color or '!skip'";
    }

    @Override
    protected @Nullable String getFailedValidationText(@NotNull ConversationContext context, @NotNull String invalidInput) {
        return "You need to type in a valid rgb color code.";
    }
}
