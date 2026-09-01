package io.github.achikoko.thetree.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import io.github.achikoko.thetree.PathUtil;
import io.github.achikoko.thetree.settings.TheTreeConfigState;
import org.jetbrains.annotations.NotNull;

/**
 * Relabels compaction-root directory nodes to their project-relative path. This runs on the node
 * the platform actually renders, so it also fixes module-content-root directories — whose own node
 * is created by the module machinery, not by our TreeStructureProvider, and therefore never sees
 * CompactedFolderNode#update.
 */
public final class CompactedFolderDecorator implements ProjectViewNodeDecorator {

    /**
     * Rewrites the label of a source root or module content root that sits directly under the
     * project root to its project-relative path ({@code src/main/java}). Content roots keep their
     * last segment bold, matching how the IDE renders module names. Anything else is left alone.
     */
    @Override
    public void decorate(@NotNull ProjectViewNode<?> node, @NotNull PresentationData data) {
        Project project = node.getProject();
        if (project == null) {
            return;
        }
        if (!TheTreeConfigState.getInstance(project).getCompactSourceFolder()) {
            return; // respect the toggle
        }
        VirtualFile vf = node.getVirtualFile();
        if (vf == null || !vf.isDirectory()) {
            return;
        }
        boolean isModuleContentRoot = ProjectRootsUtil.isModuleContentRoot(vf, project);
        if(!ProjectRootsUtil.isModuleSourceRoot(vf, project) && !isModuleContentRoot){
            return;
        }
        VirtualFile base = ProjectUtil.guessProjectDir(project);
        if (base == null) {
            return;
        }
        AbstractTreeNode<?> parent = node.getParent();
        // If the parent of the node is the base, then it is a compacted folder for sure. Otherwise, skip.
        if(parent instanceof ProjectViewNode<?> && !base.equals(((ProjectViewNode<?>)parent).getVirtualFile())){
            return;
        }
        String rel = PathUtil.relativeIfUnder(vf.getPath(), base.getPath());
        if (rel == null || rel.indexOf('/') < 0) {
            return; // single segment: nothing to compact
        }
        data.clearText();
        if(isModuleContentRoot){
            int lastIndexOf = rel.lastIndexOf('/');
            if(lastIndexOf >= 0){
                data.addText(rel.substring(0, lastIndexOf+1), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                data.addText(rel.substring(lastIndexOf+1), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            }else{
                data.addText(rel, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            }
        }else{
            data.addText(rel, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
        data.setPresentableText(rel);
    }
}
