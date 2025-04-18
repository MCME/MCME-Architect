/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.displayEntity;

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.additionalCommands.AbstractArchitectCommand;
import com.mcmiddleearth.pluginutil.FileUtil;
import com.mcmiddleearth.pluginutil.NumericUtil;
import com.mcmiddleearth.pluginutil.message.FancyMessage;
import com.mcmiddleearth.pluginutil.message.MessageType;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.util.*;

/**
 *
 * @author Eriol_Eandur
 */
public class DisplayEntityEditorCommand extends AbstractArchitectCommand {

    private final static Map<UUID, DisplayEntityEditorConfig> configList = new HashMap<>();
    
    private final int maxStepSize = 360;
    
    @Override
    public boolean onCommand(CommandSender cs, Command cmd, String c, String[] args) {
        if (!(cs instanceof Player)) {
            PluginData.getMessageUtil().sendPlayerOnlyCommandError(cs);
            return true;
        }
        if(!PluginData.hasPermission(cs,Permission.DISPLAY_ENTITY_EDITOR)) {
            PluginData.getMessageUtil().sendNoPermissionError(cs);
            return true;
        } else {
            Player p = (Player) cs;
            if(!PluginData.isModuleEnabled(p.getWorld(), Modules.DISPLAY_ENTITY_EDITOR)) {
                sendNotActivatedMessage(cs);
                return true;
            }
            DisplayEntityEditorConfig playerConfig =  getPlayerConfig(p);
            if(args.length<1) {
                sendInfoMessage(cs, playerConfig);
            }
            else {
                if(args[0].equalsIgnoreCase("help")) {
                    int page = 1;
                    if(args.length>1 && NumericUtil.isInt(args[1])) {
                        page = NumericUtil.getInt(args[1]);
                    }
                    sendHelpMessage((Player) cs,page);
                    return true;
                }
                if(args[0].equals("+") || args[0].equals("-")) {
                    int stepInDegree = playerConfig.getRotationStep();
                    if(args[0].equals("+") && stepInDegree<maxStepSize) {
                        stepInDegree += 1;
                    }
                    else if(args[0].equals("-") && stepInDegree>2) {
                        stepInDegree -= 1;
                    }
                    playerConfig.setRotationStep(stepInDegree);
                    sendRotationStepMessage(cs,playerConfig.getRotationStep());
                    return true;
                }
                if(args[0].equalsIgnoreCase("default")) {
                    playerConfig.setRotationStep(10);
                    sendStepSizeDefaultMessage(cs);
                    return true;
                }
                if(args[0].equalsIgnoreCase("clear")) {
                    playerConfig.clearCopiedDisplayEntities();
                    sendCopiedArmorStandClearedMessage(cs);
                    return true;
                }
                if(args[0].equalsIgnoreCase("files")) {
                    PluginData.getMessageUtil().sendFancyFileListMessage(p,
                                    new FancyMessage(MessageType.INFO, PluginData.getMessageUtil())
                                            .addSimple(PluginData.getMessageUtil().STRESSED+"Display entity "
                                                      +PluginData.getMessageUtil().INFO+"files /"),
                                    DisplayEntityEditorConfig.getDataDir(),
                                    FileUtil.getFileExtFilter(DisplayEntityEditorConfig.getFileExtension()),
                                    Arrays.copyOfRange(args, 1, args.length), 
                                    "/display files",
                                    "/display p",
                                    true);
                    return true;
                }
                if(!PluginData.hasPermission(p, Permission.DISPLAY_ENTITY_EDITOR_TRUSTED)
                        && (args[0].equalsIgnoreCase("place")
                          ||args[0].equalsIgnoreCase("delete")
                          ||args[0].equalsIgnoreCase("rename")
                          ||args[0].equalsIgnoreCase("save"))) {
                    PluginData.getMessageUtil().sendNoPermissionError(cs);
                    return true;
                }
                if(args[0].equalsIgnoreCase("paste")) {
                    Vector loc = p.getLocation().toVector();
                    loc = loc.add(playerConfig.getCopiedEntityRelativeLoc());
                    playerConfig.placeDisplayEntity(loc.toLocation(p.getWorld()),false);
                    return true;
                }
                if(args[0].equalsIgnoreCase("place2") && PluginData.hasPermission(p,Permission.RANDOMISER_MATERIALS)) {
                    for(int i = 0 ; i < NumericUtil.getInt(args[1]);i+=NumericUtil.getInt(args[2])) {
                        for( int j = 0; j< NumericUtil.getInt(args[1]);j+=NumericUtil.getInt(args[2])) {
                            playerConfig.placeDisplayEntity(new Location(p.getWorld(),
                                                                      p.getLocation().getBlockX()+i,
                                                                      p.getLocation().getBlockY(),
                                                                      p.getLocation().getBlockZ()+j),true);
                    }
                    }
                    return true;
                }
                if(args[0].equalsIgnoreCase("delete")) {
                    if(args.length>1) {
                        if(!playerConfig.existsFile(args[1])) {
                            PluginData.getMessageUtil().sendFileNotFoundError(cs);
                            return true;
                        }
                        if(!(PluginData.hasPermission(p, Permission.DISPLAY_ENTITY_EDITOR_DELETE)
                                || playerConfig.isCreator(args[1], p.getUniqueId()))) {
                            PluginData.getMessageUtil().sendNoPermissionError(cs);
                            return true;
                        }
                        if(playerConfig.deleteFile(args[1])) {
                            sendFileDeletedMessage(cs);
                        }
                        else {
                            sendDeleteErrorMessage(cs);
                        }
                    } else {
                        PluginData.getMessageUtil().sendNotEnoughArgumentsError(cs);
                    }
                    return true;
                }
                if(args[0].equalsIgnoreCase("rename")) {
                    if(args.length>2) {
                        if(!playerConfig.existsFile(args[1])) {
                            PluginData.getMessageUtil().sendFileNotFoundError(cs);
                            return true;
                        }
                        if(!(PluginData.hasPermission(p, Permission.DISPLAY_ENTITY_EDITOR_DELETE)
                                || playerConfig.isCreator(args[1], p.getUniqueId()))) {
                            PluginData.getMessageUtil().sendNoPermissionError(cs);
                            return true;
                        }
                        if(playerConfig.renameFile(args[1],args[2])) {
                            sendFileRenamedMessage(cs);
                        }
                        else {
                            sendRenameErrorMessage(cs);
                        }
                    } else {
                        PluginData.getMessageUtil().sendNotEnoughArgumentsError(cs);
                    }
                    return true;
                }
                if(args[0].equalsIgnoreCase("save")) {
                    if(!playerConfig.hasCopiedDisplayEntity()) {
                        sendCopyFirstMessage(cs);
                        return true;
                    }
                    if(args.length>2) {
                        String description = args[2];
                        for(int i = 3;i<args.length;i++) {
                            description = description + " " + args[i];
                        }
                        try {
                            if(playerConfig.saveArmorStand(args[1],description, p.getUniqueId())) {
                                sendSavedMessage(cs);
                            }
                            else {
                                sendExistsMessage(cs);
                            }
                        } catch (IOException ex) {
                            sendIOErrorMessage(cs);
                        }
                    }
                    else
                    {
                        sendNotEnoughArgumentsMessage(cs);
                    }
                    return true;
                }
                try {
                    int step = Integer.parseInt(args[0]);
                    if(step>maxStepSize) step = maxStepSize;
                    if(step<1) step = 1;
                    playerConfig.setRotationStep(step);
                    sendRotationStepMessage(cs,playerConfig.getRotationStep());
                    return true;
                }
                catch(NumberFormatException e) {}
                DisplayEntityEditorMode editorMode = DisplayEntityEditorMode.getEditorMode(args[0]);
                if(editorMode == null) {
                    PluginData.getMessageUtil().sendInvalidSubcommandError(cs);
                }
                else {
                    if(editorMode.equals(DisplayEntityEditorMode.PASTE) && args.length>1) {
                        try {
                            if(playerConfig.loadArmorStand(args[1])) {
                                sendLoadedMessage(cs);
                            }
                            else {
                                PluginData.getMessageUtil().sendFileNotFoundError(cs);
                            }
                        } catch (IOException | InvalidConfigurationException ex) {
                            sendIOErrorMessage(cs);
                        }
                    }
                    playerConfig.setEditorMode(editorMode);
                    sendInfoMessage(cs, playerConfig);
                }
            }
            return true;
        }
    }
    
