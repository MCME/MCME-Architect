package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.edit;

import com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.add.CmdPrompt;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ChangeCmdPrompt extends CmdPrompt {

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext conversationContext, @NotNull String input) {
        super.acceptValidatedInput(conversationContext, input);
        if(((String) Objects.requireNonNull(conversationContext.getSessionData("itemMaterial"))).startsWith("LEATHER")) {
            return new ChangeColorPrompt();
        } else {
            return new ChangeDisplayPrompt();
        }
    }
}
