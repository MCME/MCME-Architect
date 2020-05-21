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
package com.mcmiddleearth.architect.voxelStencilEditor;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class StencilList {
    
    private final String name;
    
    private final List<String> stencils= new ArrayList<>();

    public StencilList(String name) {
        this.name = name;
    }
    
    public static StencilList loadFromFile(String name) { 
        FileReader fr = null;
        try {
            File file = new File(VoxelConstants.STENCIL_LISTS_DIR,
                                 name + "."+VoxelConstants.STENCIL_LIST_EXT);
            if(!file.exists()) {
                return null;
            }
            StencilList newList = new StencilList(name);
            fr = new FileReader(file);
            try (Scanner scanner = new Scanner(fr)) {
                while(scanner.hasNext()) {
                    String stencilName = scanner.nextLine();
                    stencilName = stencilName.substring(0,stencilName.lastIndexOf("."));
                    newList.addStencil(stencilName);
                }
            }
            return newList;
        } catch (FileNotFoundException ex) {
            Logger.getLogger(StencilList.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        } finally {
            try {
                if(fr != null) {
                    fr.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(StencilList.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public boolean addStencil(String stencilName) {
        File file = new File(VoxelConstants.STENCILS_DIR+"/"
                             +stencilName + "."+VoxelConstants.STENCIL_EXT);
        if(!file.exists()) {
            return false;
        }
        stencils.add(stencilName);
        return true;
    }
    
    public boolean removeStencil(String stencilName) {
        return stencils.remove(stencilName);
    }

    public boolean fileExists() {
        return getFile().exists();
    }
    
    private File getFile() {
        return new File(VoxelConstants.STENCIL_LISTS_DIR,
                                 name + "."+VoxelConstants.STENCIL_LIST_EXT);
    }
    
    public boolean saveToFile() {
        FileWriter fw = null;
        try {
            File file = getFile();
            if(!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            file.createNewFile();
            fw = new FileWriter(file, false);
            try (PrintWriter writer = new PrintWriter(fw)) {
                Collections.sort(stencils);
                for (String s : stencils) {
                    writer.println(s);
                }   
                writer.flush();
            }
            return true;
        } catch (IOException ex) {
            Logger.getLogger(StencilList.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        } finally {
            try {
                if(fw != null) {
                    fw.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(StencilList.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public String getName() {
        return name;
    }

    public List<String> getStencils() {
        return stencils;
    }
}
