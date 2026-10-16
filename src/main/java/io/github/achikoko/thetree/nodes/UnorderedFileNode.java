package io.github.achikoko.thetree.nodes;

import com.intellij.ide.projectView.impl.nodes.PsiFileNode;

/**
 * A file node with no configured weight, re-based onto the same scale as the weighted nodes.
 *
 * <p>The platform's own {@code getTypeSortWeight} returns a folders-first grouping value that would
 * dominate any {@code /weight} from {@code thetree.yaml}. Returning {@code getWeight()} instead
 * puts unconfigured nodes on the very scale the configured ones use, so a negative weight really
 * does lift a file above the folders — whatever "Folders Always on Top" is set to.
 */
public final class UnorderedFileNode extends PsiFileNode {
    
    /** @param origin the platform node being re-wrapped; its project/value/settings are reused. */
    public UnorderedFileNode(PsiFileNode origin) {
        super(origin.getProject(), origin.getValue(), origin.getSettings());
    }
    
    /** Sorts on the same scale as {@link #getWeight()} rather than the folders-first grouping. */
    @Override
    public int getTypeSortWeight(boolean sortByType) {
        return super.getWeight();
    }
    
}
