package dev.thetree.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * A virtual folder named "Uncompacted" that groups the non-source sibling folders which would
 * otherwise be surfaced at the top of a module (e.g. {@code src/main/protobuf} when protobuf is
 * not a source root and is not declared {@code :compacted: true}). This keeps the top level of
 * the tree limited to source roots and explicitly compacted folders.
 *
 * <p>The node sinks to the bottom of its sibling list by default (large ordering weight).
 */
public final class UncompactedFolderNode extends ProjectViewNode<String> {

    private static final String NAME = "Uncompacted";
    /** Default weight that pushes this node below regular content. */
    private static final int BOTTOM_WEIGHT = 5_000;

    private final List<AbstractTreeNode<?>> children;

    public UncompactedFolderNode(Project project, ViewSettings settings, List<AbstractTreeNode<?>> children) {
        super(project, NAME, settings);
        this.children = children;
    }

    @NotNull
    @Override
    public Collection<? extends AbstractTreeNode<?>> getChildren() {
        return children;
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
        for (AbstractTreeNode<?> child : children) {
            if (child instanceof ProjectViewNode && ((ProjectViewNode<?>) child).contains(file)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void update(@NotNull PresentationData presentation) {
        presentation.setPresentableText(NAME);
        presentation.setIcon(PlatformIcons.FOLDER_ICON);
    }

    @Override
    public int getWeight() {
        return Ordering.weight(BOTTOM_WEIGHT, true);
    }

    @Override
    public int getTypeSortWeight(boolean sortByType) {
        return Ordering.typeSortWeight(BOTTOM_WEIGHT, true);
    }

    @Override
    public boolean equals(Object object) {
        return this == object;
    }

    @Override
    public boolean canRepresent(Object element) {
        return false;
    }
}
