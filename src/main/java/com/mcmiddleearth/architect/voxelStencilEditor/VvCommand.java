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
package com.mcmiddleearth.architect.voxelStencilEditor;

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.additionalCommands.AbstractArchitectCommand;
import com.mcmiddleearth.pluginutil.FileUtil;
import com.mcmiddleearth.pluginutil.NumericUtil;
import com.mcmiddleearth.pluginutil.message.FancyMessage;
import com.mcmiddleearth.pluginutil.message.MessageType;
import java.io.File;
import java.util.Arrays;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 *
 * @author Eriol_Eandur
 */
public class VvCommand extends AbstractArchitectCommand {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            PluginData.getMessageUtil().sendPlayerOnlyCommandError(sender);
            return true;
        }
        if(!PluginData.hasPermission((Player)sender,Permission.VOXEL_VIEWER)) {
            PluginData.getMessageUtil().sendNoPermissionError(sender);
            return true;
        }
        Player player = (Player) sender;
        if(!PluginData.isModuleEnabled(player.getWorld(), Modules.VOXEL_VIEWER)) {
            sendNotActivatedMessage(player);
            return true;
        }
        if(args.length<1 || args[0].toLowerCase().startsWith("list")) {
            String[] messageArgs;
            if(args.length<2) {
                messageArgs = new String[0];
            } else {
                messageArgs = Arrays.copyOfRange(args,1,args.length);
            }
            PluginData.getMessageUtil().sendFancyFileListMessage(player, 
                         new FancyMessage(MessageType.INFO, PluginData.getMessageUtil())
                            .addSimple(PluginData.getMessageUtil().HIGHLIGHT_STRESSED+"Stencil Lists"
                                    + PluginData.getMessageUtil().INFO +" /"), 
                         VoxelConstants.STENCIL_LISTS_DIR, 
                         FileUtil.getFileExtFilter(VoxelConstants.STENCIL_LIST_EXT), 
                         messageArgs,
                         "/vv list", 
                         "/b sl", true);
        } else if(args[0].toLowerCase().startsWith("stencil")) {
            PluginData.getMessageUtil().sendFancyFileListMessage(player, 
                         new FancyMessage(MessageType.INFO, PluginData.getMessageUtil())
                            .addSimple(PluginData.getMessageUtil().STRESSED+"Voxel Stencils"
                                    + PluginData.getMessageUtil().INFO +" /"), 
                         VoxelConstants.STENCILS_DIR, 
                         FileUtil.getFileExtFilter(VoxelConstants.STENCIL_EXT), 
                         Arrays.copyOfRange(args,1,args.length),
                         "/vv stencil", 
                         "/b st", true);
        } else if(args[0].equalsIgnoreCase("delete")) {
            if(!PluginData.hasPermission((Player)sender,Permission.VOXEL_VIEWER_DELETE)) {
                PluginData.getMessageUtil().sendNoPermissionError(sender);
                return true;
            }
            if(args.length<2) {
                PluginData.getMessageUtil().sendNotEnoughArgumentsError(player);
                return true;
            }
            File file;
            if(args[1].endsWith(VoxelConstants.STENCIL_EXT)) {
                file = new File(VoxelConstants.STENCILS_DIR+"/"+args[1]);
            } else {
                file = new File(VoxelConstants.STENCIL_LISTS_DIR+"/"+args[1]);
            }
            if(!file.exists()) {
                PluginData.getMessageUtil().sendErrorMessage(player, "File not found.");
                return true;
            }
            if(file.isDirectory() && file.list().length>0) {
                PluginData.getMessageUtil().sendErrorMessage(player, "You may delete an empty directory only.");
                return true;
            }
            file.delete();
            PluginData.getMessageUtil().sendInfoMessage(player,"File deleted.");
        } else if(args[0].equalsIgnoreCase("help")) {
            int page = 1;
            if(args.length>1 && NumericUtil.isInt(args[1])) {
                page = NumericUtil.getInt(args[1]);
            }
            sendHelpMessage(player,page);
        } else {
            PluginData.getMessageUtil().sendInvalidSubcommandError(player);
        }
        return true;
    }

    private void sendNotActivatedMessage(Player player) {
        PluginData.getMessageUtil().sendErrorMessage(player,"Voxel viewer is not enabled.");
    }

    @Override
    public String getHelpPermission() {
        return Permission.VOXEL_VIEWER.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": Voxel Viewer.";
    }

    @Override
    public String getUsageDescription() {
        return ": Show or delete voxel stencil lists and voxel stencils. \n "
                +ChatColor.WHITE+"Click for detailed help.";
    }
    
    @Override
    public String getHelpCommand() {
        return "/vv help";
    }
    
    @Override
    protected void sendHelpMessage(Player player, int page) {
        helpHeader = "Help for "+PluginData.getMessageUtil().STRESSED+"Voxel Viewer -";
        help = new String[][]{{"/vv stencil ","[directory] [#page]",": Views stencils."},
                                       {"/vv ", "[list] [directory] [#page]",": Views stencil lists."},
                                       {"/vv delete ","<filename>",": Deletes a voxel file. You have to specify the full filename with extension. Extension for stencil lists is '.txt' and for stencils '.vstencil'."}};
        super.sendHelpMessage(player, page);
    }
    
}

