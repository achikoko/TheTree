package io.github.achikoko.thetree;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Small path helpers. The relative/parent logic is adapted from
 * {@code com.intellij.history.core.Paths} (as used by the original FolderCompact plugin).
 */
public final class PathUtil {

    private static final boolean CASE_SENSITIVE = SystemInfo.isFileSystemCaseSensitive;

    private PathUtil() {
    }
    
    /**
     * Returns {@code path} expressed relative to {@code root}, or {@code null} if {@code path}
     * is not located under {@code root}. Returns an empty string if they are equal.
     */
    public static String relativeIfUnder(String path, String root) {
        if (!isParent(root, path)) {
            return null;
        }
        path = path.substring(root.length());
        if (path.isEmpty()) {
            return "";
        }
        return path.charAt(0) != '/' ? null : path.substring(1);
    }

    /** @return true if {@code path} equals {@code parent} or lives underneath it. */
    public static boolean isParent(String parent, String path) {
        if (equals(parent, path)) {
            return true;
        }
        parent = appendSlash(parent);
        return CASE_SENSITIVE ? path.startsWith(parent) : StringUtil.startsWithIgnoreCase(path, parent);
    }

    /** Path equality, respecting whether the running filesystem is case sensitive. */
    public static boolean equals(String p1, String p2) {
        return CASE_SENSITIVE ? p1.equals(p2) : p1.equalsIgnoreCase(p2);
    }

    /** Ensures a non-empty path ends with a single {@code /} so prefix tests are segment-safe. */
    private static String appendSlash(String parent) {
        if (parent.isEmpty()) {
            return parent;
        }
        if (parent.charAt(parent.length() - 1) != '/') {
            parent = parent + "/";
        }
        return parent;
    }

    /**
     * Splits a "/"-separated relative path into its non-empty segments.
     */
    public static List<String> segments(String relativePath) {
        List<String> out = new ArrayList<>();
        if (relativePath == null) {
            return out;
        }
        for (String s : relativePath.split("/")) {
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }
}
