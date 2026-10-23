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

import com.mcmiddleearth.architect.Log;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.pluginutil.message.FancyMessage;
import com.mcmiddleearth.pluginutil.message.MessageType;
import com.mcmiddleearth.util.PathSafety;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 *
 * @author Eriol_Eandur
 */
public abstract class AbstractArchitectCommand implements TabExecutor{

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      Command command,
                                      java.lang.String alias,
                                      java.lang.String[] args) {
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
    
    /**
     * Whether the folder a player named for a file list stays inside the list's base folder. The file list
     * reads that folder from {@code listArgs[0]}; a lone page number resolves inside the base. If the folder
     * is outside, the player is told, the refusal is logged and false is returned.
     */
    protected static boolean isListFolderAllowed(CommandSender sender, File baseDir, String[] listArgs) {
        if (listArgs.length == 0 || PathSafety.isInsideFolder(baseDir, listArgs[0])) {
            return true;
        }
        Log.warn("Rejected unsafe folder name '" + listArgs[0] + "' outside " + baseDir);
        PluginData.getMessageUtil().sendErrorMessage(sender, "Invalid folder name. Pick a folder from the list.");
        return false;
    }

}
