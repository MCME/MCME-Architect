package com.mcmiddleearth.architect.slabSmooth.core;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Files of a resource pack (or the vanilla client jar), by path inside it ("assets/minecraft/..."). */
public interface Resources extends Closeable {

    /** The file's bytes, or null if it isn't there. */
    byte[] read(String path);

    @Override
    default void close() throws IOException {
    }

    /** A zip file: a pack (its pack.mcmeta may sit in a folder inside the zip) or the client jar. */
    static Resources zip(Path file) throws IOException {
        ZipFile zip = new ZipFile(file.toFile());
        String root = "";
        int depth = Integer.MAX_VALUE;
        for (var entries = zip.entries(); entries.hasMoreElements(); ) {
            String name = entries.nextElement().getName();
            if (name.startsWith("assets/")) { // assets at the top: no folder to look into
                root = "";
                break;
            }
            if (name.equals("pack.mcmeta") || name.endsWith("/pack.mcmeta")) {
                int d = name.split("/").length;
                if (d < depth) {
                    depth = d;
                    root = name.substring(0, name.length() - "pack.mcmeta".length());
                }
            }
        }
        String prefix = root;
        return new Resources() {
            @Override
            public synchronized byte[] read(String path) {
                ZipEntry e = zip.getEntry(prefix + path);
                if (e == null || e.isDirectory()) {
                    return null;
                }
                try (InputStream in = zip.getInputStream(e)) {
                    return in.readAllBytes();
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }

            @Override
            public void close() throws IOException {
                zip.close();
            }
        };
    }

    static Resources dir(Path root) {
        return path -> {
            Path p = root.resolve(path);
            try {
                return Files.isRegularFile(p) ? Files.readAllBytes(p) : null;
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        };
    }

    /** The first of the sources that has the file: a pack over the vanilla jar, like the client does. */
    static Resources layered(List<Resources> sources) {
        return new Resources() {
            @Override
            public byte[] read(String path) {
                for (Resources r : sources) {
                    byte[] b = r.read(path);
                    if (b != null) {
                        return b;
                    }
                }
                return null;
            }

            @Override
            public void close() throws IOException {
                for (Resources r : sources) {
                    r.close();
                }
            }
        };
    }
}
