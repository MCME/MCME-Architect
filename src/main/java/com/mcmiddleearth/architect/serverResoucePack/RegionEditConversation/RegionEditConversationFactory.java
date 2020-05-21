/*
 * Copyright (C) 2018 MCME
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.mcmiddleearth.architect.serverResoucePack.RegionEditConversation;

import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.serverResoucePack.RpRegion;
import com.mcmiddleearth.pluginutil.confirmation.ConfirmationPrompt;
import com.mcmiddleearth.pluginutil.confirmation.Confirmationable;
import org.bukkit.ChatColor;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.conversations.ConversationAbandonedListener;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.conversations.ConversationPrefix;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 *
 * @author Eriol_Eandur
 */
public class RegionEditConversationFactory{
    
    private final ConversationFactory factory;
    
    public RegionEditConversationFactory(Plugin plugin){
        factory = new ConversationFactory(plugin)
                .withModality(false)
                .withPrefix(new ConversationPrefix(){
                    @Override
                    public String getPrefix(ConversationContext cc) {
                        return ChatColor.GOLD+PluginData.getMessageUtil().getPREFIX();
                    }
                })
                .withFirstPrompt(new RegionEditPrompt())
                .addConversationAbandonedListener(new RegionEditPrompt())
                .withTimeout(120)
                .withLocalEcho(true);
    }
    
    public void start(Player player, RpRegion region) {
        Conversation conversation = factory.buildConversation(player);
        ConversationContext context = conversation.getContext();
        context.setSessionData("player", player);
        context.setSessionData("region", region);
        conversation.begin();
    }
   
}
