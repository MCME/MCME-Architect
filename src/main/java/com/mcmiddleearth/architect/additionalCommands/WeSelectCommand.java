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

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.specialBlockHandling.listener.BlockPickerListener;
import com.mcmiddleearth.pluginutil.NumericUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 *
 * @author Eriol_Eandur
 */
public class WeSelectCommand extends AbstractArchitectCommand {

    private final static Map<UUID,String> weSelect = new HashMap<>();
    private final static Map<UUID,String> weSelectShift = new HashMap<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            PluginData.getMessageUtil().sendPlayerOnlyCommandError(sender);
            return true;
        }
        Player player = (Player)sender;
        if(PluginData.isModuleEnabled(player.getWorld(), Modules.SPECIAL_BLOCKS_FLINT))
        {
            if(!PluginData.hasPermission(player, Permission.ARCHITECT_WESELECT)) {
                PluginData.getMessageUtil().sendNoPermissionError(sender);
                return true;
            }
            UUID uuid = player.getUniqueId();

            if (!weSelect.containsKey(player)) {
                weSelect.put(uuid,"");
            }
            if(!weSelectShift.containsKey(player)) {
            weSelectShift.put(uuid, "");
            }

            if(args[0].equalsIgnoreCase("reset")){
                weSelect.replace(uuid,"");
                weSelectShift.replace(uuid,"");
            } else if(args[0].equalsIgnoreCase("show")){
                // send Message
            } else if(args[0].equalsIgnoreCase("shift")) {
                if(args.length > 2){
                    String args_added = "";
                    for(int i = 1; i < args.length;i++) args_added = args_added + " " + args[i];
                    args_added = args_added.substring(1);
                    weSelectShift.replace(uuid,args_added);
                }else weSelectShift.replace(uuid,args[0]);
            } else {
                if(args.length > 1){
                    String args_added = "";
                    for(int i = 0;i < args.length; i++) args_added = args_added + " " + args[i];
                    args_added = args_added.substring(1);
                    weSelect.replace(uuid,args_added);
                }else weSelect.replace(uuid,args[0]);
            }
        }
        sendNotEnabledErrorMessage(player);
        return true;
    }

    public static String getWeSelect(UUID player, boolean shift){
        if (!weSelect.containsKey(player)) {
            weSelect.put(player,"");
        }
        if(!weSelectShift.containsKey(player)){
            weSelectShift.put(player,"");
        }

        if(shift) return weSelectShift.get(player);
        else return weSelect.get(player);
    }

    private void sendNotEnabledErrorMessage(CommandSender sender) {
        PluginData.getMessageUtil().sendErrorMessage(sender,"WE block data selection is not enabled for this world.");
    }

    private void sendStringReset(CommandSender cs){

    }

    private void sendShow(CommandSender cs,String shift, String no_sneaking){

    }

    private void sendStringSet(CommandSender cs,boolean shift){

    }

    @Override
    public String getHelpPermission() {
        return Permission.ARCHITECT_WESELECT.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": ....";
    }

    @Override
    public String getUsageDescription() {
        return ": ....";
    }
    
}
