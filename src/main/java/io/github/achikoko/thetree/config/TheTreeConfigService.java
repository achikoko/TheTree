package io.github.achikoko.thetree.config;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Project service that loads and caches the {@code .thetree.yaml} file living directly inside a
 * module content root. The cache is keyed by content-root URL and invalidated by comparing the
 * file's modification stamp, so external edits are picked up automatically.
 */
public final class TheTreeConfigService {

    private static final String CONFIG_FILE_NAME = ".thetree.yaml";

    private static final Logger LOG = Logger.getInstance(TheTreeConfigService.class);

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    @SuppressWarnings("unused")
    public TheTreeConfigService(Project project) {
        // Project is injected by the platform; kept for symmetry / future use.
    }

    public static TheTreeConfigService getInstance(Project project) {
        return project.getService(TheTreeConfigService.class);
    }

    /**
     * Returns the parsed config for the given content root, loading/reloading as needed.
     * Never returns null; an absent or unreadable file yields {@link TheTreeConfig#EMPTY}.
     */
    @NotNull
    public TheTreeConfig getConfig(@NotNull VirtualFile contentRoot) {
        VirtualFile file = contentRoot.findChild(CONFIG_FILE_NAME);
        String key = contentRoot.getUrl();

        if (file == null || !file.isValid() || file.isDirectory()) {
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
        VirtualFile file = contentRoot.findChild(CONFIG_FILE_NAME);
        return file != null && file.isValid() && !file.isDirectory();
    }

    public void invalidate() {
        cache.clear();
    }

    private static TheTreeConfig read(VirtualFile file) {
        try {
            String text = new String(file.contentsToByteArray(), file.getCharset());
            return TheTreeConfigParser.parse(text);
        } catch (IOException | RuntimeException e) {
            LOG.warn("The Tree: failed to read " + file.getPath(), e);
            return TheTreeConfig.EMPTY;
        }
    }

    public static @NotNull String configFileName() {
        return CONFIG_FILE_NAME;
    }

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
