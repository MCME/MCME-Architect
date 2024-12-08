package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.add;

import org.bukkit.Material;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.FixedSetPrompt;
import org.bukkit.conversations.Prompt;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ItemPrompt extends FixedSetPrompt {

    public ItemPrompt() {
        super("ok");
    }

    protected ItemPrompt(String... fixedSet) {
        super(fixedSet);
    }

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext conversationContext, @NotNull String input) {
        ItemStack item = ((Player)conversationContext.getForWhom()).getInventory().getItemInMainHand();
        conversationContext.setSessionData("itemMaterial", item.getType().name());
        if(item.hasItemMeta() && item.getItemMeta().hasCustomModelData()) {
            conversationContext.setSessionData("cmd", item.getItemMeta().getCustomModelData());
        }
        if(item.getType().name().startsWith("LEATHER")) {
            LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
            conversationContext.setSessionData("color", meta.getColor().asRGB());
        }
        return new CmdPrompt();
    }

    @Override
    protected boolean isInputValid(@NotNull ConversationContext context, @NotNull String input) {
        return !((Player)context.getForWhom()).getInventory().getItemInMainHand().getType().equals(Material.AIR)
              && super.isInputValid(context, input);

    }

    @Override
    public @NotNull String getPromptText(@NotNull ConversationContext conversationContext) {
        return "Hold the item you want to add to custom inventory in main hand and type 'ok'.";
    }

}
