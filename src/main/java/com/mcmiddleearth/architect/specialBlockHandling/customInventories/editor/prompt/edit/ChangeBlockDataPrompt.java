package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.edit;

import com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.add.BlockDataPrompt;
import org.bukkit.conversations.ConversationContext;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class ChangeBlockDataPrompt extends BlockDataPrompt {

    public ChangeBlockDataPrompt(String... blockStateKeys) {
        super(blockStateKeys);
    }

    @Override
    public @NotNull String getPromptText(@NotNull ConversationContext conversationContext) {
        return "Current blockData"+getBlockStateKeys()[0]+" is "
                +conversationContext.getSessionData(getBlockStateKeys()[0])+". Left-click a block to use for blockData"+getBlockStateKeys()[0]+". Or type !skip!";
    }

    private static String[] getFixedSet(String[] blockstateKeys) {
        String[] result =  Arrays.copyOf(blockstateKeys, blockstateKeys.length+1);
        result[result.length-1] = "!skip";
        return result;
    }

}
