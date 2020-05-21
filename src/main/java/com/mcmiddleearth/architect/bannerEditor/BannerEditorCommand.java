/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.bannerEditor;

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.additionalCommands.AbstractArchitectCommand;
import com.mcmiddleearth.pluginutil.FileUtil;
import com.mcmiddleearth.pluginutil.NumericUtil;
import com.mcmiddleearth.pluginutil.message.FancyMessage;
import com.mcmiddleearth.pluginutil.message.MessageType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 *
 * @author Eriol_Eandur
 */
public class BannerEditorCommand extends AbstractArchitectCommand {

    private final static Map<UUID, BannerEditorConfig> configList = new HashMap<>();
    
    @Override
    public boolean onCommand(CommandSender cs, Command cmd, String c, String[] args) {
        if (!(cs instanceof Player)) {
            PluginData.getMessageUtil().sendPlayerOnlyCommandError(cs);
            return true;
        }
        if(!PluginData.hasPermission((Player)cs,Permission.BANNER_EDITOR)) {
            PluginData.getMessageUtil().sendNoPermissionError(cs);
            return true;
        }
        if(!PluginData.isModuleEnabled(((Player)cs).getWorld(), Modules.BANNER_EDITOR)) {
            sendNotEnabledErrorMessage(cs);
            return true;
        } else {
            Player p = (Player) cs;
            BannerEditorConfig playerConfig =  getPlayerConfig(p);
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
                } else if(args[0].equalsIgnoreCase("save")) {
                    if(!PluginData.hasPermission((Player)cs,Permission.BANNER_EDITOR_SAVE)) {
                        PluginData.getMessageUtil().sendNoPermissionError(cs);
                        return true;
                    }
                    if(args.length<3) {
                        PluginData.getMessageUtil().sendNotEnoughArgumentsError(cs);
                        return true;
                    }
                    if(!(BannerUtil.isBanner(p.getInventory().getItemInMainHand().getType()))) {
                        sendNoBannerInHandError(cs);
                    } else {
                        String description = "";
                        for(int i=2;i<args.length;i++) {
                            description = description + args[i]+" ";
                        }
                        try {
                            if(playerConfig.saveBanner(p.getInventory().getItemInMainHand(), args[1], description, ((Player)cs).getUniqueId())) {
                                sendBannerSavedMessage(cs);
                            } else {
                                sendFileExistsMessage(cs);
                            }
                        } catch (IOException ex) {
                            PluginData.getMessageUtil().sendIOError(cs);
                            Logger.getLogger(BannerEditorCommand.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    return true;
                } else if(args[0].equalsIgnoreCase("delete")) {
                    if(args.length<2) {
                        PluginData.getMessageUtil().sendNotEnoughArgumentsError(cs);
                        return true;
                    }
                    if(!(PluginData.hasPermission((Player)cs,Permission.BANNER_EDITOR_DELETE)
                            || playerConfig.isCreator(args[1],((Player)cs).getUniqueId()))) {
                        PluginData.getMessageUtil().sendNoPermissionError(cs);
                        return true;
                    }
                    if(playerConfig.deleteFile(args[1])) {
                        sendFileDeletedMessage(cs);
                    }
                    else {
                        sendDeleteErrorMessage(cs);
                    }
                    return true;
                } else if(args[0].equalsIgnoreCase("load")) {
                    if(args.length<2) {
                        PluginData.getMessageUtil().sendNotEnoughArgumentsError(cs);
                        return true;
                    }
                    ItemStack item = playerConfig.loadBanner(args[1]);
                    if(item == null) {
                        sendBannerNotFound(cs);
                        return true;
                    }
                    item.setAmount(item.getMaxStackSize());
                    p.getInventory().addItem(item);
                    sendGotBanner(p);
                    return true;
                } else if(args[0].equalsIgnoreCase("files")) {
                    PluginData.getMessageUtil().sendFancyFileListMessage(p,
                                    new FancyMessage(MessageType.INFO,PluginData.getMessageUtil())
                                            .addSimple(PluginData.getMessageUtil().STRESSED+"Banner "
                                                    +PluginData.getMessageUtil().INFO+"files /"),
                                    BannerEditorConfig.getDataDir(),
                                    FileUtil.getFileExtFilter(BannerEditorConfig.getFileExtension()),
                                    Arrays.copyOfRange(args, 1, args.length), 
                                    "/banner files", 
                                    "/banner load", true);
                    return true;
                }
                int id = NumericUtil.getInt(args[0]);
                if(id>-1) {
                    playerConfig.setPatternId(id);
                    sendInfoMessage(cs, playerConfig);
                    return true;
                }
                if(args.length>1) {
                    id = NumericUtil.getInt(args[1]);
                    if(id>-1) {
                        playerConfig.setPatternId(id);
                    }
                }
                BannerEditorMode editorMode = BannerEditorMode.getEditorMode(args[0]);
                if(editorMode == null) {
                    PluginData.getMessageUtil().sendInvalidSubcommandError(cs);
                }
                else {
                    playerConfig.setEditorMode(editorMode);
                    sendInfoMessage(cs, playerConfig);
                }
            }
            return true;
        }
    }
    
    public static BannerEditorConfig getPlayerConfig(OfflinePlayer p) {
        for(UUID search: configList.keySet()) {
            if(search.equals(p.getUniqueId())) {
                return configList.get(search);
            }
        }
        BannerEditorConfig newConfig = new BannerEditorConfig();
        configList.put(p.getUniqueId(), newConfig);
        return newConfig;
    }
        
    private void sendInfoMessage(CommandSender cs, BannerEditorConfig playerConfig) {
                    PluginData.getMessageUtil().sendInfoMessage(cs,"banner editor mode: ");
                    switch(playerConfig.getEditorMode()) {
                        case ORIENTATION:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs,"   -> change orientation");
                            break;
                        case LIST:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs,"   -> list patterns");
                            break;
                        case TEXTURE:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs,"   -> change texture "+ playerConfig.getPatternId());
                            break;
                        case COLOR:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs,"   -> change color of pattern "+ playerConfig.getPatternId());
                            break;
                        case ADD:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs,"   -> add pattern");
                            break;
                        case REMOVE:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs,"   -> remove pattern "+ playerConfig.getPatternId());
                            break;
                        case SHIELD:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs,"   -> banner to shield");
                            break;
                        case GET:
                            PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs,"   -> get banner ");
                            break;
                    }
    }

    private void sendNotEnabledErrorMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "Banner editor is not enabled for this world.");
    }

    private void sendNoBannerInHandError(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "You don't have a banner in hand.");
    }

    private void sendBannerSavedMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "Banner was saved to file.");
    }

    private void sendFileExistsMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "File already exists. Delete first.");
    }

    private void sendGotBanner(Player p) {
        PluginData.getMessageUtil().sendInfoMessage(p, "Banner was loaded and placed in your inventory.");
    }

    private void sendBannerNotFound(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "Banner file not found or no valid banner data in file.");
    }
    
    private void sendFileDeletedMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "File deleted.");
    }

    private void sendDeleteErrorMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "File not found or directory not empty.");
    }

    @Override
    public String getHelpPermission() {
        return Permission.BANNER_EDITOR.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": Banner Editor.";
    }

    @Override
    public String getUsageDescription() {
        return ": A banner consists of a base which has only a color but no texture and up to 6 different patterns which have a texture and a color each. \n " 
                + "The banner editor features a number of commands to select properties of a banner you want to change. The changes are applied to a banner by clicking at it with a stick in hand. \n "
                + ChatColor.WHITE+"Click for detailed help.";
    }
    
    @Override
    public String getHelpCommand() {
        return "/banner help";
    }
    
    @Override
    protected void sendHelpMessage(Player player, int page) {
        List<String[]> helpList = new ArrayList<>();
        helpHeader = "Help for "+PluginData.getMessageUtil().STRESSED+"Banner Editor -";
        help = new String[][]{{"/banner files ","[folder]",": Shows saved banners."},
                              {"/banner save ","<filename> <description>",": Saves banner."},
                              {"/banner delete ","<filename>",": Deletes a saved banner."},
                              {"/banner load ","<filename>",": Loads banner."}};
        helpList.addAll(Arrays.asList(help));
            for(BannerEditorMode mode: BannerEditorMode.values()) {
                helpList.add(new String[]{"/banner "+mode.getName(),mode.getArguments(),mode.getHelpText()});
            }
        help = helpList.toArray(help);
        super.sendHelpMessage(player, page);
    }
    
}
