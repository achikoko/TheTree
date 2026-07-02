package dev.thetree.config;

import dev.thetree.PathUtil;
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

    /**
     * Resolves the config node for {@code relativePath} (relative to the module root),
     * or {@code null} if that branch is not described by the config.
     */
    @Nullable
    public NodeConfig resolve(String relativePath) {
        return resolve(root, PathUtil.segments(relativePath));
    }

    @Nullable
    public static NodeConfig resolve(@Nullable NodeConfig from, List<String> segments) {
        NodeConfig current = from;
        for (String segment : segments) {
            if (current == null) {
                return null;
            }
            current = current.child(segment);
        }
        return current;
    }

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
}
