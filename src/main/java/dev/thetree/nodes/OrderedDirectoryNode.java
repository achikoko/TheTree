package dev.thetree.nodes;

import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;

/**
 * A plain directory node that additionally carries a configured ordering weight. Used to
 * decorate ordinary (non-compacted) folders so their {@code :weight} from {@code .thetree.yaml}
 * takes effect. Children are produced normally by the platform and re-processed by the
 * tree structure provider, so nested weights/hidden flags keep working.
 */
public final class OrderedDirectoryNode extends PsiDirectoryNode {

    private final int weight;

    public OrderedDirectoryNode(PsiDirectoryNode origin, int weight) {
        super(origin.getProject(), origin.getValue(), origin.getSettings(), origin.getFilter());
        this.weight = weight;
    }

    @Override
    public int getWeight() {
        return Ordering.weight(weight, true);
    }

    @Override
    public int getTypeSortWeight(boolean sortByType) {
        return Ordering.typeSortWeight(weight, true);
    }
}
