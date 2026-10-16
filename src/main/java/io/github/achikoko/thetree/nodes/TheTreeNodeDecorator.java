package io.github.achikoko.thetree.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import io.github.achikoko.thetree.PathUtil;
import io.github.achikoko.thetree.config.NameFragment;
import io.github.achikoko.thetree.config.NodeConfig;
import io.github.achikoko.thetree.config.TheTreeConfigService;
import io.github.achikoko.thetree.settings.TheTreeConfigState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Relabels nodes on the node the platform actually renders: compaction roots get their
 * project-relative path, then any {@code /rename} from {@code thetree.yaml} replaces the label.
 * Running here also covers module-content-root directories — whose own node is created by the
 * module machinery, not by our TreeStructureProvider, and therefore never sees
 * CompactedFolderNode#update.
 */
public final class TheTreeNodeDecorator implements ProjectViewNodeDecorator {

    /** Applies the compacted label first, so a {@code /rename} can still override it. */
    @Override
    public void decorate(@NotNull ProjectViewNode<?> node, @NotNull PresentationData data) {
        Project project = node.getProject();
        if (project == null) {
            return;
        }
        decorateCompacted(node, data, project);
        decorateRenamed(node, data, project);
    }

    /**
     * Replaces the label with the {@code /rename} fragments configured for the node's file, if
     * any. Speed search keeps working on the concatenated text.
     */
    private static void decorateRenamed(ProjectViewNode<?> node, PresentationData data, Project project) {
        if (!TheTreeConfigState.getInstance(project).getUseConfigFile()) {
            return;
        }
        VirtualFile vf = node.getVirtualFile();
        if (vf == null) {
            return;
        }
        List<NameFragment> fragments = renameFor(project, vf);
        if (fragments == null) {
            return;
        }
        data.clearText();
        StringBuilder plain = new StringBuilder();
        for (NameFragment fragment : fragments) {
            data.addText(fragment.text(), fragment.style().attributes());
            plain.append(fragment.text());
        }
        data.setPresentableText(plain.toString());
    }

    /**
     * Finds the {@code /rename} for {@code file}, asking the {@code thetree.yaml} of its own
     * content root first and then those of the enclosing content roots, so an outer (e.g.
     * workspace-level) config can rename a nested module's folder. Excluded folders are included.
     */
    @Nullable
    private static List<NameFragment> renameFor(Project project, VirtualFile file) {
        ProjectFileIndex index = ProjectFileIndex.getInstance(project);
        TheTreeConfigService service = TheTreeConfigService.getInstance(project);
        VirtualFile root = index.getContentRootForFile(file, false);
        while (root != null) {
            String relative = PathUtil.relativeIfUnder(file.getPath(), root.getPath());
            if (relative != null && !relative.isEmpty()) {
                NodeConfig cfg = service.getConfig(root).resolve(relative);
                if (cfg != null && cfg.renameOrNull() != null) {
                    return cfg.renameOrNull();
                }
            }
            VirtualFile parent = root.getParent();
            root = parent == null ? null : index.getContentRootForFile(parent, false);
        }
        return null;
    }

    /**
     * Rewrites the label of a source root or module content root that sits directly under the
     * project root to its project-relative path ({@code src/main/java}). Content roots keep their
     * last segment bold, matching how the IDE renders module names. Anything else is left alone.
     */
    private static void decorateCompacted(ProjectViewNode<?> node, PresentationData data, Project project) {
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
