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
package com.mcmiddleearth.architect.chunkUpdate;

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.additionalCommands.AbstractArchitectCommand;
import com.mcmiddleearth.pluginutil.NMSUtil;
import com.mcmiddleearth.pluginutil.NumericUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 *
 * @author Eriol_Eandur
 */
public class ChunkUpdateCommand extends AbstractArchitectCommand {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            PluginData.getMessageUtil().sendPlayerOnlyCommandError(sender);
            return true;
        }
        Player player = (Player)sender;
        if(!PluginData.hasPermission(player, Permission.CUNK_UPDATE)) {
            PluginData.getMessageUtil().sendNoPermissionError(sender);
            return true;
        }
        if(!PluginData.isModuleEnabled(player.getWorld(), Modules.CHUNK_UPDATE)) {
            sendNotEnabledErrorMessage(sender);
            return true;
        }
        if(args.length>0 && args[0].equalsIgnoreCase("auto")) {
            if(!PluginData.hasPermission(player, Permission.CUNK_UPDATE_AUTO)) {
                PluginData.getMessageUtil().sendNoPermissionError(sender);
                return true;
            }
            if(args.length<2) {
                PluginData.getMessageUtil().sendNotEnoughArgumentsError(sender);
                return true;
            }
            if(!args[1].equals(PluginData.getDefaultKey()) && (Bukkit.getWorld(args[1]) == null)) {
                sendWorldNotFoundMessage((Player)sender);
                return true;
            }
            boolean enable = false;
            if(args.length>2) {
                enable = args[2].equalsIgnoreCase("true");
            }
            PluginData.setModuleEnabled(Bukkit.getWorld(args[1]), Modules.CHUNK_UPDATE_AUTO, enable);
            sendAutoChunkUpdateMessage((Player)sender,enable);
            return true;
        }
        int radius = 20;
        if(args.length>0 && NumericUtil.isInt(args[0])) {
            radius = Math.min(NumericUtil.getInt(args[0]),Bukkit.getServer().getViewDistance()*16);
        }
        Location low = ((Player) sender).getLocation().add(new Vector(-radius, 0 , -radius));
        Location high = ((Player) sender).getLocation().add(new Vector(radius, 0 , radius));
        //NMSUtil.updatePlayerChunks(low,high);
        sendChunkUpdateMessage((Player)sender);
        return true;
    }

    private void sendNotEnabledErrorMessage(CommandSender sender) {
        PluginData.getMessageUtil().sendErrorMessage(sender,"Chunk updates is not enabled for this world.");
    }

    private void sendChunkUpdateMessage(Player p) {
        PluginData.getMessageUtil().sendInfoMessage(p,"Chunk updates have been queued.");
    }

    private void sendAutoChunkUpdateMessage(Player p, boolean enable) {
        PluginData.getMessageUtil().sendInfoMessage(p,"Automatic Chunk updates have been "
                                                       +(enable?"enabled":"disabled")+".");
    }

    @Override
    public String getHelpPermission() {
        return Permission.CUNK_UPDATE.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": Updates chunks around you.";
    }

    @Override
    public String getUsageDescription() {
        return ": Resends the chunks around you to all players in area. Use to remove client side glitches.";
    }
    
    private void sendWorldNotFoundMessage(Player p) {
        PluginData.getMessageUtil().sendErrorMessage(p, "You must specify a valid world name or '-default'.");
    }
    
}
