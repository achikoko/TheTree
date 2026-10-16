package io.github.achikoko.thetree.nodes;

import com.intellij.ide.projectView.impl.nodes.PsiFileNode;

/**
 * A file node that carries a configured ordering weight, so a file can be lifted above or pushed
 * below folders per its {@code /weight} in {@code thetree.yaml}. Lower weight renders higher.
 */
public final class OrderedFileNode extends PsiFileNode {

    private final int weight;

    /**
     * @param origin the platform node being re-wrapped.
     * @param weight the {@code /weight} resolved for this file; lower renders higher.
     */
    public OrderedFileNode(PsiFileNode origin, int weight) {
        super(origin.getProject(), origin.getValue(), origin.getSettings());
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
