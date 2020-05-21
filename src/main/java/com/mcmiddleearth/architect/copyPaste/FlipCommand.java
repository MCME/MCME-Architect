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
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 *
 * @author Eriol_Eandur
 */
public class FlipCommand extends AbstractArchitectCommand {

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
        char axis;
        //PluginData.getMessageUtil().sendErrorMessage(cs, "Flipping is not yet supported.");
        //if(true) return true;
        if(args.length>0) {
            if(!CopyPasteManager.isAxis(args[0])) {
                PluginData.getMessageUtil().sendErrorMessage(cs, "Possible axis are x, y and z.");
                return true;
            }
            axis = args[0].charAt(0);
        } else {
            Location loc = player.getLocation();
            float yaw = loc.getYaw();
            while(yaw<-180) yaw+=360;
            while(yaw>180) yaw-=360;
            if(loc.getPitch()>45 || loc.getPitch()<-45) {
                axis = 'y';
            } else if(Math.abs(yaw)<135 && Math.abs(yaw)>45) {
                axis = 'x';
            } else {
                axis = 'z';
            }
        }
        CopyPasteManager.flipClipboard(player, axis);
        PluginData.getMessageUtil().sendInfoMessage(cs, "Your clipboard was flipped.");
        return true;
    }
    
    @Override
    public String getHelpPermission() {
        return Permission.COPY_PASTE.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": Flip your clipboard.";
    }

    @Override
    public String getUsageDescription() {
        return " [axis]: Flip your clipboard along x- y- or z-axis.";
    }
    
    @Override
    public String getHelpCommand() {
        return "/flip";
    }
    
    @Override
    protected void sendHelpMessage(Player player, int page) {
        helpHeader = "Help for "+PluginData.getMessageUtil().STRESSED+"flip command -";
        help = new String[][]{{"Flip your clipboard along x- y- or z-axis."}};
        super.sendHelpMessage(player, page);
    }

}
