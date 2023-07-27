package com.mcmiddleearth.architect.customHeadManager;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.google.common.io.BaseEncoding;
import com.mcmiddleearth.architect.PluginData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.net.URL;
import java.util.UUID;

public class HeadDataBuilderItem {

    public HeadDataBuilderItem(Player submitter, String name) {
        ItemStack item = submitter.getInventory().getItemInMainHand();
        if(item.getItemMeta() instanceof SkullMeta skullMeta) {
            UUID player = (skullMeta.getPlayerProfile()!=null?skullMeta.getPlayerProfile().getId():null);
            UUID owner = (skullMeta.getOwningPlayer()!=null?skullMeta.getOwningPlayer().getUniqueId():player);
            if(owner == null)  owner = submitter.getUniqueId();

            PlayerProfile profile = skullMeta.getPlayerProfile();
            URL url = profile.getTextures().getSkin();

            String texture = BaseEncoding.base64().encode(String.format("{textures:{SKIN:{url:\"%s\"}}}", url).getBytes());
            /*try {
                Field profileField = skullMeta.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);
                GameProfile profile = (GameProfile) profileField.get(skullMeta);
                PropertyMap propertyMap = profile.getProperties();
                Property property = (Property) propertyMap.get("textures");
                texture = property.getValue();
            } catch (NoSuchFieldException | SecurityException e) {
                Bukkit.getLogger().log(Level.SEVERE, "No such method exception during reflection.", e);
            } catch (IllegalArgumentException | IllegalAccessException e) {
                Bukkit.getLogger().log(Level.SEVERE, "Unable to use reflection.", e);
            }*/

            CustomHeadData headData = new CustomHeadData(owner, texture);
            if (CustomHeadManagerData.addReviewHead(name, headData)) {
                PluginData.getMessageUtil().sendInfoMessage(submitter, "Head has been submitted.");
            } else {
                PluginData.getMessageUtil().sendErrorMessage(submitter, "Failed to submit head.");
            }
        } else {
            PluginData.getMessageUtil().sendErrorMessage(submitter, "You don't have a head in your main hand.");
        }
    }
}
