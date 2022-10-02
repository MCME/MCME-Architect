package com.mcmiddleearth.architect.specialBlockHandling.data;

import com.mcmiddleearth.architect.PluginData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Jubo
 */
public class SwitchStickData {

    private File file;
    private FileConfiguration config;

    private static Map<String,Object> switchStick = new HashMap<>();

    public SwitchStickData(){
        this.file = new File(PluginData.getSwitchStickDir(),"switchstick.yml");
        config = YamlConfiguration.loadConfiguration(file);
    }

    public static boolean isSwitchStick(String uuid){
        switchStick = PluginData.getSwitchStickMap();
        return switchStick.containsKey(uuid);
    }

    public void saveEntry(String uuid, Object bool){
        if(!switchStick.containsKey(uuid)){
            switchStick.put(uuid,bool);
        }else{
            switchStick.replace(uuid,bool);
        }
        if((Boolean) bool){
            config.set(uuid,bool);
        }else{
            config.set(uuid,null);
        }

        try {
            config.save(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
