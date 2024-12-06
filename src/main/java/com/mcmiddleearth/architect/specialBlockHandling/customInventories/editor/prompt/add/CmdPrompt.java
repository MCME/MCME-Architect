package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.add;

import com.mcmiddleearth.pluginutil.NumericUtil;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.ValidatingPrompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CmdPrompt extends ValidatingPrompt {

    @Override
    protected boolean isInputValid(@NotNull ConversationContext conversationContext, @NotNull String input) {
        return input.equalsIgnoreCase("!skip")
                || NumericUtil.isInt(input) && NumericUtil.getInt(input)>-1;
    }

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext conversationContext, @NotNull String input) {
        if(!input.equalsIgnoreCase("!skip")) {
            conversationContext.setSessionData("cmd",NumericUtil.getInt(input));
        }
        return new ColorPrompt(); //todo: only for leather
    }

    @Override
    public @NotNull String getPromptText(@NotNull ConversationContext conversationContext) {
        return "Type in a value for custom model data or '!skip'"; //todo prompt current cmd
    }

    @Override
    protected @Nullable String getFailedValidationText(@NotNull ConversationContext context, @NotNull String invalidInput) {
        return "You need to type in a not negative integer or '!skip'.";
    }
}
