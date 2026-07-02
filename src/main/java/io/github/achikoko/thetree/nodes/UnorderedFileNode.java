package io.github.achikoko.thetree.nodes;

import com.intellij.ide.projectView.impl.nodes.PsiFileNode;

public final class UnorderedFileNode extends PsiFileNode {
    
    public UnorderedFileNode(PsiFileNode origin) {
        super(origin.getProject(), origin.getValue(), origin.getSettings());
    }
    
    @Override
    public int getTypeSortWeight(boolean sortByType) {
        return super.getWeight();
    }
    
}
