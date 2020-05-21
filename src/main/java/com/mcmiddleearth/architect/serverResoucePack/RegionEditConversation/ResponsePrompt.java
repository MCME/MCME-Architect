/*
 * Copyright (C) 2018 Eriol_Eandur
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
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.MessagePrompt;
import org.bukkit.conversations.Prompt;

/**
 *
 * @author Eriol_Eandur
 */
public class ResponsePrompt extends MessagePrompt {
    
    private final String response;
    
    public ResponsePrompt(String response) {
        this.response = response;
    }

    @Override
    protected Prompt getNextPrompt(ConversationContext cc) {
        return new RegionEditPrompt();
    }

    @Override
    public String getPromptText(ConversationContext cc) {
        return PluginData.getMessageUtil().STRESSED+response;
    }
}
