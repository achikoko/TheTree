package io.github.achikoko.thetree.nodes;

import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;

/**
 * A directory node with no configured weight, re-based onto the same scale as the weighted nodes.
 * The directory counterpart of {@link UnorderedFileNode} — see there for why the override matters.
 */
public final class UnorderedDirectoryNode extends PsiDirectoryNode {
    
    /** @param origin the platform node being re-wrapped; its project/value/settings/filter are reused. */
    public UnorderedDirectoryNode(PsiDirectoryNode origin) {
        super(origin.getProject(), origin.getValue(), origin.getSettings(), origin.getFilter());
    }
    
    /** Sorts on the same scale as {@link #getWeight()} rather than the folders-first grouping. */
    @Override
    public int getTypeSortWeight(boolean sortByType) {
        return super.getWeight();
    }
    
}
