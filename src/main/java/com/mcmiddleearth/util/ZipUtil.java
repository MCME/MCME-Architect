/*
 * Copyright (C) 2017 MCME
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
package com.mcmiddleearth.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 *
 * @author Eriol_Eandur
 */
public class ZipUtil {

    public static synchronized int extract(String sourceURL, InputStream in, 
                               String matchStart, File outPath) throws IOException{
        int downloadedFiles = 0;
        URL url = new URL(sourceURL);
        BufferedOutputStream dest = null;
        ZipInputStream zin = null;
//Logger.getGlobal().info("outpath "+outPath.getAbsolutePath());
//Logger.getGlobal().info("temp "+temp.getAbsolutePath());
        if(!outPath.exists()) {
            outPath.mkdir();
        }
        File temp = new File(outPath,"temp");
        if(temp.exists()) {
            File[] files = temp.listFiles();
            for(File file: files) {
                file.delete();
            }
//Logger.getGlobal().info("cleared dir");
        } else {
            temp.mkdir();
        }
        try{
//Logger.getGlobal().info("url "+url.toString());
            zin = new ZipInputStream(url.openStream());
//Logger.getGlobal().info("zin "+zin.available());
            in = zin;
            ZipEntry entry = zin.getNextEntry();
//Logger.getGlobal().info("entry "+entry);
            /*try {
                Thread.sleep(5000);
            } catch (InterruptedException ex) {
                Logger.getLogger(ZipUtil.class.getName()).log(Level.SEVERE, null, ex);
            }*/
            while(entry != null) {
                //System.out.println(entry.getName());
//Logger.getGlobal().info("entry "+entry.getName());
                if(!entry.isDirectory() && entry.getName().startsWith(matchStart)) {
                    int BUFFER = 2048;
                    File file = new File(temp,entry.getName()
                                              .substring(entry.getName().lastIndexOf("/")+1));
                    //Files.move(file.toPath(),file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    //if(!file.exists()) file.mkdirs();
                    FileOutputStream fos = new FileOutputStream(file);
                    dest = new BufferedOutputStream(fos, BUFFER);
                    int count;
                    byte[] data = new byte[BUFFER];
                    while ((count = zin.read(data, 0, BUFFER)) != -1) {
                       dest.write(data, 0, count);
                    }
                    dest.flush();
                    dest.close();
                    fos.close();
                    //System.out.println("extracted");
                }
                entry = zin.getNextEntry();
            }
//Logger.getGlobal().info("search for "+matchStart);
//Logger.getGlobal().info("temp later "+temp);
            File[] files = temp.listFiles();
//Logger.getGlobal().info("temp files "+files);
            if(files!=null) {
                downloadedFiles = files.length;
                for(File file: outPath.listFiles()) {
                    file.delete();
                }
                for(File file: temp.listFiles()) {
//Logger.getGlobal().info("toPath "+file.toPath());
//Logger.getGlobal().info("fromPath "+new File(outPath,file.getName()));
                    Files.move(file.toPath(),new File(outPath,file.getName()).toPath(), 
                               StandardCopyOption.REPLACE_EXISTING);
                }
            }
            temp.delete();
        } 
        finally {
            if(zin!=null) zin.close();
            if(dest!=null) dest.close();
        }
        return downloadedFiles;
    }
    
    /*private static Path download(String sourceURL, String targetDirectory) throws IOException
    {
        URL url = new URL(sourceURL);
        String fileName = sourceURL.substring(sourceURL.lastIndexOf('/') + 1, sourceURL.length());
        Path targetPath = new File(targetDirectory + File.separator + fileName).toPath();
        Files.copy(url.openStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        return targetPath;
    }*/

}
