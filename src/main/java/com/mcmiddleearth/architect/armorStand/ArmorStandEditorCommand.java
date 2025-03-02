/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.armorStand;

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.additionalCommands.AbstractArchitectCommand;
import com.mcmiddleearth.architect.armorStand.guard.ArmorStandRollbackCommand;
import com.mcmiddleearth.pluginutil.FileUtil;
import com.mcmiddleearth.pluginutil.NumericUtil;
import com.mcmiddleearth.pluginutil.message.FancyMessage;
import com.mcmiddleearth.pluginutil.message.MessageType;

import java.io.IOException;
import java.util.*;

import org.bukkit.ChatColor;
import org.bukkit.Location;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 *
 * @author Eriol_Eandur
 */
public class ArmorStandEditorCommand extends AbstractArchitectCommand {

    private final static Map<UUID, ArmorStandEditorConfig> configList = new HashMap<>();
    
    private final int maxStepSize = 360;
    
    @Override
    public boolean onCommand(CommandSender cs, Command cmd, String c, String[] args) {
        if (!(cs instanceof Player)) {
            PluginData.getMessageUtil().sendPlayerOnlyCommandError(cs);
            return true;
        }
        if(args.length>0 && args[0].equalsIgnoreCase("rollback")) {
            return ArmorStandRollbackCommand.execute((Player)cs, args, configList);
        }
        if(!PluginData.hasPermission(cs,Permission.ARMOR_STAND_EDITOR)) {
            PluginData.getMessageUtil().sendNoPermissionError(cs);
            return true;
        } else {
            Player p = (Player) cs;
            if(!PluginData.isModuleEnabled(p.getWorld(), Modules.ARMOR_STAND_EDITOR)) {
                sendNotActivatedMessage(cs);
                return true;
            }
            ArmorStandEditorConfig playerConfig =  getPlayerConfig(p);
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
                if(args[0].equalsIgnoreCase("parts")) {
                    sendPartsHelpMessage(cs);
                    return true;
                }
                if(args[0].equalsIgnoreCase("clear")) {
                    playerConfig.clearCopiedArmorStand();
                    sendCopiedArmorStandClearedMessage(cs);
                    return true;
                }
                if(args[0].equalsIgnoreCase("files")) {
                    PluginData.getMessageUtil().sendFancyFileListMessage(p,
                                    new FancyMessage(MessageType.INFO, PluginData.getMessageUtil())
                                            .addSimple(PluginData.getMessageUtil().STRESSED+"Armor Stand "
                                                      +PluginData.getMessageUtil().INFO+"files /"),
                                    ArmorStandEditorConfig.getDataDir(),
                                    FileUtil.getFileExtFilter(ArmorStandEditorConfig.getFileExtension()),
                                    Arrays.copyOfRange(args, 1, args.length), 
                                    "/armor files", 
                                    "/armor p", 
                                    true);
                    return true;
                }
                if(!PluginData.hasPermission(p, Permission.ARMOR_STAND_EDITOR_TRUSTED)
                        && (args[0].equalsIgnoreCase("place")
                          ||args[0].equalsIgnoreCase("delete")
                          ||args[0].equalsIgnoreCase("rename")
                          ||args[0].equalsIgnoreCase("save"))) {
                    PluginData.getMessageUtil().sendNoPermissionError(cs);
                    return true;
                }
                if(args[0].equalsIgnoreCase("paste")) {
                    Vector loc = p.getLocation().toVector();
                    loc = loc.add(playerConfig.getCopiedArmorStandRelativeLoc());
                    playerConfig.placeArmorStand(loc.toLocation(p.getWorld()),false);
                    return true;
                }
                if(args[0].equalsIgnoreCase("place2") && PluginData.hasPermission(p,Permission.RANDOMISER_MATERIALS)) {
                    for(int i = 0 ; i < NumericUtil.getInt(args[1]);i+=NumericUtil.getInt(args[2])) {
                        for( int j = 0; j< NumericUtil.getInt(args[1]);j+=NumericUtil.getInt(args[2])) {
                            playerConfig.placeArmorStand(new Location(p.getWorld(),
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
                        if(!(PluginData.hasPermission(p, Permission.ARMOR_STAND_EDITOR_DELETE) 
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
                        if(!(PluginData.hasPermission(p, Permission.ARMOR_STAND_EDITOR_DELETE) 
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
                    if(!playerConfig.hasCopiedArmorStand()) {
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
                ArmorStandEditorMode editorMode = ArmorStandEditorMode.getEditorMode(args[0]);
                if(editorMode == null) {
                    PluginData.getMessageUtil().sendInvalidSubcommandError(cs);
                }
                else {
                    if(editorMode.equals(ArmorStandEditorMode.PASTE) && args.length>1) {
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
                    if(args.length>1) {
                        ArmorStandPart part = ArmorStandPart.getPart(args[1]);
                        if(part!=null) {
                            playerConfig.setPart(part);
                        }
                    } else if(args[0].toLowerCase().startsWith(ArmorStandEditorMode.STRAIGHT.getName())) {
                        playerConfig.setPart(ArmorStandPart.ALL);
                    }
                    sendInfoMessage(cs, playerConfig);
                }
            }
            return true;
        }
    }
    
    public static ArmorStandEditorConfig getPlayerConfig(Player p) {
        for(UUID search: configList.keySet()) {
            if(search.equals(p.getUniqueId())) {
                return configList.get(search);
            }
        }
        ArmorStandEditorConfig newConfig = new ArmorStandEditorConfig(p);
        configList.put(p.getUniqueId(), newConfig);
        return newConfig;
    }
        
    private void sendInfoMessage(CommandSender cs, ArmorStandEditorConfig playerConfig) {
                    PluginData.getMessageUtil().sendInfoMessage(cs, "Current Armor Stand Editor mode: ");
                    switch(playerConfig.getEditorMode()) {
                        case HELMET:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> remove/place helmet item");
                            break;
                        case HAND:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> remove/place item in main hand");
                            break;
                        case OFF_HAND:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> remove/place item in off hand.");
                            break;
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
                        case BASE:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> switch base plate");
                            break;
                        case MARKER:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> switch collision box");
                            break;
                        case ARMS:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> switch arms");
                            break;
                        case PASTE:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> paste armor stand");
                            break;
                        case COPY:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> copy armor stand");
                            break;
                        case STRAIGHT:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "   -> straighten armor stand");
                            break;
                    }
    }

    private void sendRotationStepMessage(CommandSender cs, int rotationStep) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "    -> Set rot/move step to "+rotationStep+" percent/degree");
    }

    private void sendCopiedArmorStandClearedMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "armor stand clippboard was cleared.");
    }

    private void sendPartsHelpMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "part arguments of Armor Stand Editor:");
        PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, PluginData.getMessageUtil().STRESSED+"    h "+PluginData.getMessageUtil().INFO+" -> head");
        PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, PluginData.getMessageUtil().STRESSED+"    la "+PluginData.getMessageUtil().INFO+"-> left arm");
        PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, PluginData.getMessageUtil().STRESSED+"    ra "+PluginData.getMessageUtil().INFO+"-> right arm");
        PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, PluginData.getMessageUtil().STRESSED+"    ll "+PluginData.getMessageUtil().INFO+"-> left leg");
        PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, PluginData.getMessageUtil().STRESSED+"    rl "+PluginData.getMessageUtil().INFO+"-> right leg");
        PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, PluginData.getMessageUtil().STRESSED+"    b  "+PluginData.getMessageUtil().INFO+"-> body");
    }

    private void sendNotEnoughArgumentsMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "Not enough arguments: /armor save <filename> <description>");
    }

    private void sendSavedMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "Armor stand saved.");
    }

    private void sendLoadedMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "Armor stand loaded.");
    }

    private void sendExistsMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "File already exists. Delete first.");
    }

    private void sendCopyFirstMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "Copy an armor stand first. Use '/armor c' and click with stick at an armor stand.");
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
        PluginData.getMessageUtil().sendErrorMessage(cs, "Armor stand editor is not activated for this world.");
    }
        
    private void sendStepSizeDefaultMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "    -> Set move/rot step to 10 percent/degree.");
    }
        
    @Override
    public String getHelpPermission() {
        return Permission.ARMOR_STAND_EDITOR.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": Armor Stand Editor.";
    }

    @Override
    public String getUsageDescription() {
        return ": The armor stand editor features a number of commands to select properties of an armor stand you want to change. The changes are applied to an armor stand by clicking at it with a stick in hand. \n "
                +ChatColor.WHITE+"Click for detailed help.";
    }
    
    @Override
    public String getHelpCommand() {
        return "/armor help";
    }
    
    @Override
    protected void sendHelpMessage(Player player, int page) {
        List<String[]> helpList = new ArrayList<>();
        helpHeader = "Help for "+PluginData.getMessageUtil().STRESSED+"Armor Stand Editor -";
        help = new String[][]{{"/armor parts","",": Shows help about armor stand parts."},
                              {"/armor place","",": Places copied armor stand."},
                              {"/armor clear","",": Clears copied armor stand."},
                              {"/armor save ","<filename> <description>",": Saves armor stand."},
                              {"/armor files ","[folder]",": Shows saved armor stands."}};
        helpList.addAll(Arrays.asList(help));
            for(ArmorStandEditorMode mode: ArmorStandEditorMode.values()) {
                helpList.add(new String[]{"/armor "+mode.getName(),mode.getArguments(),mode.getHelpText()});
            }
        help = helpList.toArray(help);
        super.sendHelpMessage(player, page);
    }
    
}
