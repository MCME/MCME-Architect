package com.mcmiddleearth.util;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Log;

import java.io.*;

public class ResourceUtil {

    public static void saveResourceToFile(String resource, File file) {
        InputStream inputStream = ArchitectPlugin.class.getResourceAsStream("/" + resource);
        if (!file.exists()) {
            if (inputStream == null) {
                Log.error("Resource '" + resource + "' not found in plugin jar; cannot create " + file.getAbsolutePath());
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
                    Log.error("Failed to copy resource '" + resource + "' to " + file.getAbsolutePath(), var12);
                }
            }
        }

    }

}
