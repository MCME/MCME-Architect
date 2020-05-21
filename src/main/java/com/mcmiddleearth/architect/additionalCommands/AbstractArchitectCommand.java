/*
 * Copyright (C) 2016 MCME
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
package com.mcmiddleearth.architect.additionalCommands;

import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.pluginutil.message.FancyMessage;
import com.mcmiddleearth.pluginutil.message.MessageType;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 *
 * @author Eriol_Eandur
 */
public abstract class AbstractArchitectCommand implements TabExecutor{

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                               Command command,
                                               String alias,
                                               String[] args) {
        return new ArrayList<>();
    }
    
    public abstract String getHelpPermission();

    public abstract String getShortDescription();

    public abstract String getUsageDescription();

    public String getHelpCommand() {
        return null;
    }
    
    protected String helpHeader = "This is a template and should be overridden.";
    protected String[][] help = new String[][]{{"command"," arguments",": short description"," long description (tooltip)"}};
    
    protected void sendHelpMessage(Player player, int page) {
        List<FancyMessage> helpList = new ArrayList<>();
        FancyMessage header = new FancyMessage(MessageType.INFO,PluginData.getMessageUtil())
                                        .addSimple(helpHeader);
        for(String[] line: help) {
            String hoverText = line[2].substring(2);
            if(line.length>3) {
                hoverText = hoverText + line[3];
            }
            helpList.add(new FancyMessage(MessageType.WHITE,PluginData.getMessageUtil())
                .addFancy(ChatColor.DARK_AQUA+line[0]+line[1]
                         +ChatColor.WHITE+line[2],
                            line[0], 
                            PluginData.getMessageUtil()
                                    .hoverFormat(line[0]+line[1]+": "
                                                +hoverText
                                                +" \n "+ChatColor.WHITE+"Click to use.",": ",true)));
        }
        PluginData.getMessageUtil().sendFancyListMessage(player, header, helpList, 
                                                         getHelpCommand(), page);
    }
    
}
