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

    /**
     * @param root   the content-root node this compaction was hoisted to; used as a fallback base
     *               when the file index cannot name a content root for the folder.
     * @param origin the platform directory node being re-wrapped.
     */
    public CompactedFolderNode(PsiDirectoryNode root, PsiDirectoryNode origin) {
        super(origin.getProject(), origin.getValue(), origin.getSettings(), origin.getFilter());
        this.root = root;
    }

    /** Applies a {@code /weight} from {@code thetree.yaml}; unset means "keep the IDE's order". */
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
        // This makes the compaction relative to the thetree.yaml file.
        VirtualFile base = ProjectFileIndex.getInstance(getProject()).getContentRootForFile(file);
        if (base == null && root != null) {
            base = root.getVirtualFile(); // fallback to the invoking content root
        }
        return base == null ? null : PathUtil.relativeIfUnder(file.getPath(), base.getPath());
    }

    /** Relabels the node with its compacted relative path instead of the bare folder name. */
    @Override
    protected void updateImpl(@NotNull PresentationData data) {
        super.updateImpl(data);
        String relative = relativePath();
        if (relative != null && !relative.isEmpty()) {
            data.setPresentableText(relative); // keeps speed-search / accessibility correct
        }
    }
    
    /** @return the configured weight, or the platform default when none was declared. */
    @Override
    public int getWeight() {
        return weight == null ? super.getWeight() : weight;
    }
    
    /** @return the configured weight, so ordering holds with "Folders Always on Top" too. */
    @Override
    public int getTypeSortWeight(boolean sortByType) {
        return weight == null ? super.getWeight() : weight;
    }
    
}
