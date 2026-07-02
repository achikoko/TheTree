package dev.thetree;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import dev.thetree.config.NodeConfig;
import dev.thetree.config.TheTreeConfig;
import dev.thetree.config.TheTreeConfigService;
import dev.thetree.nodes.CompactedFolderNode;
import dev.thetree.nodes.ModuleLibrariesNode;
import dev.thetree.nodes.OrderedDirectoryNode;
import dev.thetree.nodes.OrderedFileNode;
import dev.thetree.nodes.UncompactedFolderNode;
import dev.thetree.settings.TheTreeConfigState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The single {@link TreeStructureProvider} that implements all of The Tree's behavior:
 *
 * <ol>
 *   <li><b>Compaction</b> of source roots and folders declared {@code :compacted: true} in
 *       {@code .thetree.yaml} into single "src/main/java"-style nodes.</li>
 *   <li><b>Uncompacted grouping</b>: non-source, non-declared sibling folders (e.g.
 *       {@code src/main/protobuf}) are collected under a virtual "Uncompacted" node instead of
 *       being surfaced at the module top level.</li>
 *   <li><b>Ordering</b> by per-node {@code :weight} (0 by default; lower renders higher).</li>
 *   <li><b>Hiding</b> of nodes marked {@code :hidden: true}.</li>
 * </ol>
 *
 * <p>The provider is invoked by the platform for every parent, so weight/hidden are applied at
 * any depth: at the module root here directly, and deeper via {@link #decorateChildren}.
 */
public final class TheTreeStructureProvider implements TreeStructureProvider {

    @NotNull
    @Override
    public Collection<AbstractTreeNode<?>> modify(@NotNull AbstractTreeNode<?> parent,
                                                  @NotNull Collection<AbstractTreeNode<?>> children,
                                                  ViewSettings settings) {
        if (!(parent instanceof PsiDirectoryNode)) {
            return children;
        }
        PsiDirectoryNode dirParent = (PsiDirectoryNode) parent;
        Project project = parent.getProject();
        if (project == null) {
            return children;
        }
        VirtualFile parentFile = dirParent.getVirtualFile();
        if (parentFile == null) {
            return children;
        }

        if (ProjectRootsUtil.isModuleContentRoot(parentFile, project)) {
            return modifyContentRoot(dirParent, parentFile, children, settings, project);
        }
        return decorateDeeper(dirParent, parentFile, children, project);
    }

    // ---- module content root ---------------------------------------------
    
    private Collection<AbstractTreeNode<?>> modifyContentRoot(PsiDirectoryNode parent,
                                                              VirtualFile contentRoot,
                                                              Collection<AbstractTreeNode<?>> children,
                                                              ViewSettings settings,
                                                              Project project) {
        TheTreeConfigState state = TheTreeConfigState.getInstance(project);
        TheTreeConfigService configService = TheTreeConfigService.getInstance(project);
        
        boolean useConfig = state.getUseConfigFile();
        TheTreeConfig config = useConfig ? configService.getConfig(contentRoot) : TheTreeConfig.EMPTY;
        boolean decorate = useConfig && config.isPresent();
        
        List<AbstractTreeNode<?>> topLevel = new ArrayList<>();
        
        if (state.getCompactSourceFolder()) {
            Set<String> declaredCompacted = declaredCompactedAbsolutePaths(config, contentRoot);
            // 1. Hoist every compaction root in the subtree to the top level.
            for (AbstractTreeNode<?> child : children) {
                findCompactionRoots(parent, child, declaredCompacted, project, topLevel);
            }
            // 2. Keep the original children in their natural place, except those that are
            //    themselves compaction roots or whose subtree exists only to hold roots.
            for (AbstractTreeNode<?> child : children) {
                if (keepInNaturalTree(child, project, declaredCompacted)) {
                    topLevel.add(child);
                }
            }
        } else {
            topLevel.addAll(children);
        }
        
        List<AbstractTreeNode<?>> result = decorate
            ? decorateChildren(new ArrayList<>(topLevel), config.root(), contentRoot.getPath())
            : new ArrayList<>(topLevel);
        
        if (state.getShowModuleLibraries()) {
            result.add(new ModuleLibrariesNode(parent, settings));
        }
        return result;
    }

    // ---- deeper levels ----------------------------------------------------
    
    private Collection<AbstractTreeNode<?>> decorateDeeper(PsiDirectoryNode parent,
                                                           VirtualFile parentFile,
                                                           Collection<AbstractTreeNode<?>> children,
                                                           Project project) {
        TheTreeConfigState state = TheTreeConfigState.getInstance(project);
        VirtualFile contentRoot = ProjectRootManager.getInstance(project).getFileIndex()
            .getContentRootForFile(parentFile);
        
        // 1. Prune compaction roots (and branches that exist only to hold them) out of the
        //    natural tree, so each root appears once — at the top level.
        List<AbstractTreeNode<?>> pruned;
        if (state.getCompactSourceFolder() && contentRoot != null) {
            TheTreeConfig cfgForRoots = state.getUseConfigFile()
                ? TheTreeConfigService.getInstance(project).getConfig(contentRoot)
                : TheTreeConfig.EMPTY;
            Set<String> declaredCompacted = declaredCompactedAbsolutePaths(cfgForRoots, contentRoot);
            pruned = new ArrayList<>(children.size());
            for (AbstractTreeNode<?> child : children) {
                if (keepInNaturalTree(child, project, declaredCompacted)) {
                    pruned.add(child);
                }
            }
        } else {
            pruned = new ArrayList<>(children);
        }
        
        // 2. Apply weight/hidden where the config describes this branch.
        if (!state.getUseConfigFile() || contentRoot == null) {
            return pruned;
        }
        TheTreeConfig config = TheTreeConfigService.getInstance(project).getConfig(contentRoot);
        if (!config.isPresent()) {
            return pruned;
        }
        String relative = PathUtil.relativeIfUnder(parentFile.getPath(), contentRoot.getPath());
        if (relative == null) {
            return pruned;
        }
        NodeConfig parentConfig = config.resolve(relative);
        if (parentConfig == null) {
            return pruned;
        }
        return decorateChildren(pruned, parentConfig, parentFile.getPath());
    }

    // ---- decoration (weight + hidden) ------------------------------------

    private List<AbstractTreeNode<?>> decorateChildren(List<AbstractTreeNode<?>> nodes,
                                                       NodeConfig parentConfig,
                                                       String contextPath) {
        List<AbstractTreeNode<?>> out = new ArrayList<>(nodes.size());
        for (AbstractTreeNode<?> node : nodes) {
            List<String> segments = segmentsFor(node, contextPath);
            if (segments == null) {
                out.add(node); // virtual/unknown node: leave as-is
                continue;
            }
            NodeConfig cfg = TheTreeConfig.resolve(parentConfig, segments);
            if (cfg != null && cfg.isHidden()) {
                continue; // drop hidden node
            }
            int weight = cfg != null ? cfg.weightOrDefault() : 0;
            out.add(applyWeight(node, weight));
        }
        return out;
    }

    @Nullable
    private List<String> segmentsFor(AbstractTreeNode<?> node, String contextPath) {
        if (node instanceof CompactedFolderNode) {
            VirtualFile vf = ((CompactedFolderNode) node).getVirtualFile();
            String rel = vf == null ? null : PathUtil.relativeIfUnder(vf.getPath(), contextPath);
            if (rel == null || rel.isEmpty()) {
                return vf == null ? null : List.of(vf.getName());
            }
            return PathUtil.segments(rel);
        }
        if (node instanceof PsiDirectoryNode) {
            VirtualFile vf = ((PsiDirectoryNode) node).getVirtualFile();
            return vf == null ? null : List.of(vf.getName());
        }
        if (node instanceof PsiFileNode) {
            VirtualFile vf = ((PsiFileNode) node).getVirtualFile();
            return vf == null ? null : List.of(vf.getName());
        }
        return null;
    }

    private AbstractTreeNode<?> applyWeight(AbstractTreeNode<?> node, int weight) {
        if (node instanceof CompactedFolderNode) {
            ((CompactedFolderNode) node).setWeight(weight);
            return node;
        }
        if (node instanceof PsiDirectoryNode) {
            return new OrderedDirectoryNode((PsiDirectoryNode) node, weight);
        }
        if (node instanceof PsiFileNode) {
            return new OrderedFileNode((PsiFileNode) node, weight);
        }
        return node;
    }

    // ---- compaction traversal --------------------------------------------

    private Set<String> declaredCompactedAbsolutePaths(TheTreeConfig config, VirtualFile contentRoot) {
        Set<String> out = new HashSet<>();
        String base = contentRoot.getPath();
        for (List<String> segments : config.declaredCompactedPaths()) {
            out.add(base + "/" + String.join("/", segments));
        }
        return out;
    }

    private boolean isCompactionRoot(VirtualFile file, Project project, Set<String> declaredCompacted) {
        return ProjectRootsUtil.isModuleSourceRoot(file, project) || declaredCompacted.contains(file.getPath());
    }

    /** Collects compaction roots (source roots + declared-compacted folders) under {@code node}. */
    private void findCompactionRoots(PsiDirectoryNode root,
                                     AbstractTreeNode<?> node,
                                     Set<String> declaredCompacted,
                                     Project project,
                                     List<AbstractTreeNode<?>> out) {
        if (!(node instanceof PsiDirectoryNode)) {
            return;
        }
        PsiDirectoryNode dirNode = (PsiDirectoryNode) node;
        VirtualFile vf = dirNode.getVirtualFile();
        if (vf == null) {
            return;
        }
        if (ProjectRootsUtil.isModuleContentRoot(vf, project) && !vf.equals(root.getVirtualFile())) {
            // A nested content root (submodule): handled separately by the platform. Stop here.
            return;
        }
        if (isCompactionRoot(vf, project, declaredCompacted)) {
            out.add(new CompactedFolderNode(root, dirNode));
            return; // do not descend past a compaction root
        }
        for (AbstractTreeNode<?> child : node.getChildren()) {
            findCompactionRoots(root, child, declaredCompacted, project, out);
        }
    }
    /** Whether a child stays in the natural (nested) tree. Compaction roots are shown once at the
     *  top level; a directory whose subtree holds only compaction roots collapses away. */
    private boolean keepInNaturalTree(AbstractTreeNode<?> node, Project project, Set<String> declaredCompacted) {
        if (!(node instanceof PsiDirectoryNode)) {
            return true; // files stay in place
        }
        VirtualFile vf = ((PsiDirectoryNode) node).getVirtualFile();
        if (vf == null) {
            return true;
        }
        if (isCompactionRoot(vf, project, declaredCompacted)) {
            return false; // hoisted to the top level
        }
        Scan s = scan(vf, project, declaredCompacted);
        return !(s.hasRoot && !s.hasLeftover);
    }
    
    private static final class Scan {
        boolean hasRoot;
        boolean hasLeftover;
    }
    
    /** Inspects a directory's subtree for compaction roots and "leftover" content (any file, an
     *  empty folder, or a folder that itself holds leftovers). */
    private Scan scan(VirtualFile dir, Project project, Set<String> declaredCompacted) {
        Scan s = new Scan();
        VirtualFile[] kids = dir.getChildren();
        if (kids.length == 0) {
            s.hasLeftover = true; // a real, empty folder is worth showing
            return s;
        }
        for (VirtualFile child : kids) {
            if (!child.isDirectory()) {
                s.hasLeftover = true;
            } else if (isCompactionRoot(child, project, declaredCompacted)) {
                s.hasRoot = true;
            } else {
                Scan cs = scan(child, project, declaredCompacted);
                s.hasRoot |= cs.hasRoot;
                s.hasLeftover |= cs.hasLeftover;
            }
        }
        return s;
    }
    
}
