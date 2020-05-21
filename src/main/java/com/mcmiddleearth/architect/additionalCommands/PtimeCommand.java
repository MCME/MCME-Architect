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

import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.pluginutil.NumericUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 *
 * @author Eriol_Eandur
 */
public class PtimeCommand extends AbstractArchitectCommand {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            PluginData.getMessageUtil().sendPlayerOnlyCommandError(sender);
            return true;
        }
        Player player = (Player)sender;
        if(!PluginData.hasPermission(player, Permission.ARCHITECT_RELOAD)) {
            PluginData.getMessageUtil().sendNoPermissionError(sender);
            return true;
        }
        int speed;
        if(args.length<1) {
            speed = 5;
        } else {
            speed = NumericUtil.getInt(args[0]);
        }
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            player.removePotionEffect(PotionEffectType.SPEED);
            sendOffMessage(player);
            return true;
        } else {
            final PotionEffect effect = new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, speed, false, false);
            player.addPotionEffect(effect);
            sendOnMessage(player);
            return true;
        }
    }

    private void sendNotEnabledErrorMessage(CommandSender sender) {
        PluginData.getMessageUtil().sendErrorMessage(sender,"Full brightness is not enabled for this world.");
    }

    private void sendOffMessage(Player p) {
        PluginData.getMessageUtil().sendInfoMessage(p,"Speed off!");
    }
    
    private void sendOnMessage(Player p) {
        PluginData.getMessageUtil().sendInfoMessage(p,"Speed on!");
    }

    @Override
    public String getHelpPermission() {
        return Permission.FULL_BRIGHTNESS.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": Speed";
    }

    @Override
    public String getUsageDescription() {
        return ": Switches on/off speed.";
    }
    
}