    public static DisplayEntityEditorConfig getPlayerConfig(Player p) {
        for(UUID search: configList.keySet()) {
            if(search.equals(p.getUniqueId())) {
                return configList.get(search);
            }
        }
        DisplayEntityEditorConfig newConfig = new DisplayEntityEditorConfig(p);
        configList.put(p.getUniqueId(), newConfig);
        return newConfig;
    }
        
    private void sendInfoMessage(CommandSender cs, DisplayEntityEditorConfig playerConfig) {
                    PluginData.getMessageUtil().sendInfoMessage(cs, "Current Display Entity Editor mode: ");
                    switch(playerConfig.getEditorMode()) {
                        case GRAVITY:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> switch gravity");
                            break;
                        case XROTATE:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> rotate " + playerConfig.getPart().getPartName()+" along x-Axis");
                            break;
                        case YROTATE:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> rotate " + playerConfig.getPart().getPartName()+" along y-axis");
                            break;
                        case ZROTATE:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> rotate " + playerConfig.getPart().getPartName()+" along z-axis");
                            break;
                        case ROTATE:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> rotate " + playerConfig.getPart().getPartName()+" along your view direction");
                            break;
                        case MOVE:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> move to left/right");
                            break;
                        case TURN:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> turn full armor stand");
                            break;
                        case XMOVE:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> move along x-axis");
                            break;
                        case YMOVE:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> move along y-axis");
                            break;
                        case ZMOVE:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> move along z-axis");
                            break;
                        case SIZE:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> switch size");
                            break;
                        case SCALE:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> change scale");
                            break;
                        case VISIBLE:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> switch visibility");
                            break;
                        case LOCK:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> switch lock");
                            break;
                        case MARKER:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> switch collision box");
                            break;
                        case PASTE:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> paste armor stand");
                            break;
                        case COPY:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> copy armor stand");
                            break;
                    }
    }

