package dev.thetree.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import dev.thetree.PathUtil;
import org.jetbrains.annotations.NotNull;

/**
 * A directory shown by its path relative to the module content root (e.g. "src/main/java"),
 * i.e. the "compacted" representation. Carries a configurable ordering weight.
 */
public final class CompactedFolderNode extends PsiDirectoryNode {

    private final PsiDirectoryNode root;
    private int weight;

    public CompactedFolderNode(PsiDirectoryNode root, PsiDirectoryNode origin) {
        super(origin.getProject(), origin.getValue(), origin.getSettings(), origin.getFilter());
        this.root = root;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    /** The compacted display path, e.g. "src/main/protobuf". */
    public String relativePath() {
        return PathUtil.relativeIfUnder(getVirtualFile().getPath(), root.getVirtualFile().getPath());
    }

    @Override
    protected void updateImpl(@NotNull PresentationData data) {
        super.updateImpl(data);
        String relative = relativePath();
        if (relative != null && !relative.isEmpty()) {
            data.setPresentableText(relative);
        }
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
