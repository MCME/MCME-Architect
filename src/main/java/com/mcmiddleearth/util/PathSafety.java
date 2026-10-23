/*
 * Copyright (C) 2026 MCME
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

import java.io.File;
import java.io.IOException;

/**
 * Guards against directory traversal. Every place that turns a user- or archive-supplied name into a
 * {@link File} inside a fixed base directory must go through {@link #resolveInside} so that inputs
 * such as {@code ../../server.properties} cannot escape the base.
 * <p>
 * The check is canonical-path containment, not name blocklisting: the intended file's absolute,
 * symlink-resolved path must equal the base directory or sit beneath it. This is immune to the
 * {@code ..} / encoding / symlink tricks that defeat character filtering.
 */
public final class PathSafety {

    private PathSafety() {}

    /** Resolve {@code childName + "." + extension} inside {@code baseDir}, verifying containment. */
    public static File resolveInside(File baseDir, String childName, String extension) {
        String ext = (extension == null) ? "" : "." + extension;
        return resolveInside(baseDir, childName + ext);
    }

    /**
     * Resolve {@code childName} inside {@code baseDir}, verifying containment.
     *
     * @throws SecurityException if {@code childName} is null/blank or resolves outside {@code baseDir}.
     */
    public static File resolveInside(File baseDir, String childName) {
        if (childName == null || childName.isBlank()) {
            throw new SecurityException("Empty file name is not allowed in " + baseDir);
        }
        File candidate = new File(baseDir, childName);
        if (!isInside(baseDir, candidate)) {
            throw new SecurityException("File name '" + childName
                    + "' resolves outside the allowed directory " + baseDir);
        }
        return candidate;
    }

    /** @return true iff {@code child}'s canonical path is {@code baseDir} itself or a descendant of it. */
    public static boolean isInside(File baseDir, File child) {
        try {
            String base = baseDir.getCanonicalPath();
            String target = child.getCanonicalPath();
            // Guard the sibling-prefix case ("/a/foo" must not contain "/a/foobar") with the separator.
            return target.equals(base) || target.startsWith(base + File.separator);
        } catch (IOException e) {
            // If the path cannot be canonicalised, treat it as unsafe.
            return false;
        }
    }

    /**
     * Whether a folder a player named, which may hold sub-folders separated by {@code /}, stays inside
     * {@code baseDir}. Null or blank means the base itself. A {@code \} counts as {@code /} on every OS, so a
     * Linux server and a Windows test run give the same answer. java.io puts an absolute name under the
     * base, as the file lists do; {@code Path.resolve} would not, so it is not used here.
     */
    public static boolean isInsideFolder(File baseDir, String folder) {
        if (folder == null || folder.isBlank()) {
            return true;
        }
        return isInside(baseDir, new File(baseDir, folder.replace('\\', '/')));
    }

    /**
     * Whether a player-given name can name a new file {@code name + "." + extension} directly in
     * {@code baseDir}: not blank, no {@code /} or {@code \}, not {@code .} or {@code ..}, and inside the base.
     */
    public static boolean isPlainName(File baseDir, String name, String extension) {
        if (name == null || name.isBlank() || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
                || name.equals(".") || name.equals("..")) {
            return false;
        }
        return isInside(baseDir, new File(baseDir, name + "." + extension));
    }
}
