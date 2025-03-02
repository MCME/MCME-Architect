/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.armorStand;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.armorStand.guard.ArmorStandGuard;
import com.mcmiddleearth.pluginutil.EventUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;

import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class ArmorStandListener implements Listener {

    @EventHandler
    public void placeArmorStand(EntitySpawnEvent event) {
        if(PluginData.isModuleEnabled(event.getEntity().getWorld(),Modules.ARMOR_STAND_EDITOR)
            && event.getEntity().getType().equals(EntityType.ARMOR_STAND)) {
            event.getEntity().setGravity(false);
            event.getEntity().setInvulnerable(true);
        }
    }

    @EventHandler
    public void blockFishingRod(PlayerFishEvent event) {
        if(PluginData.isModuleEnabled(event.getPlayer().getWorld(),Modules.ARMOR_STAND_PROTECTION)
                && (event.getCaught() instanceof ArmorStand)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void blockCombustion(EntityCombustEvent event) {
        if((event.getEntity() instanceof ArmorStand)
                && PluginData.isModuleEnabled(event.getEntity().getWorld(),Modules.ARMOR_STAND_PROTECTION)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void PlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if(!event.hasBlock() || !EventUtil.isMainHandEvent(event)) {
            return;
        }
        if(PluginData.isModuleEnabled(p.getWorld(),Modules.ARMOR_STAND_PROTECTION)
                && p.getInventory().getItemInMainHand().getType().equals(Material.ARMOR_STAND)
                && EventUtil.isMainHandEvent(event)
                && event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
            if(!PluginData.checkBuildPermissions(p, event.getClickedBlock().getLocation(),
                                            Permission.ARMOR_STAND_EDITOR)) {
                event.setCancelled(true);
            }
        }
        ArmorStandEditorConfig config = ArmorStandEditorCommand.getPlayerConfig(p);
        if(PluginData.isModuleEnabled(p.getWorld(),Modules.ARMOR_STAND_EDITOR)
                && config.getEditorMode().equals(ArmorStandEditorMode.PASTE)
                && p.getInventory().getItemInHand().getType().equals(Material.STICK)
                && EventUtil.isMainHandEvent(event)) {
            if(!PluginData.checkBuildPermissions(p, event.getClickedBlock().getLocation(),
                                         Permission.ARMOR_STAND_EDITOR)) {
                event.setCancelled(true);
                return;
            }
            Location loc;
            boolean exact;
            if(event.getAction().equals(Action.LEFT_CLICK_BLOCK)
                    || event.getAction().equals(Action.LEFT_CLICK_AIR)) {
                loc = p.getLocation();
                exact = true;
            }
            else {
                loc = event.getClickedBlock().getRelative(BlockFace.UP, 1).getLocation();
                exact = false;
            }
            config.placeArmorStand(loc, exact);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void EntityDamageByEntity(EntityDamageByEntityEvent event) {
        if(event.getEntity() instanceof ArmorStand) {
            if(PluginData.isModuleEnabled(event.getEntity().getWorld(),Modules.ARMOR_STAND_PROTECTION)
                    && (!(event.getDamager() instanceof Player)
                        || !PluginData.hasPermission((Player)event.getDamager(),
                                                     Permission.ARMOR_STAND_EDITOR))) {
                if(!(event.getDamager() instanceof Player)) {
                    event.setCancelled(true);
                    return;
                } else if(!PluginData.checkBuildPermissions((Player) event.getDamager(), event.getEntity().getLocation(),
                                                        Permission.ARMOR_STAND_EDITOR)) {
                    event.setCancelled(true);
                    return;
                }
            }
            if(PluginData.isModuleEnabled(event.getEntity().getWorld(),Modules.ARMOR_STAND_EDITOR)) {
                event.setCancelled(event.isCancelled() 
                        | manipulate((ArmorStand)event.getEntity(), (Player) event.getDamager(), false));
            }
        } 
    }
    
    @EventHandler //(ignoreCancelled = true) removed to make item block stands editable
    public void PlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if(event.getRightClicked() instanceof ArmorStand) {
            if(PluginData.isModuleEnabled(event.getPlayer().getWorld(),Modules.ARMOR_STAND_PROTECTION)) {
                if(!PluginData.checkBuildPermissions(event.getPlayer(), event.getRightClicked().getLocation(),
                                             Permission.ARMOR_STAND_EDITOR)) {
                    event.setCancelled(true);
                    return;
                }
            }
            if(PluginData.isModuleEnabled(event.getPlayer().getWorld(),Modules.ARMOR_STAND_EDITOR)) {
                event.setCancelled(event.isCancelled() 
                        | manipulate((ArmorStand) event.getRightClicked(),event.getPlayer(),true));
            }
        }
    }
    
    private boolean manipulate(ArmorStand armorStand, Player player, boolean rightClick) {
        if(!PluginData.checkBuildPermissions(player, armorStand.getLocation(),
                                     Permission.ARMOR_STAND_EDITOR)) {
            return true;
        }
        ArmorStandGuard.setModifiedFlag(armorStand);
        ArmorStandEditorConfig config = ArmorStandEditorCommand.getPlayerConfig(player);
        ArmorStandEditorMode mode = config.getEditorMode();
        if(ArmorStandUtil.isLocked(armorStand)
                && !(mode.equals(ArmorStandEditorMode.LOCK) 
                  && player.getItemInHand().getType().equals(Material.STICK))) {
            sendLockedMessage(player);
            return true;
        }
        int stepInDegree = config.getRotationStep();
        if(!(player.getItemInHand().getType().equals(Material.STICK)
                || mode.equals(ArmorStandEditorMode.HAND)
                || mode.equals(ArmorStandEditorMode.OFF_HAND)
                || mode.equals(ArmorStandEditorMode.HELMET))) {
            return false;
        }
        ArmorStandPart part = config.getPart();
        ArmorStandEditorMode modifiedMode = mode;
//Logger.getGlobal().info("mode: "+mode.name());
        switch(mode) {
            case HAND:
            case OFF_HAND:
                EntityEquipment equipment = armorStand.getEquipment();
                if(!rightClick) {
                    if(mode.equals(ArmorStandEditorMode.HAND))
                        equipment.setItemInMainHand(null);//armorStand.setItemInHand(null);
                    else
                        equipment.setItemInOffHand(null);//armorStand.setItemInHand(null);
                    break;
                }
                ItemStack oldItem;
                if(mode.equals(ArmorStandEditorMode.HAND))
                    oldItem = equipment.getItemInMainHand();
                else
                    oldItem = equipment.getItemInOffHand();
                //oldItem= armorStand.getItemInHand();
                ItemStack newItem = new ItemStack(player.getInventory().getItemInMainHand());
                newItem.setAmount(1);
                if(mode.equals(ArmorStandEditorMode.HAND))
                    equipment.setItemInMainHand(newItem);//armorStand.setItemInHand(null);
                else
                    equipment.setItemInOffHand(newItem);//armorStand.setItemInHand(null);
                //armorStand.setItemInHand(newItem);
                if(player.getGameMode().equals(GameMode.SURVIVAL)) {
                    int amount = player.getInventory().getItemInMainHand().getAmount();
                    if(amount==1) {
                        player.getInventory().setItemInMainHand(null);
                    }
                    else {
                        player.getInventory().getItemInMainHand().setAmount(amount-1);
                    }
                    player.getInventory().addItem(oldItem);
                }
                break;
            case HELMET:
//Logger.getGlobal().info("right Click: "+rightClick);
                if(!rightClick) {
                    armorStand.setHelmet(null);
                    break;
                }
                ItemStack oldHelmet = armorStand.getHelmet();
                ItemStack newHelmet = new ItemStack(player.getInventory().getItemInMainHand());
                newHelmet.setAmount(1);
                armorStand.setHelmet(newHelmet);
//Logger.getGlobal().info("Helmet set: "+newHelmet.getType());
                if(player.getGameMode().equals(GameMode.SURVIVAL)) {
                    int amount = player.getInventory().getItemInMainHand().getAmount();
                    if(amount==1) {
                        player.getInventory().setItemInMainHand(null);
                    }
                    else {
                        player.getInventory().getItemInMainHand().setAmount(amount-1);
                    }
                    player.getInventory().addItem(oldHelmet);
                }
                break;
            case ROTATE:
                Location playerLoc = player.getLocation();
                if(playerLoc.getPitch()>45 || playerLoc.getPitch()<-45) {
                    modifiedMode = ArmorStandEditorMode.YROTATE;
                    if(playerLoc.getPitch()<-45) {
                        rightClick=!rightClick;
                    }
                }
                else 
                {
                    float yaw = playerLoc.getYaw() - armorStand.getLocation().getYaw();
                    while(yaw<-180) {
                        yaw+=360;
                    }
                    while(yaw>180) {
                        yaw-=360;
                    }
                    if((yaw<45 && yaw>-45) || (yaw>135 || yaw<-135)) {
                        modifiedMode = ArmorStandEditorMode.ZROTATE;
                        if(yaw<45 && yaw>-45) {
                            rightClick=!rightClick;
                        }
                    } else {
                        modifiedMode = ArmorStandEditorMode.XROTATE;
                        if(yaw>45 && yaw<135) {
                            rightClick=!rightClick;
                        }
                    }
                }
            case XROTATE:
            case YROTATE:
            case ZROTATE:
                switch(part) {
                    case HEAD:
                    case ALL:
                        armorStand.setHeadPose(rotate(modifiedMode,rightClick,stepInDegree,armorStand.getHeadPose()));
                        break;
                    case RARM:
                        armorStand.setRightArmPose(rotate(modifiedMode,rightClick,stepInDegree,armorStand.getRightArmPose()));
                        break;
                    case LARM:
                        armorStand.setLeftArmPose(rotate(modifiedMode,rightClick,stepInDegree,armorStand.getLeftArmPose()));
                        break;
                    case LLEG:
                        armorStand.setLeftLegPose(rotate(modifiedMode,rightClick,stepInDegree,armorStand.getLeftLegPose()));
                        break;
                    case RLEG:
                        armorStand.setRightLegPose(rotate(modifiedMode,rightClick,stepInDegree,armorStand.getRightLegPose()));
                        break;
                    case BODY:
                        armorStand.setBodyPose(rotate(modifiedMode,rightClick,stepInDegree,armorStand.getBodyPose()));
                }
                break;
            case TURN:
                armorStand.teleport(turn(armorStand.getLocation(),rightClick,stepInDegree));
                break;
            case XMOVE:
                armorStand.teleport(moveX(armorStand.getLocation(),rightClick,stepInDegree));
                break;
            case YMOVE:
                armorStand.teleport(moveY(armorStand.getLocation(),rightClick,stepInDegree));
                break;
            case ZMOVE:
                armorStand.teleport(moveZ(armorStand.getLocation(),rightClick,stepInDegree));
                break;
            case MOVE:
                playerLoc = player.getLocation();
                float yaw = playerLoc.getYaw();
                while(yaw<0) {
                    yaw+=360;
                }
                while(yaw>360) {
                    yaw-=360;
                }
                if(yaw<45 || yaw>315 || (yaw>135 && yaw<225)) {
                    if(yaw>135 && yaw<225) {
                        rightClick=!rightClick;
                    }
                    armorStand.teleport(moveX(armorStand.getLocation(),rightClick,stepInDegree));
                } else {
                    if(!(yaw>45 && yaw<135)) {
                        rightClick=!rightClick;
                    }
                    armorStand.teleport(moveZ(armorStand.getLocation(),rightClick,stepInDegree));
                }
                break;
            case SIZE:
                armorStand.setSmall(!armorStand.isSmall());
                PluginData.getMessageUtil().sendInfoMessage(player,"Switched armor stand size.");
                break;
            case SCALE:
                AttributeInstance scaleAttribute = armorStand.getAttribute(Attribute.SCALE);
                if(scaleAttribute != null) {
                    double newScale = calculateScale(scaleAttribute.getBaseValue(), rightClick, stepInDegree,
                            PluginData.getOrCreateWorldConfig(armorStand.getWorld().getName()).getArmorStandMinScale(),
                            PluginData.getOrCreateWorldConfig(armorStand.getWorld().getName()).getArmorStandMaxScale());
                    scaleAttribute.setBaseValue(newScale);
                }
                break;
            case VISIBLE:
                armorStand.setVisible(!armorStand.isVisible());
                PluginData.getMessageUtil().sendInfoMessage(player,"Switched visibility.");
                break;
            case LOCK:
                if(armorStand.getScoreboardTags().contains("LOCKED")) {
                    ArmorStandUtil.lockArmorStand(armorStand, false);
                    armorStand.setVisible(true);
                    PluginData.getMessageUtil().sendInfoMessage(player,"Armor stand unlocked.");
                } else {
                    ArmorStandUtil.lockArmorStand(armorStand, true);
                    PluginData.getMessageUtil().sendInfoMessage(player,"Armor stand locked.");
                }
                break;
            case GRAVITY:
                armorStand.setGravity(!armorStand.hasGravity());
                PluginData.getMessageUtil().sendInfoMessage(player,"Switched armor stand affected by gravity.");
                break;
            case BASE:
                armorStand.setBasePlate(!armorStand.hasBasePlate());
                PluginData.getMessageUtil().sendInfoMessage(player,"Switched armor stand base plate.");
                break;
            case MARKER:
                //armorStand.setMarker(!armorStand.isMarker());
                PluginData.getMessageUtil().sendErrorMessage(player,"This mode is deactivated.");
                break;
            case ARMS:
                armorStand.setArms(!armorStand.hasArms());
                PluginData.getMessageUtil().sendInfoMessage(player,"Switched armor stand arms.");
                break;
            case PASTE:
                //nothing to do here
                break;
            case COPY:
                config.copyArmorStand(player, armorStand);
                sendCopyMessage(player);
                break;
            case STRAIGHT:
                switch(part) {
                    case HEAD:
                        armorStand.setHeadPose(new EulerAngle(0,0,0));
                        break;
                    case RARM:
                        armorStand.setRightArmPose(new EulerAngle(0,0,0));
                        break;
                    case LARM:
                        armorStand.setLeftArmPose(new EulerAngle(0,0,0));
                        break;
                    case LLEG:
                        armorStand.setLeftLegPose(new EulerAngle(0,0,0));
                        break;
                    case RLEG:
                        armorStand.setRightLegPose(new EulerAngle(0,0,0));
                        break;
                    case BODY:
                        armorStand.setBodyPose(new EulerAngle(0,0,0));
                    case ALL:
                        armorStand.setHeadPose(new EulerAngle(0,0,0));
                        armorStand.setRightArmPose(new EulerAngle(0,0,0));
                        armorStand.setLeftArmPose(new EulerAngle(0,0,0));
                        armorStand.setLeftLegPose(new EulerAngle(0,0,0));
                        armorStand.setRightLegPose(new EulerAngle(0,0,0));
                        armorStand.setBodyPose(new EulerAngle(0,0,0));
                        break;
                }
                break;
        }
        return true;
    }

    private EulerAngle rotate(ArmorStandEditorMode mode, boolean positive, int stepInDegree, EulerAngle previous) {
        double step = stepInDegree*3.1412/180;
        if(!positive) {
            step *= -1;
        }
        EulerAngle newAngle;
        switch(mode) {
            case XROTATE:
                newAngle = new EulerAngle(previous.getX()+step,previous.getY(),previous.getZ());
                break;
            case YROTATE:
                newAngle = new EulerAngle(previous.getX(),previous.getY()+step,previous.getZ());
                break;
            case ZROTATE:
                newAngle = new EulerAngle(previous.getX(),previous.getY(),previous.getZ()+step);
                break;
            default:
                newAngle = EulerAngle.ZERO;
        }
        return newAngle;
    }
    
    private Location turn(Location oldLoc, boolean positive, int stepInDegree) {
        float step = stepInDegree;
        if(!positive) {
            step *= -1;
        }
        oldLoc.setYaw(oldLoc.getYaw()+step);
        return oldLoc;
    }

    private Location moveX(Location location, boolean rightClick, int stepInDegree) {
        double step = stepInDegree/100d;
        if(!rightClick) {
            step *= -1;
        }
        location.setX(location.getX()+step);
        return location;
    }

    private Location moveY(Location location, boolean rightClick, int stepInDegree) {
        double step = stepInDegree/100d;
        if(!rightClick) {
            step *= -1;
        }
        location.setY(location.getY()+step);
        return location;
    }

    private Location moveZ(Location location, boolean rightClick, int stepInDegree) {
        double step = stepInDegree/100d;
        if(!rightClick) {
            step *= -1;
        }
        location.setZ(location.getZ()+step);
        return location;
    }

    private double calculateScale(double oldScale, boolean rightClick, int stepInDegree, double min, double max) {
        double value;
        if(rightClick) {
            value = oldScale * (1d + stepInDegree / 100d);
        } else {
            value = oldScale * (1d - stepInDegree / 100d);
        }
        if(value < Math.max(0.0625, min)) value = Math.max(0.0625, min);
        if(value > Math.min(16, max)) value = Math.min(16, max);
        return value;
    }

    private void sendCopyMessage(Player player) {
        PluginData.getMessageUtil().sendInfoMessage(player,"Armor stand copied to clipboard.");
    }

    private void sendLockedMessage(Player player) {
        PluginData.getMessageUtil().sendErrorMessage(player,"This armor stand is locked! Use /armor l to unlock.");
    }
}