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
package com.mcmiddleearth.architect.signEditor;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.additionalCommands.*;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.pluginutil.NumericUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class SignCommand extends AbstractArchitectCommand {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            PluginData.getMessageUtil().sendPlayerOnlyCommandError(sender);
            return true;
        }
        if(!PluginData.hasPermission(player, Permission.SIGN_EDITOR)) {
            PluginData.getMessageUtil().sendNoPermissionError(sender);
            return true;
        }
        if(!PluginData.isModuleEnabled(player.getWorld(), Modules.SIGN_EDITOR)) {
            sendNotEnabledErrorMessage(sender);
            return true;
        }
        if(args.length<2) {
            PluginData.getMessageUtil().sendNotEnoughArgumentsError(sender);
            return true;
        }
        if(args[0].equalsIgnoreCase("glow")) {
            SignEditorData.editSignGlow(player, Boolean.parseBoolean(args[1]));
            return true;
        }
        if(!NumericUtil.isInt(args[0])) {
            sendInvalidArgument(sender);
            return true;
        }
        int lineNumber = NumericUtil.getInt(args[0]);
        if(lineNumber<1||lineNumber>4) {
            sendWrongLineNumberMessage(player);
        }
        String line = args[1];
        for(int i=2;i<args.length;i++) {
            line = line+" "+args[i];
        }
//Logger.getGlobal().info("SignEditor line: "+line);
        line = line.replace("\\_"," ");
        if(SignEditorData.formattedLength(line, '&',"")>SignEditorData.getRowLength(player)) {
            sendLineTooLong(sender);
        }
//Logger.getGlobal().info("SignEditor line: "+line);
        SignEditorData.editSign(player, lineNumber,line);
        Bukkit.getScheduler().runTaskLater(ArchitectPlugin.getPluginInstance(), new Runnable() {
            @Override
            public void run() {
                SignEditorData.sendSignMessage(player);
            }
        },1);
        return true;
    }

    private void sendNoSignSelected(Player player) {

    }

    private void sendInvalidGlowArgument(Player player) {
    }

    private void sendGlowDisable(Player player) {
    }

    private void sendGlowEnable(Player player) {
    }

    private void sendNotEnabledErrorMessage(CommandSender sender) {
        PluginData.getMessageUtil().sendErrorMessage(sender,"Sign Editor is not enabled for this world.");
    }

    private void sendInvalidArgument(CommandSender sender) {
        PluginData.getMessageUtil().sendErrorMessage(sender,"First argument must be a number.");
    }

    private void sendLineTooLong(CommandSender sender) {
        PluginData.getMessageUtil().sendErrorMessage(sender,"You typed in many characters. Line might get truncated depending on letter width.");
    }

    private void sendWrongLineNumberMessage(Player player) {
        PluginData.getMessageUtil().sendErrorMessage(player,"Line number must be between 1 and 4.");
    }

    @Override
    public String getHelpPermission() {
        return Permission.SIGN_EDITOR.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return " <#line>: Edit signs.";
    }

    @Override
    public String getUsageDescription() {
        return " <#line>: Edits a line of a sign. You need to right-click the sign with a stick first.";
    }

    
}
