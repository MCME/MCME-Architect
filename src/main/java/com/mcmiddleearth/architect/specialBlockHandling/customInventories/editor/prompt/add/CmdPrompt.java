package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.add;

import com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.edit.ChangeColorPrompt;
import com.mcmiddleearth.pluginutil.NumericUtil;
import org.bukkit.Material;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.ValidatingPrompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

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
        Object itemMaterial = conversationContext.getSessionData("itemMaterial");
        String name = "";
        if(itemMaterial instanceof Material material) {
            name = material.name();
        } else {
            name = (itemMaterial!=null?(String) name:"");
        }
        if(name.startsWith("LEATHER")) {
            return new ColorPrompt();
        } else {
            return new DisplayPrompt();
        }
    }

    @Override
    public @NotNull String getPromptText(@NotNull ConversationContext conversationContext) {
        return "Current custom model data is "+conversationContext.getSessionData("cmd")
                +". Type in a new custom model data or '!skip'";
    }

    @Override
    protected @Nullable String getFailedValidationText(@NotNull ConversationContext context, @NotNull String invalidInput) {
        return "You need to type in a not negative integer or '!skip'.";
    }
}
