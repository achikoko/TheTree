package io.github.achikoko.thetree;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.achikoko.thetree.config.NodeConfig;
import io.github.achikoko.thetree.config.TheTreeConfig;
import io.github.achikoko.thetree.config.TheTreeConfigService;
import io.github.achikoko.thetree.settings.TheTreeConfigState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves a node's configured {@code /weight} directly from a {@link VirtualFile}, independent of
 * which concrete tree-node class currently wraps that file. This is what lets option B
 * ({@link io.github.achikoko.thetree.view.TheTreeProjectViewPane}) order nodes from the comparator
 * even after a downstream {@code TreeStructureProvider} rebuilt them and dropped our node subclass.
 */
public final class NodeWeights {

    /** The platform's own weight for a directory — what an unconfigured folder keeps. */
    public static final int DEFAULT_FOLDER_WEIGHT = 20;

    /** The platform's own weight for a file — what an unconfigured file keeps. */
    public static final int DEFAULT_FILE_WEIGHT = 30;

    private NodeWeights() {
    }

    /**
     * Resolves the {@code /weight} declared for {@code file} in the {@code .thetree.yaml} of its
     * content root.
     *
     * @return the configured weight, or the platform default for the node's kind
     *         ({@link #DEFAULT_FOLDER_WEIGHT} / {@link #DEFAULT_FILE_WEIGHT}) when the file is
     *         unknown, the config toggle is off, no config file exists, or the branch is not
     *         described.
     */
    public static int weightFor(@NotNull Project project, @Nullable VirtualFile file) {
        if (file == null) {
            return DEFAULT_FILE_WEIGHT;
        }
        int fallback = defaultWeightFor(file);
        TheTreeConfigState state = TheTreeConfigState.getInstance(project);
        if (!state.getUseConfigFile()) {
            return fallback;
        }
        VirtualFile contentRoot = ProjectFileIndex.getInstance(project).getContentRootForFile(file);
        if (contentRoot == null) {
            return fallback;
        }
        TheTreeConfig config = TheTreeConfigService.getInstance(project).getConfig(contentRoot);
        if (!config.isPresent()) {
            return fallback;
        }
        String relative = PathUtil.relativeIfUnder(file.getPath(), contentRoot.getPath());
        if (relative == null || relative.isEmpty()) {
            return fallback;
        }
        NodeConfig cfg = config.resolve(relative);
        Integer weight = cfg == null ? null : cfg.weightOrNull();
        return weight == null ? fallback : weight;
    }

    /** The weight an unconfigured node of this kind carries on the platform's own scale. */
    public static int defaultWeightFor(@NotNull VirtualFile file) {
        return file.isDirectory() ? DEFAULT_FOLDER_WEIGHT : DEFAULT_FILE_WEIGHT;
    }
}
