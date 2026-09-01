package io.github.achikoko.thetree.view;

import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * EXPERIMENTAL (option B). A dedicated project-view pane that owns its sort comparator.
 *
 * <p>The comparator runs <em>after</em> the entire {@code TreeStructureProvider} chain, on whatever
 * final node wraps each file, and keys weight off the node's {@link com.intellij.openapi.vfs.VirtualFile}
 * (see {@link TheTreeNodeComparator} / {@link io.github.achikoko.thetree.NodeWeights}). That makes
 * ordering immune to downstream providers (Maven pom nodes, Java class nodes, ...) rebuilding our
 * nodes and dropping the {@code getWeight()}/{@code getTypeSortWeight()} overrides, and it applies in
 * every sort mode (Folders Always on Top on/off, sort by name/type).
 *
 * <p>Shows as a separate "The Tree" tab in the Project tool window; all The Tree structure providers
 * still apply inside it. Known rough edge while evaluating: "Select in Project View" / scroll-from-source
 * still targets the standard pane (inherited {@code createSelectInTarget}). To discard option B: remove
 * the {@code <projectViewPane>} line in plugin.xml and delete the {@code io.github.achikoko.thetree.view}
 * package plus {@link io.github.achikoko.thetree.NodeWeights}.
 */
public final class TheTreeProjectViewPane extends ProjectViewPane {

    public static final String ID = "TheTreePane";

    public TheTreeProjectViewPane(@NotNull Project project) {
        super(project);
    }

    @Override
    public @NotNull String getId() {
        return ID;
    }

    @Override
    public @NotNull String getTitle() {
        return "The Tree";
    }

    @Override
    public int getWeight() {
        return 10; // tab order: after the standard Project pane (weight 0)
    }

    @Override
    protected @NotNull Comparator<NodeDescriptor<?>> createComparator() {
        return new TheTreeNodeComparator(myProject, super.createComparator());
    }
}
