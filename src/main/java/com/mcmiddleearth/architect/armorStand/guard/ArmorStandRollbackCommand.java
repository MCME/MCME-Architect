/*
 * Copyright (C) 2016 MCME
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
package com.mcmiddleearth.architect.armorStand.guard;

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import static com.mcmiddleearth.architect.armorStand.ArmorStandEditorCommand.getPlayerConfig;
import com.mcmiddleearth.architect.armorStand.ArmorStandEditorConfig;
import com.mcmiddleearth.architect.armorStand.ArmorStandEditorMode;
import com.mcmiddleearth.pluginutil.NumericUtil;
import com.mcmiddleearth.pluginutil.message.MessageUtil;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 *
 * @author Eriol_Eandur
 */
public class ArmorStandRollbackCommand {
    
    public static boolean execute(Player player, String[] args, Map<UUID, ArmorStandEditorConfig> configList) {
        if(!PluginData.hasPermission(player,Permission.ARMOR_STAND_ROLLBACK)) {
            PluginData.getMessageUtil().sendNoPermissionError(player);
            return true;
        }
        if(!PluginData.isModuleEnabled((player).getWorld(), Modules.ARMOR_STAND_ROLLBACK)) {
            sendNotActivatedMessage(player);
            return true;
        }
        if(args.length==1) {
            ArmorStandEditorConfig playerConfig =  getPlayerConfig(player);
            playerConfig.setEditorMode(ArmorStandEditorMode.ROLLBACK);
            return true;
        }
        if(args.length==2 && NumericUtil.isInt(args[1])) {
            ArmorStandGuard.startShpereRollback(player, player.getLocation(), NumericUtil.getInt(args[1]));
        }
        AbstractRollback rollback = ArmorStandGuard.getRollback(player);
        if(rollback == null) {
            sendNoCurrentRollbackMessage(player);
            return true;
        }
        if(args[1].equalsIgnoreCase("previous")) {
            if(rollback.previous()) {
                sendPreviousMessage(player);
            } else {
                sendNoPreviousFoundMessage(player);
            }
            return true;
        }
        if(args[1].equalsIgnoreCase("next")) {
            if(rollback.next()) {
                sendNextMessage(player);
            } else {
                sendNoNextFoundMessage(player);
            }
            return true;
        }
        if(args[1].equalsIgnoreCase("end")) {
            ArmorStandGuard.removeRollback(player);
            return true;
        }
        try {
            Date rollbackTime = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).parse(args[1]);
            rollback.setTime(rollbackTime.getTime());
        } catch (ParseException ex) {
            sentNoValidTimeMessage();
        }
        sendInvalidArgumentsMessage(player);
        return true;
    }

    private static void sendInvalidArgumentsMessage(Player player) {
        PluginData.getMessageUtil().sendErrorMessage(player, "Invalid arguments.");
    }

    private static void sendNotActivatedMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "Armor stand rollback is not activated for this world.");
    }

    private static void sendNoCurrentRollbackMessage(Player player) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private static void sendPreviousMessage(Player player) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private static void sendNoPreviousFoundMessage(Player player) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private static void sendNextMessage(Player player) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private static void sendNoNextFoundMessage(Player player) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private static void sentNoValidTimeMessage() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}