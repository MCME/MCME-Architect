package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.edit;

import com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.add.ColorPrompt;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChangeColorPrompt extends ColorPrompt {

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext conversationContext, @NotNull String input) {
        super.acceptValidatedInput(conversationContext, input);
        return new ChangeDisplayPrompt();
    }
}
