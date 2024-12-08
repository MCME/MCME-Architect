package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.edit;

import com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.add.BlockDataPrompt;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;

public class ChangeBlockDataPrompt extends BlockDataPrompt {

    public ChangeBlockDataPrompt(String... blockStateKeys) {
        super(blockStateKeys);
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull String getPromptText(@NotNull ConversationContext conversationContext) {
        Map<String,String> blockData = (Map<String,String>) conversationContext.getSessionData("blockData");
        assert blockData != null;
        return "Current blockData"+getBlockStateKeys()[0]+" is "
                +blockData.get(getBlockStateKeys()[0])+". Left-click a block to use for blockData"+getBlockStateKeys()[0]
                +". Or type '!skip'. You may also type in valid block data.";
    }

    @Override
    protected boolean isInputValid(@NotNull ConversationContext conversationContext, @NotNull String input) {
        return input.equalsIgnoreCase("!skip") || super.isInputValid(conversationContext, input);
    }

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext conversationContext, @NotNull String input) {
        if (!input.equalsIgnoreCase("!skip")) {
            super.acceptValidatedInput(conversationContext, input);
        }
        if(getBlockStateKeys().length>1) {
            return new ChangeBlockDataPrompt(Arrays.copyOfRange(getBlockStateKeys(),1,getBlockStateKeys().length));
        } else {
            return new ChangeItemPrompt();
        }
    }


}
