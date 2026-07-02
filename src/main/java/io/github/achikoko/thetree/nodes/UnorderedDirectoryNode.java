package io.github.achikoko.thetree.nodes;

import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;

public final class UnorderedDirectoryNode extends PsiDirectoryNode {
    
    public UnorderedDirectoryNode(PsiDirectoryNode origin) {
        super(origin.getProject(), origin.getValue(), origin.getSettings(), origin.getFilter());
    }
    
    @Override
    public int getTypeSortWeight(boolean sortByType) {
        return super.getWeight();
    }
    
}
