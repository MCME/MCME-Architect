package com.mcmiddleearth.architect.displayEntity;

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
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;

public class DisplayEntityListener implements Listener {

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
        DisplayEntityEditorConfig config = DisplayEntityEditorCommand.getPlayerConfig(p);
        if(PluginData.isModuleEnabled(p.getWorld(),Modules.ARMOR_STAND_EDITOR)
                && config.getEditorMode().equals(DisplayEntityEditorMode.PASTE)
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
            config.placeDisplayEntity(loc, exact);
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

    private boolean manipulate(Display displayEntity, Player player, boolean rightClick) {
        if(!PluginData.checkBuildPermissions(player, displayEntity.getLocation(),
                Permission.DISPLAY_ENTITY_EDITOR)) {
            return true;
        }
        DisplayEntityEditorConfig config = DisplayEntityEditorCommand.getPlayerConfig(player);
        DisplayEntityEditorMode mode = config.getEditorMode();
        if(DisplayEntityUtil.isLocked(displayEntity)
                && !(mode.equals(DisplayEntityEditorMode.LOCK)
                && player.getItemInHand().getType().equals(Material.STICK))) {
            sendLockedMessage(player);
            return true;
        }
        int stepInDegree = config.getRotationStep();
        if(!(player.getItemInHand().getType().equals(Material.STICK)
                || mode.equals(DisplayEntityEditorMode.HAND)
                || mode.equals(DisplayEntityEditorMode.OFF_HAND)
                || mode.equals(DisplayEntityEditorMode.HELMET))) {
            return false;
        }
        DisplayEntityEditorMode modifiedMode = mode;
//Logger.getGlobal().info("mode: "+mode.name());
        switch(mode) {
            case ROTATE:
                Location playerLoc = player.getLocation();
                if(playerLoc.getPitch()>45 || playerLoc.getPitch()<-45) {
                    modifiedMode = DisplayEntityEditorMode.YROTATE;
                    if(playerLoc.getPitch()<-45) {
                        rightClick=!rightClick;
                    }
                }
                else
                {
                    float yaw = playerLoc.getYaw() - displayEntity.getLocation().getYaw();
                    while(yaw<-180) {
                        yaw+=360;
                    }
                    while(yaw>180) {
                        yaw-=360;
                    }
                    if((yaw<45 && yaw>-45) || (yaw>135 || yaw<-135)) {
                        modifiedMode = DisplayEntityEditorMode.ZROTATE;
                        if(yaw<45 && yaw>-45) {
                            rightClick=!rightClick;
                        }
                    } else {
                        modifiedMode = DisplayEntityEditorMode.XROTATE;
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
                        displayEntity.setHeadPose(rotate(modifiedMode,rightClick,stepInDegree,displayEntity.getHeadPose()));
                        break;
                    case RARM:
                        displayEntity.setRightArmPose(rotate(modifiedMode,rightClick,stepInDegree,displayEntity.getRightArmPose()));
                        break;
                    case LARM:
                        displayEntity.setLeftArmPose(rotate(modifiedMode,rightClick,stepInDegree,displayEntity.getLeftArmPose()));
                        break;
                    case LLEG:
                        displayEntity.setLeftLegPose(rotate(modifiedMode,rightClick,stepInDegree,displayEntity.getLeftLegPose()));
                        break;
                    case RLEG:
                        displayEntity.setRightLegPose(rotate(modifiedMode,rightClick,stepInDegree,displayEntity.getRightLegPose()));
                        break;
                    case BODY:
                        displayEntity.setBodyPose(rotate(modifiedMode,rightClick,stepInDegree,displayEntity.getBodyPose()));
                }
                break;
            case TURN:
                displayEntity.teleport(turn(displayEntity.getLocation(),rightClick,stepInDegree));
                break;
            case XMOVE:
                displayEntity.teleport(moveX(displayEntity.getLocation(),rightClick,stepInDegree));
                break;
            case YMOVE:
                displayEntity.teleport(moveY(displayEntity.getLocation(),rightClick,stepInDegree));
                break;
            case ZMOVE:
                displayEntity.teleport(moveZ(displayEntity.getLocation(),rightClick,stepInDegree));
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
                    displayEntity.teleport(moveX(displayEntity.getLocation(),rightClick,stepInDegree));
                } else {
                    if(!(yaw>45 && yaw<135)) {
                        rightClick=!rightClick;
                    }
                    displayEntity.teleport(moveZ(displayEntity.getLocation(),rightClick,stepInDegree));
                }
                break;
            case SCALE:
                AttributeInstance scaleAttribute = displayEntity.getAttribute(Attribute.SCALE);
                if(scaleAttribute != null) {
                    double newScale = calculateScale(scaleAttribute.getBaseValue(), rightClick, stepInDegree,
                            PluginData.getOrCreateWorldConfig(displayEntity.getWorld().getName()).getArmorStandMinScale(),
                            PluginData.getOrCreateWorldConfig(displayEntity.getWorld().getName()).getArmorStandMaxScale());
                    scaleAttribute.setBaseValue(newScale);
                }
                break;
            case LOCK:
                if(displayEntity.getScoreboardTags().contains("LOCKED")) {
                    DisplayEntityUtil.lockDisplayEntity(displayEntity, false);
                    displayEntity.setVisible(true);
                    PluginData.getMessageUtil().sendInfoMessage(player,"Display Entity unlocked.");
                } else {
                    DisplayEntityUtil.lockDisplayEntity(displayEntity, true);
                    PluginData.getMessageUtil().sendInfoMessage(player,"Display Entity locked.");
                }
                break;
            case MARKER:
                //displayEntity.setMarker(!displayEntity.isMarker());
                PluginData.getMessageUtil().sendErrorMessage(player,"This mode is deactivated.");
                break;
            case PASTE:
                //nothing to do here
                break;
            case COPY:
                config.copyDisplayEntity(player, displayEntity);
                sendCopyMessage(player);
                break;
        }
        return true;
    }

    private EulerAngle rotate(DisplayEntityEditorMode mode, boolean positive, int stepInDegree, EulerAngle previous) {
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
