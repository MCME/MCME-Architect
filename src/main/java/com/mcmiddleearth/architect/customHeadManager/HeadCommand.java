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
package com.mcmiddleearth.architect.customHeadManager;

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.additionalCommands.AbstractArchitectCommand;
import com.mcmiddleearth.pluginutil.FileUtil;
import com.mcmiddleearth.pluginutil.NumericUtil;
import com.mcmiddleearth.pluginutil.message.FancyMessage;
import com.mcmiddleearth.pluginutil.message.MessageType;
import java.util.Arrays;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 *
 * @author Eriol_Eandur
 */
public class HeadCommand extends AbstractArchitectCommand {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            PluginData.getMessageUtil().sendPlayerOnlyCommandError(sender);
            return true;
        }
        Player player = (Player) sender;
        if(!PluginData.isModuleEnabled(player.getWorld(), Modules.CUSTOM_HEAD_MANAGER)) {
            sendNotActivatedMessage(player);
            return true;
        }
        //user commands
        if(!PluginData.hasPermission(player,Permission.CUSTOM_HEAD_USER)) {
            PluginData.getMessageUtil().sendNoPermissionError(player);
            return true;
        }
        if (args.length < 1 || args[0].equalsIgnoreCase("help")) {
            int page = 1;
            if(args.length>1 && NumericUtil.isInt(args[1])) {
                page = NumericUtil.getInt(args[1]);
            }
            sendHelpMessage(player,page);
            return true;
        }
        if(args[0].equalsIgnoreCase("warp")) {
            if(CustomHeadManagerData.getGallery()!=null) {
                PluginData.getMessageUtil().sendInfoMessage(player, "Teleporting to Custom Head Collection ...");
                player.teleport(CustomHeadManagerData.getGallery().getLocation());
            } else {
                PluginData.getMessageUtil().sendErrorMessage(player, "There is no Custom Head Collection on the server.");
            }
            return true;
        }
        if(args[0].equalsIgnoreCase("list")) {
            String[] messageArgs;
            if(args.length<2) {
                messageArgs = new String[0];
            } else {
                messageArgs = Arrays.copyOfRange(args,1,args.length);
            }
            PluginData.getMessageUtil().sendFancyFileListMessage(player, 
                             new FancyMessage(MessageType.INFO,PluginData.getMessageUtil())
                                .addSimple(PluginData.getMessageUtil().STRESSED+"Custom Head "
                                        +PluginData.getMessageUtil().INFO +"files /"), 
                             CustomHeadManagerData.getAcceptedHeadDir(), 
                             FileUtil.getFileExtFilter(CustomHeadManagerData.getFileExtension()), 
                             messageArgs,
                             "/chead list", 
                             "/get head",
                             true);
            return true;
        }
        if (args.length < 2 && !args[0].equalsIgnoreCase("setCollection")
                            && !args[0].equalsIgnoreCase("reviewList")
                            && !args[0].equalsIgnoreCase("reload")
                            && !args[0].equalsIgnoreCase("upload")) {
            PluginData.getMessageUtil().sendNotEnoughArgumentsError(sender);
            return true;
        }
        if(args[0].equalsIgnoreCase("submit")) {
            String headName="";
            if(args.length>2) {
                UUID ownerId=null;
                try {
                    ownerId = UUID.fromString(args[1]);
                }
                catch(IllegalArgumentException e) {}
                if(ownerId==null) {
                    CustomHeadManagerData.submitHead(player, args[1], args[2]);
                } else {
                    CustomHeadManagerData.submitHead(player, ownerId, args[2]);
                }
            } else {
                CustomHeadManagerData.submitHead(player, player.getUniqueId(), args[1]);
            }
            sendSubmittingHeadMessage(player);
            return true;
        }
            
        //manager commands
        if(!PluginData.hasPermission(player,Permission.CUSTOM_HEAD_MANAGER)) {
            PluginData.getMessageUtil().sendNoPermissionError(player);
            return true;
        }
        if(args[0].equalsIgnoreCase("upload")) {
            boolean uploadToReviewFolder = false;
            String filename = "upload.txt";
            if(args.length>1) {
                int index = 1;
                if(!args[1].equalsIgnoreCase("-r")){
                    filename = args[1];
                    index++;
                } 
                if(args.length>index && args[index].equalsIgnoreCase("-r")) {
                    uploadToReviewFolder = true;
                } 
            }
            CustomHeadManagerData.upload(player, filename, uploadToReviewFolder);
            sendHeadsUploaded(player, uploadToReviewFolder);
            return true;
        }
        if(args[0].equalsIgnoreCase("reload")) {
            CustomHeadManagerData.load();
            sendHeadsReloaded(player);
            return true;
        }
        if(args[0].equalsIgnoreCase("reviewList")) {
            String[] messageArgs;
            if(args.length<2) {
                messageArgs = new String[0];
            } else {
                messageArgs = Arrays.copyOfRange(args,1,args.length);
            }
            PluginData.getMessageUtil().sendFancyFileListMessage(player, 
                         new FancyMessage(MessageType.INFO, PluginData.getMessageUtil())
                            .addSimple(PluginData.getMessageUtil().HIGHLIGHT+"Submitted Custom Heads "
                                    +PluginData.getMessageUtil().INFO +" /"), 
                         CustomHeadManagerData.getSubmittedHeadDir(), 
                         FileUtil.getFileExtFilter(CustomHeadManagerData.getFileExtension()), 
                         messageArgs,
                         "/chead reviewList", 
                         "/chead review", true);
            return true;
        }
        if(args[0].equalsIgnoreCase("setCollection")) {
            CustomHeadGallery gallery = CustomHeadManagerData.getGallery();
            if(gallery == null) {
                CustomHeadManagerData.setGallery(
                        new CustomHeadGallery(CustomHeadManagerData.getCollection(),
                                              player.getLocation()));
                sendNewGalleryCreatedMessage(player);
            } else {
                gallery.setLocation(player.getLocation());
                sendGalleryMovedMessage(player);
            }
            CustomHeadManagerData.saveGallery();
            return true;
        }
        if(args[0].equalsIgnoreCase("review")) {
            ItemStack head = CustomHeadManagerData.getSumittedHead(args[1]);
            if(head!=null) {
                player.getInventory().addItem(head);
                sendGivenReviewHead(player);
            } else {
                sendHeadNotFound(player);
            }
            return true;
        }
        if(args[0].equalsIgnoreCase("reject")) {
            if(CustomHeadManagerData.rejectHead(args[1])) {
                sendHeadRejected(player);
            } else {
                sendHeadNotFound(player);
            }
            return true;
        }
        if(args[0].equalsIgnoreCase("delete")) {
            if(CustomHeadManagerData.deleteHead(args[1])) {
                sendHeadDeleted(player);
            } else {
                sendHeadNotFound(player);
            }
            return true;
        }
        if (args.length < 3) {
            PluginData.getMessageUtil().sendNotEnoughArgumentsError(sender);
            return true;
        }
        if(args[0].equalsIgnoreCase("rename")) {
            if(CustomHeadManagerData.getHeadData(args[1])==null) {
                sendHeadNotFound(player);
                return true;
            }
            if(CustomHeadManagerData.renameHead(args[1],args[2])) {
                sendHeadMoved(player);
            } else {
                sendHeadAlreadyExists(player);
            }
            return true;
        }
        if(args[0].equalsIgnoreCase("accept")) {
            CustomHeadData data = CustomHeadManagerData.getSubmittedHeadData(args[1]);
            if(data == null) {
                sendHeadNotFound(player);
                return true;
            }
            if(CustomHeadManagerData.acceptHead(args[1],args[2])) {
                sendHeadAcceptedMessage(player);
            } else {
                sendHeadAlreadyExists(player);
            }
            return true;
        }
        PluginData.getMessageUtil().sendInvalidSubcommandError(player);
        return true;
    }

    private void sendNotActivatedMessage(Player player) {
        PluginData.getMessageUtil().sendErrorMessage(player,"Custom Heads are not enabled for this world.");
    }

    private void sendNewGalleryCreatedMessage(Player player) {
        PluginData.getMessageUtil().sendInfoMessage(player,"Custom Head Gallery was created at your location.");
    }

    private void sendGalleryMovedMessage(Player player) {
        PluginData.getMessageUtil().sendInfoMessage(player,"Custom Head Gallery was moved to your location.");
    }

    private void sendGivenReviewHead(Player player) {
        PluginData.getMessageUtil().sendInfoMessage(player,"Given submitted head for review.");
    }

    private void sendHeadNotFound(Player player) {
        PluginData.getMessageUtil().sendErrorMessage(player,"Head not found.");
    }

    private void sendHeadAcceptedMessage(Player player) {
        PluginData.getMessageUtil().sendInfoMessage(player,"Head was added to Custom Head Collection.");
    }

    private void sendHeadAlreadyExists(Player player) {
        PluginData.getMessageUtil().sendErrorMessage(player,"Head could not be added to Custom Head Collection. Already exists?");
    }

    private void sendHeadRejected(Player player) {
        PluginData.getMessageUtil().sendInfoMessage(player,"Head was deleted from submitted heads directory.");
    }

    private void sendHeadDeleted(Player player) {
        PluginData.getMessageUtil().sendInfoMessage(player,"Head was deleted from Custom Head Collection.");
    }

    private void sendHeadMoved(Player player) {
        PluginData.getMessageUtil().sendInfoMessage(player,"Head was renamed.");
    }

    private void sendSubmittingHeadMessage(Player player) {
        PluginData.getMessageUtil().sendInfoMessage(player,"Submitting your head . . . .");
    }
    
    private void sendHeadsReloaded(Player player) {
        PluginData.getMessageUtil().sendInfoMessage(player,"Custom Head Collection was reloaded from file.");
    }
    
    @Override
    public String getHelpPermission() {
        return Permission.CUSTOM_HEAD_USER.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": Submit and manage heads.";
    }
    @Override
    public String getUsageDescription() {
        return ": Provides commands to submit heads to MCME Head Collection and manage it. \n "
                +ChatColor.WHITE+"Click for detailed help.";
    }
    
    @Override
    public String getHelpCommand() {
        return "/chead help";
    }
    
    @Override
    protected void sendHelpMessage(Player player, int page) {
        helpHeader = "Help for "+PluginData.getMessageUtil().STRESSED+"MCME Head Collection -";
        help = new String[][]{{"/chead warp","",": Teleports you to the MCME Head Collection."},
                                       {"/chead list","",": Shows a clickable list of all heads."},
                                       {"/chead submit ","[playername] <headName>",": Submits a head. ","If no [playername] is specified your own head will be submitted."}};
        super.sendHelpMessage(player, page);
    }

    private void sendHeadsUploaded(Player player, boolean uploadToReviewFolder) {
        if(uploadToReviewFolder) {
            PluginData.getMessageUtil().sendInfoMessage(player,"Heads uploaded to review folder.");
        } else {
            PluginData.getMessageUtil().sendInfoMessage(player,"Heads uploaded to Custom Head Collection.");
        }
    }
    
    
}
