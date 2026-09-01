package io.github.achikoko.thetree.view;

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.achikoko.thetree.NodeWeights;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * Orders siblings by their configured {@code /weight} (lower renders higher), falling back to the
 * platform's normal comparator (folders-on-top / name / type) for equal weight.
 *
 * <p>Because the weight is looked up from each node's {@link VirtualFile} rather than read off a
 * node subclass, this works regardless of which concrete node class ended up wrapping the file, and
 * it dominates in every sort mode — so a file with a negative weight can rise above a folder even
 * when "Folders Always on Top" is off.
 */
final class TheTreeNodeComparator implements Comparator<NodeDescriptor<?>> {

    private final Project project;
    private final Comparator<? super NodeDescriptor<?>> fallback;

    /**
     * @param project  used to resolve each node's weight from its content root's config.
     * @param fallback the platform comparator applied when two nodes share a weight.
     */
    TheTreeNodeComparator(@NotNull Project project,
                          @NotNull Comparator<? super NodeDescriptor<?>> fallback) {
        this.project = project;
        this.fallback = fallback;
    }

    /** Compares by configured weight first, then defers to the platform's own ordering. */
    @Override
    public int compare(NodeDescriptor<?> a, NodeDescriptor<?> b) {
        int wa = weightOf(a);
        int wb = weightOf(b);
        if (wa != wb) {
            return Integer.compare(wa, wb); // lower weight renders higher
        }
        return fallback.compare(a, b);
    }

    /** @return the descriptor's configured weight, or a default for nodes with no file behind them. */
    private int weightOf(NodeDescriptor<?> descriptor) {
        if (!(descriptor instanceof ProjectViewNode)) {
            return NodeWeights.DEFAULT_FILE_WEIGHT;
        }
        VirtualFile file = ((ProjectViewNode<?>) descriptor).getVirtualFile();
        return NodeWeights.weightFor(project, file);
    }
}
