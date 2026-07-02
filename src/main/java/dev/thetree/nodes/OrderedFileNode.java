package dev.thetree.nodes;

import com.intellij.ide.projectView.impl.nodes.PsiFileNode;

/**
 * A file node that carries a configured ordering weight, so a file can be lifted above or pushed
 * below folders per its {@code :weight} in {@code .thetree.yaml}.
 */
public final class OrderedFileNode extends PsiFileNode {

    private final int weight;

    public OrderedFileNode(PsiFileNode origin, int weight) {
        super(origin.getProject(), origin.getValue(), origin.getSettings());
        this.weight = weight;
    }

    @Override
    public int getWeight() {
        return Ordering.weight(weight, false);
    }

    @Override
    public int getTypeSortWeight(boolean sortByType) {
        return Ordering.typeSortWeight(weight, false);
    }
}
