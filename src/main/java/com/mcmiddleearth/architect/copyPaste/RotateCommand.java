/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.copyPaste;

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.additionalCommands.AbstractArchitectCommand;
import com.mcmiddleearth.pluginutil.NumericUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 *
 * @author Eriol_Eandur
 */
public class RotateCommand extends AbstractArchitectCommand {

    @Override
    public boolean onCommand(CommandSender cs, Command cmd, String c, String[] args) {
        if (!(cs instanceof Player)) {
            PluginData.getMessageUtil().sendPlayerOnlyCommandError(cs);
            return true;
        }
        Player player = (Player) cs;
        if(!PluginData.isModuleEnabled(player.getWorld(), Modules.COPY_PASTE)) {
            PluginData.getMessageUtil().sendErrorMessage(cs,"CopyPasting is not enabled for this world.");
            return true;
        }
        if(!PluginData.hasPermission(cs, Permission.COPY_PASTE)) {
            PluginData.getMessageUtil().sendNoPermissionError(cs);
            return true;
        }
        if(!CopyPasteManager.hasClipboard(player)) {
            PluginData.getMessageUtil().sendErrorMessage(cs, "Copy something to your clipboard first with /copy.");
            return true;
        }
        int degree = 90;
        if(args.length>0 && NumericUtil.isInt(args[0])) {
            degree = NumericUtil.getInt(args[0]);
        }
        CopyPasteManager.rotateClipboard(player, degree);
        PluginData.getMessageUtil().sendInfoMessage(cs, "Your clipboard was rotated.");
        return true;
    }
    
    @Override
    public String getHelpPermission() {
        return Permission.COPY_PASTE.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": Rotate your clipboard.";
    }

    @Override
    public String getUsageDescription() {
        return " [degree]: Rotate your clipboard in steps of 90 degree.";
    }
    
    @Override
    public String getHelpCommand() {
        return "/rotate";
    }
    
    @Override
    protected void sendHelpMessage(Player player, int page) {
        helpHeader = "Help for "+PluginData.getMessageUtil().STRESSED+"Rotate command -";
        help = new String[][]{{"Rotate your clipboard in steps of 90 degree."}};
        super.sendHelpMessage(player, page);
    }

}
