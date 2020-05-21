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
package com.mcmiddleearth.architect.weSchematicsViewer;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.additionalCommands.AbstractArchitectCommand;
import com.mcmiddleearth.pluginutil.FileUtil;
import com.mcmiddleearth.pluginutil.message.FancyMessage;
import com.mcmiddleearth.pluginutil.message.MessageType;
import java.io.File;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 *
 * @author Eriol_Eandur
 */
public class SchListCommand extends AbstractArchitectCommand {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            PluginData.getMessageUtil().sendPlayerOnlyCommandError(sender);
            return true;
        }
        if(!PluginData.hasPermission((Player)sender,Permission.WE_SCHEMATICS_VIEWER)) {
            PluginData.getMessageUtil().sendNoPermissionError(sender);
            return true;
        }
        Player player = (Player) sender;
        if(!PluginData.isModuleEnabled(player.getWorld(), Modules.WE_SCHEMATICS_VIEWER)) {
            sendNotActivatedMessage(player);
            return true;
        }
        File baseDir = new File(ArchitectPlugin.getPluginInstance()
                                 .getDataFolder().getParent() + "/WorldEdit/schematics");
        PluginData.getMessageUtil().sendFancyFileListMessage(player, 
                         new FancyMessage(MessageType.INFO, PluginData.getMessageUtil())
                            .addSimple(PluginData.getMessageUtil().STRESSED+"World Edit Schematics"
                                    + PluginData.getMessageUtil().INFO +" /"), 
                         baseDir, 
                         FileUtil.getFileOnlyFilter(), 
                         args,
                         "/schlist", 
                         null, true);
        return true;
    }

    private void sendNotActivatedMessage(Player player) {
        PluginData.getMessageUtil().sendErrorMessage(player, "World edit schematics viewer is not enabled.");
    }
    
    @Override
    public String getHelpPermission() {
        return Permission.WE_SCHEMATICS_VIEWER.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": World Edit Schematics Viewer.";
    }

    @Override
    public String getUsageDescription() {
        return " [#page]: Lists all WE schematics, you can click at folder names to navigate into them.";
    }
    
    @Override
    public String getHelpCommand() {
        return null;
    }
    
    
}
