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
 * Resolves a node's configured {@code :weight} directly from a {@link VirtualFile}, independent of
 * which concrete tree-node class currently wraps that file. This is what lets option B
 * ({@link io.github.achikoko.thetree.view.TheTreeProjectViewPane}) order nodes from the comparator
 * even after a downstream {@code TreeStructureProvider} rebuilt them and dropped our node subclass.
 */
public final class NodeWeights {

    /** The spec default: an unmarked node has weight 0 (lower renders higher). */
    public static final int DEFAULT_WEIGHT = 0;

    private NodeWeights() {
    }

    public static int weightFor(@NotNull Project project, @Nullable VirtualFile file) {
        if (file == null) {
            return DEFAULT_WEIGHT;
        }
        TheTreeConfigState state = TheTreeConfigState.getInstance(project);
        if (!state.getUseConfigFile()) {
            return DEFAULT_WEIGHT;
        }
        VirtualFile contentRoot = ProjectFileIndex.getInstance(project).getContentRootForFile(file);
        if (contentRoot == null) {
            return DEFAULT_WEIGHT;
        }
        TheTreeConfig config = TheTreeConfigService.getInstance(project).getConfig(contentRoot);
        if (!config.isPresent()) {
            return DEFAULT_WEIGHT;
        }
        String relative = PathUtil.relativeIfUnder(file.getPath(), contentRoot.getPath());
        if (relative == null || relative.isEmpty()) {
            return DEFAULT_WEIGHT;
        }
        NodeConfig cfg = config.resolve(relative);
        Integer weight = cfg == null ? null : cfg.weightOrNull();
        return weight == null ? DEFAULT_WEIGHT : weight;
    }
}