    private void sendRotationStepMessage(CommandSender cs, int rotationStep) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "    -> Set rot/move step to "+rotationStep+" percent/degree");
    }

    private void sendCopiedArmorStandClearedMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "display entity clippboard was cleared.");
    }

    private void sendNotEnoughArgumentsMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "Not enough arguments: /display save <filename> <description>");
    }

    private void sendSavedMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "Display entity saved.");
    }

    private void sendLoadedMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "Display entity loaded.");
    }

    private void sendExistsMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "File already exists. Delete first.");
    }

    private void sendCopyFirstMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "Copy a display entity first. Use '/display c' and click with stick at a display entity.");
    }

    private void sendIOErrorMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "IO error. Nothing was saved.");
    }

    private void sendFileDeletedMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "File deleted.");
    }

    private void sendDeleteErrorMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "File not found or directory not empty.");
    }

    private void sendFileRenamedMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "File renamed.");
    }

    private void sendRenameErrorMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "File could not be renamed.");
    }

    private void sendNotActivatedMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "Display entity editor is not activated for this world.");
    }
        
    private void sendStepSizeDefaultMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "    -> Set move/rot step to 10 percent/degree.");
    }
        
    @Override
    public String getHelpPermission() {
        return Permission.DISPLAY_ENTITY_EDITOR.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": Display Entity Editor.";
    }

    @Override
    public String getUsageDescription() {
        return ": The display entity editor features a number of commands to select properties of a display entity you want to change. The changes are applied to a display entity by clicking at it with a stick in hand. \n "
                +ChatColor.WHITE+"Click for detailed help.";
    }
    
    @Override
    public String getHelpCommand() {
        return "/display help";
    }
    
    @Override
    protected void sendHelpMessage(Player player, int page) {
        List<String[]> helpList = new ArrayList<>();
        helpHeader = "Help for "+PluginData.getMessageUtil().STRESSED+"Display Entity Editor -";
        help = new String[][]{{"/display place","",": Places copied display entities."},
                              {"/display clear","",": Clears copied display entities."},
                              {"/display save ","<filename> <description>",": Saves display entity."},
                              {"/display files ","[folder]",": Shows saved display entities."}};
        helpList.addAll(Arrays.asList(help));
            for(DisplayEntityEditorMode mode: DisplayEntityEditorMode.values()) {
                helpList.add(new String[]{"/display "+mode.getName(),mode.getArguments(),mode.getHelpText()});
            }
        help = helpList.toArray(help);
        super.sendHelpMessage(player, page);
    }
    
}
