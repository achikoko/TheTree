package io.github.achikoko.thetree.config;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Project service that loads and caches the {@code thetree.yaml} file living directly inside a
 * module content root. The legacy {@code .thetree.yaml} is still read when no {@code thetree.yaml}
 * sits next to it, but is discouraged. The cache is keyed by content-root URL and invalidated by comparing the
 * file's modification stamp, so external edits are picked up automatically.
 */
public final class TheTreeConfigService {

    /** Accepted config file names, preferred first: the dotted one is legacy (0.1). */
    private static final List<String> CONFIG_FILE_NAMES = List.of("thetree.yaml", ".thetree.yaml");

    private static final Logger LOG = Logger.getInstance(TheTreeConfigService.class);

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    @SuppressWarnings("unused")
    public TheTreeConfigService(Project project) {
        // Project is injected by the platform; kept for symmetry / future use.
    }

    /** @return the project-level instance of this service. */
    public static TheTreeConfigService getInstance(Project project) {
        return project.getService(TheTreeConfigService.class);
    }

    /**
     * Returns the parsed config for the given content root, loading/reloading as needed.
     * Never returns null; an absent or unreadable file yields {@link TheTreeConfig#EMPTY}.
     */
    @NotNull
    public TheTreeConfig getConfig(@NotNull VirtualFile contentRoot) {
        VirtualFile file = findConfigFile(contentRoot);
        String key = contentRoot.getUrl();

        if (file == null) {
            cache.remove(key);
            return TheTreeConfig.EMPTY;
        }

        long stamp = file.getModificationStamp();
        Cached cached = cache.get(key);
        if (cached != null && cached.stamp == stamp && cached.fileUrl.equals(file.getUrl())) {
            return cached.config;
        }

        TheTreeConfig parsed = read(file);
        cache.put(key, new Cached(file.getUrl(), stamp, parsed));
        return parsed;
    }

    /** Whether a content root has a config file at all (used to decide when to decorate). */
    public boolean hasConfig(@NotNull VirtualFile contentRoot) {
        return findConfigFile(contentRoot) != null;
    }

    /** The first accepted config file present in {@code contentRoot}, or {@code null} if none is. */
    private static VirtualFile findConfigFile(@NotNull VirtualFile contentRoot) {
        for (String name : CONFIG_FILE_NAMES) {
            VirtualFile file = contentRoot.findChild(name);
            if (file != null && file.isValid() && !file.isDirectory()) {
                return file;
            }
        }
        return null;
    }

    /** Drops every cached config; called when a config file changes on disk. */
    public void invalidate() {
        cache.clear();
    }

    /** Reads and parses the file, degrading to {@link TheTreeConfig#EMPTY} on any I/O failure. */
    private static TheTreeConfig read(VirtualFile file) {
        if (!file.getName().equals(CONFIG_FILE_NAMES.get(0))) {
            LOG.warn("The Tree: " + file.getPath() + " uses a discouraged name; rename it to "
                + CONFIG_FILE_NAMES.get(0) + ".");
        }
        try {
            String text = new String(file.contentsToByteArray(), file.getCharset());
            return TheTreeConfigParser.parse(text);
        } catch (IOException | RuntimeException e) {
            LOG.warn("The Tree: failed to read " + file.getPath(), e);
            return TheTreeConfig.EMPTY;
        }
    }

    /** Whether {@code name} is one of the config file names The Tree looks for in content roots. */
    public static boolean isConfigFileName(@NotNull String name) {
        return CONFIG_FILE_NAMES.contains(name);
    }

    /** One cache entry: the parsed config plus the file identity/stamp it was parsed from. */
    private static final class Cached {
        final String fileUrl;
        final long stamp;
        final TheTreeConfig config;

        Cached(String fileUrl, long stamp, TheTreeConfig config) {
            this.fileUrl = fileUrl;
            this.stamp = stamp;
            this.config = config;
        }
    }
}
