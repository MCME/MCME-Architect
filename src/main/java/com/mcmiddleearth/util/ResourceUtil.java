package com.mcmiddleearth.util;

import com.mcmiddleearth.architect.ArchitectPlugin;

import java.io.*;
import java.util.logging.Logger;

public class ResourceUtil {

    public static void saveResourceToFile(String resource, File file) {
        InputStream inputStream = ArchitectPlugin.class.getResourceAsStream("/" + resource);
        if (!file.exists()) {
            if (inputStream == null) {
                Logger.getGlobal().severe("resource " + resource + " not found in plugin jar");
            } else {
                try {
                    if (file.createNewFile()) {
                        InputStreamReader in = new InputStreamReader(inputStream);

                        try {
                            FileWriter fw = new FileWriter(file);

                            try {
                                char[] buf = new char[1024];
                                int read = 1;

                                while(read > 0) {
                                    read = in.read(buf);
                                    if (read > 0) {
                                        fw.write(buf, 0, read);
                                    }
                                }

                                fw.flush();
                            } catch (Throwable var10) {
                                try {
                                    fw.close();
                                } catch (Throwable var9) {
                                    var10.addSuppressed(var9);
                                }

                                throw var10;
                            }

                            fw.close();
                        } catch (Throwable var11) {
                            try {
                                in.close();
                            } catch (Throwable var8) {
                                var11.addSuppressed(var8);
                            }

                            throw var11;
                        }

                        in.close();
                    }
                } catch (IOException var12) {
                    Logger.getGlobal().severe("IOException: " + var12.getLocalizedMessage());
                }
            }
        }

    }

}
