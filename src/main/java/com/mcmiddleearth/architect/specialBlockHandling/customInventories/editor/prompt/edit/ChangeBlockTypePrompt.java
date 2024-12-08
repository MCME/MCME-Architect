package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.edit;

import com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.add.BlockTypePrompt;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.FixedSetPrompt;
import org.bukkit.conversations.Prompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class ChangeBlockTypePrompt extends BlockTypePrompt {

    public ChangeBlockTypePrompt() {
        super(getFixedSet());
    }

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext conversationContext, @NotNull String input) {
        if(!input.equalsIgnoreCase("!skip")) {
           conversationContext.setSessionData("type",input);
        }
        String type = (String) conversationContext.getSessionData("type"); assert type != null;
        for (String[] blockDatum : BlockTypePrompt.blockData) {
            if (type.equalsIgnoreCase(blockDatum[0])) {
                String[] blockOrientations = Arrays.copyOfRange(blockDatum, 1, blockDatum.length);
                if(blockOrientations.length == 0) {
                    return new ChangeItemPrompt();
                } else {
                    return new ChangeBlockDataPrompt(blockOrientations);
                }
            }
        }
        return END_OF_CONVERSATION;
    }

    @Override
    public @NotNull String getPromptText(@NotNull ConversationContext conversationContext) {
        return "Current block type is "+conversationContext.getSessionData("type")
                +". Type in a new block type or '!skip' "+formatFixedSet();
    }

    private static String[] getFixedSet() {
        String[] result =  Arrays.copyOf(BlockTypePrompt.getBlockTypes(), BlockTypePrompt.getBlockTypes().length+1);
        result[result.length-1] = "!skip";
        return result;
    }
}
