package io.github.achikoko.thetree.config;

import io.github.achikoko.thetree.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The parsed {@code .thetree.yaml} for a single module content root. Wraps the root
 * {@link NodeConfig} and provides convenience resolution over "/"-separated relative paths.
 */
public final class TheTreeConfig {

    public static final TheTreeConfig EMPTY = new TheTreeConfig(new NodeConfig(NodeMatcher.of("")), false);

    private final NodeConfig root;
    private final boolean present;

    public TheTreeConfig(NodeConfig root, boolean present) {
        this.root = root;
        this.present = present;
    }

    public NodeConfig root() {
        return root;
    }

    /** Whether an actual {@code .thetree.yaml} file backs this config. */
    public boolean isPresent() {
        return present;
    }

//    /**
//     * Resolves the config node for {@code relativePath} (relative to the module root),
//     * or {@code null} if that branch is not described by the config.
//     */
//    @Nullable
//    public NodeConfig resolve(String relativePath) {
//        return resolve(root, PathUtil.segments(relativePath));
//    }
//
//    @Nullable
//    public static NodeConfig resolve(@Nullable NodeConfig from, List<String> segments) {
//        NodeConfig current = from;
//        for (String segment : segments) {
//            if (current == null) {
//                return null;
//            }
//            current = current.child(segment);
//        }
//        return current;
//    }

    /**
     * Collects the relative paths (as segment lists) of every node explicitly marked
     * {@code :compacted: true}. Used to treat user-declared folders like source roots.
     */
    @NotNull
    public List<List<String>> declaredCompactedPaths() {
        List<List<String>> out = new ArrayList<>();
        collectCompacted(root, new ArrayList<>(), out);
        return out;
    }

    private static void collectCompacted(NodeConfig node, List<String> path, List<List<String>> out) {
        for (Map.Entry<String, NodeConfig> e : node.literalChildren().entrySet()) {
            path.add(e.getKey());
            if (e.getValue().isCompacted()) {
                out.add(new ArrayList<>(path));
            }
            collectCompacted(e.getValue(), path, out);
            path.remove(path.size() - 1);
        }
    }
    
    /** Resolve by an already-split path (relative to the module root). */
    @Nullable
    public NodeConfig resolve(List<String> segments) {
        return match(root, segments, 0);
    }
    
    @Nullable
    public NodeConfig resolve(String relativePath) {
        return match(root, PathUtil.segments(relativePath), 0);
    }
    
    @Nullable
    public static NodeConfig resolve(@Nullable NodeConfig from, List<String> segments) {
        return match(from, segments, 0);
    }
    
    @Nullable
    private static NodeConfig match(@Nullable NodeConfig node, List<String> segments, int index) {
        if (node == null) {
            return null;
        }
        if (index == segments.size()) {
            return node; // whole path consumed: this node describes it
        }
        String seg = segments.get(index);
        
        // 1. Exact literal wins over any pattern.
        NodeConfig lit = node.literalChild(seg);
        if (lit != null) {
            NodeConfig r = match(lit, segments, index + 1);
            if (r != null) return r;
        }
        
        // 2. Patterns, most specific first, ** last.
        for (NodeConfig cand : node.orderedPatternChildren()) {
            if (cand.matcher().isDeep()) {
                // ** consumes zero or more segments — try every split.
                for (int k = 0; index + k <= segments.size(); k++) {
                    NodeConfig r = match(cand, segments, index + k);
                    if (r != null) return r;
                }
            } else if (cand.matcher().matches(seg)) {
                NodeConfig r = match(cand, segments, index + 1);
                if (r != null) return r;
            }
        }
        return null;
    }
    
}
