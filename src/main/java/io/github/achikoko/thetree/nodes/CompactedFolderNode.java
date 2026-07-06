package io.github.achikoko.thetree.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.achikoko.thetree.PathUtil;
import org.jetbrains.annotations.NotNull;

/**
 * A directory shown by its path relative to the module content root (e.g. "src/main/java"),
 * i.e. the "compacted" representation. Carries a configurable ordering weight.
 */
public final class CompactedFolderNode extends PsiDirectoryNode {

    private final PsiDirectoryNode root;
    private Integer weight;

    public CompactedFolderNode(PsiDirectoryNode root, PsiDirectoryNode origin) {
        super(origin.getProject(), origin.getValue(), origin.getSettings(), origin.getFilter());
        this.root = root;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

//    /** The compacted display path, e.g. "src/main/protobuf". */
//    public String relativePath() {
//        return PathUtil.relativeIfUnder(getVirtualFile().getPath(), root.getVirtualFile().getPath());
//    }
    
    /** The compacted display path relative to the root module (project root), e.g. "src/main/java". */
    public String relativePath() {
        VirtualFile file = getVirtualFile();
        if (file == null) {
            return null;
        }
        // This makes the compaction relative to project file. (An issue if it is multimodule)
//        VirtualFile base = ProjectUtil.guessProjectDir(getProject());
        // This makes the compaction relative to the .thetree.yaml file.
        VirtualFile base = ProjectFileIndex.getInstance(getProject()).getContentRootForFile(file);
        if (base == null && root != null) {
            base = root.getVirtualFile(); // fallback to the invoking content root
        }
        return base == null ? null : PathUtil.relativeIfUnder(file.getPath(), base.getPath());
    }

    @Override
    protected void updateImpl(@NotNull PresentationData data) {
        super.updateImpl(data);
        String relative = relativePath();
        if (relative != null && !relative.isEmpty()) {
            data.setPresentableText(relative); // keeps speed-search / accessibility correct
        }
    }
    
    @Override
    public int getWeight() {
        return weight == null ? super.getWeight() : weight;
    }
    
    @Override
    public int getTypeSortWeight(boolean sortByType) {
        return weight == null ? super.getWeight() : weight;
    }
    
}
