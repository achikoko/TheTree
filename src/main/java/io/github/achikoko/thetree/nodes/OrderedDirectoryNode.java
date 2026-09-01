package io.github.achikoko.thetree.nodes;

import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;

/**
 * A plain directory node that additionally carries a configured ordering weight. Used to
 * decorate ordinary (non-compacted) folders so their {@code /weight} from {@code .thetree.yaml}
 * takes effect. Children are produced normally by the platform and re-processed by the
 * tree structure provider, so nested weights/hidden flags keep working.
 */
public final class OrderedDirectoryNode extends PsiDirectoryNode {

    private final int weight;

    /**
     * @param origin the platform node being re-wrapped.
     * @param weight the {@code /weight} resolved for this folder; lower renders higher.
     */
    public OrderedDirectoryNode(PsiDirectoryNode origin, int weight) {
        super(origin.getProject(), origin.getValue(), origin.getSettings(), origin.getFilter());
        this.weight = weight;
    }
    
    /** @return the configured weight (used when "Folders Always on Top" is off). */
    @Override
    public int getWeight() {
        return weight;
    }
    
    /** @return the configured weight, so ordering holds with "Folders Always on Top" too. */
    @Override
    public int getTypeSortWeight(boolean sortByType) {
        return weight;
    }
    
}
