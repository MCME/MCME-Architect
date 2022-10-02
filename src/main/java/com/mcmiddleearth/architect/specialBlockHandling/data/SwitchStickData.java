package com.mcmiddleearth.architect.specialBlockHandling.data;

import com.mcmiddleearth.architect.PluginData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 *
 * @author Jubo
 */
public class SwitchStickData {

    private File file;
    private FileConfiguration config;

    public SwitchStickData(){
        this.file = new File(PluginData.getSwitchStickDir(),"switchstick.yml");
        config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isSwitchStick(String uuid){
        return config.contains(uuid);
    }

    public void saveEntry(String uuid, Object bool){

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
