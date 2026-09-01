package io.github.achikoko.thetree;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.achikoko.thetree.config.NodeConfig;
import io.github.achikoko.thetree.config.TheTreeConfig;
import io.github.achikoko.thetree.config.TheTreeConfigService;
import io.github.achikoko.thetree.nodes.*;
import io.github.achikoko.thetree.settings.TheTreeConfigState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The single {@link TreeStructureProvider} that implements all The Tree's behavior:
 *
 * <ol>
 *   <li><b>Compaction</b> of source roots and folders declared {@code /compacted: true} in
 *       {@code .thetree.yaml} into single "src/main/java"-style nodes, hoisted to the module's
 *       top level; branches that existed only to hold such a root collapse away.</li>
 *   <li><b>Ordering</b> by per-node {@code /weight} (0 by default; lower renders higher).</li>
 *   <li><b>Hiding</b> of nodes marked {@code /hidden: true}.</li>
 * </ol>
 *
 * <p>The provider is invoked by the platform for every parent, so weight/hidden are applied at
 * any depth: at the module root here directly, and deeper via {@link #decorateChildren}.
 */
public final class TheTreeStructureProvider implements TreeStructureProvider {

    /**
     * Platform entry point, called for every expanded directory. Module content roots go through
     * the compaction pass; everything deeper only gets pruning plus weight/hidden decoration.
     */
    @NotNull
    @Override
    public Collection<AbstractTreeNode<?>> modify(@NotNull AbstractTreeNode<?> parent,
                                                  @NotNull Collection<AbstractTreeNode<?>> children,
                                                  ViewSettings settings) {
        if (!(parent instanceof PsiDirectoryNode dirParent)) {
            return children;
        }
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
    
    /**
     * Rebuilds a module content root's children: hoist every compaction root in the subtree to the
     * top level, keep the remaining natural children, then apply weight/hidden and optionally
     * append the "Libraries" node.
     */
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
                findCompactionRoots(parent, child, declaredCompacted, project, topLevel, config);
            }
            // 2. Keep the original children in their natural place, except those that are
            //    themselves compaction roots or whose subtree exists only to hold roots.
            for (AbstractTreeNode<?> child : children) {
                if (keepInNaturalTree(child, project, declaredCompacted, config, contentRoot)) {
                    topLevel.add(child);
                }
            }
        } else {
            topLevel.addAll(children);
        }
        
        List<AbstractTreeNode<?>> result = decorate
            ? decorateChildren(new ArrayList<>(topLevel), config, contentRoot.getPath())
            : new ArrayList<>(topLevel);
        
        if (state.getShowModuleLibraries()) {
            result.add(new ModuleLibrariesNode(parent, settings));
        }
        return result;
    }

    // ---- deeper levels ----------------------------------------------------
    
    /**
     * Handles any directory below a content root: prunes nodes already hoisted to the top level,
     * then applies weight/hidden if the config describes this branch.
     */
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
            for(AbstractTreeNode<?> child: children){
                if(keepInNaturalTree(child, project, declaredCompacted, cfgForRoots, contentRoot)){
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
        return decorateChildren(pruned, config, parentFile.getPath());
    }

    // ---- decoration (weight + hidden) ------------------------------------

    /**
     * Drops hidden nodes and re-wraps the rest so ordering works: configured nodes become
     * {@code Ordered*} wrappers, unconfigured ones become {@code Unordered*} wrappers that sit on
     * the same sort scale. Nodes we cannot map to a path are passed through untouched.
     *
     * @param contextPath absolute path the child segments are resolved against.
     */
    private List<AbstractTreeNode<?>> decorateChildren(List<AbstractTreeNode<?>> nodes,
                                                       TheTreeConfig parentConfig,
                                                       String contextPath) {
        List<AbstractTreeNode<?>> out = new ArrayList<>(nodes.size());
        for (AbstractTreeNode<?> node : nodes) {
            List<String> segments = segmentsFor(node, contextPath);
            if (segments == null) {
                out.add(node); // virtual/unknown node: leave as-is
                continue;
            }
            NodeConfig cfg = parentConfig.resolve(segments);
            if (cfg != null && cfg.isHidden()) {
                continue; // drop hidden node
            }
            Integer weight = cfg != null ? cfg.weightOrNull() : null;
            
            if(weight != null){
                out.add(applyWeight(node, weight));
            }else if (node instanceof CompactedFolderNode) {
                out.add(node);
            }else if(node instanceof PsiFileNode){
                out.add(new UnorderedFileNode((PsiFileNode)node));
            }else if(node instanceof PsiDirectoryNode){
                out.add(new UnorderedDirectoryNode((PsiDirectoryNode)node));
            }else{
                out.add(node);
            }
            
        }
        return out;
    }

    /**
     * The config path segments for a node, relative to {@code contextPath}. Compacted nodes carry a
     * multi-segment path; ordinary nodes are a single name.
     *
     * @return the segments, or {@code null} for virtual nodes that have no file behind them.
     */
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

    /** Attaches a configured weight to a node, wrapping it when the node class cannot hold one. */
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

    /** Resolves every {@code /compacted: true} entry into an absolute path for fast lookup. */
    private Set<String> declaredCompactedAbsolutePaths(TheTreeConfig config, VirtualFile contentRoot) {
        Set<String> out = new HashSet<>();
        String base = contentRoot.getPath();
        for (List<String> segments : config.declaredCompactedPaths()) {
            out.add(base + "/" + String.join("/", segments));
        }
        return out;
    }

    /** A compaction root is either a module source root or a folder declared {@code /compacted}. */
    private boolean isCompactionRoot(VirtualFile file, Project project, Set<String> declaredCompacted) {
        return ProjectRootsUtil.isModuleSourceRoot(file, project) || declaredCompacted.contains(file.getPath());
    }

    /**
     * Collects compaction roots (source roots + declared-compacted folders) under {@code node} into
     * {@code out}, wrapped as {@link CompactedFolderNode}. The walk does not descend past a root it
     * found, past a nested content root, or into a hidden folder — unless that hidden folder has an
     * explicitly re-shown descendant.
     */
    private void findCompactionRoots(PsiDirectoryNode root,
                                     AbstractTreeNode<?> node,
                                     Set<String> declaredCompacted,
                                     Project project,
                                     List<AbstractTreeNode<?>> out,
                                     TheTreeConfig config) {
        if (!(node instanceof PsiDirectoryNode)) {
            return;
        }
        PsiDirectoryNode dirNode = (PsiDirectoryNode) node;
        VirtualFile vf = dirNode.getVirtualFile();
        if (vf == null) {
            return;
        }
        @Nullable NodeConfig nodeCfg = config.resolve(PathUtil.segments(vf.getPath()));
        if(nodeCfg != null && nodeCfg.isHidden() && !nodeCfg.hasExplicitlyShownChildren()){
            return; // Found a hidden folder, don't care if it contains a source root
        }
        if (isCompactionRoot(vf, project, declaredCompacted)) {
            out.add(new CompactedFolderNode(root, dirNode));
            return; // do not descend past a compaction root
        }
        if (ProjectRootsUtil.isModuleContentRoot(vf, project) && !vf.equals(root.getVirtualFile())) {
            // A nested content root (submodule): handled separately by the platform. Stop here.
            return;
        }
        for (AbstractTreeNode<?> child : node.getChildren()) {
            findCompactionRoots(root, child, declaredCompacted, project, out, config);
        }
    }
    /**
     * Whether a child stays in the natural (nested) tree. Compaction roots are shown once at the
     * top level, hidden nodes are dropped, and a directory whose subtree holds nothing but
     * compaction roots collapses away — unless it also contains a nested module.
     */
    private boolean keepInNaturalTree(AbstractTreeNode<?> node, Project project, Set<String> declaredCompacted, TheTreeConfig config, VirtualFile contentRoot) {
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
        NodeConfig childConfig = config.resolve(PathUtil.relativeIfUnder(vf.getPath(), contentRoot.getPath()));
        if(childConfig != null && childConfig.isHidden()){
            return false; // hidden
        }
        if(ProjectRootsUtil.isModuleContentRoot(vf, project)){
            return true;
        }
        Scan s = scan(vf, project, declaredCompacted, config, contentRoot);
        return s.hasModule || !(s.hasRoot && !s.hasLeftover);
    }
    
    /** What a subtree scan found: compaction roots, nested modules, and anything else worth showing. */
    private static final class Scan {
        boolean hasRoot;
        boolean hasModule;
        boolean hasLeftover;
    }
    
    /** Inspects a directory's subtree for compaction roots and "leftover" content (any file, an
     *  empty folder, or a folder that itself holds leftovers). */
    private Scan scan(VirtualFile dir, Project project, Set<String> declaredCompacted, TheTreeConfig config, VirtualFile contentRoot) {
        Scan s = new Scan();
        VirtualFile[] kids = dir.getChildren();
        if (kids.length == 0) {
            s.hasLeftover = true; // a real, empty folder is worth showing
            return s;
        }
        for (VirtualFile child : kids) {
            NodeConfig childConfig = config.resolve(PathUtil.relativeIfUnder(child.getPath(), contentRoot.getPath()));
            if(childConfig != null && childConfig.isHidden()){
//                continue;
            }else if (!child.isDirectory()) {
                s.hasLeftover = true;
            } else if (isCompactionRoot(child, project, declaredCompacted)) {
                s.hasRoot = true;
            } else if(ProjectRootsUtil.isModuleContentRoot(child, project)){
                s.hasModule = true;
            } else {
                Scan cs = scan(child, project, declaredCompacted, config, contentRoot);
                s.hasRoot |= cs.hasRoot;
                s.hasLeftover |= cs.hasLeftover;
                s.hasModule |= cs.hasModule;
            }
        }
        return s;
    }
    
}
