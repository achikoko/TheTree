package dev.thetree.config;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One node of the tree described by {@code .thetree.yaml}. A node carries optional properties
 * ({@code :compacted}, {@code :weight}, {@code :hidden}) and a set of named children.
 *
 * <p>Property fields are nullable so we can distinguish "unset" (inherit default) from an
 * explicit value. Later-defined values overwrite earlier ones (see {@link #setCompacted} etc.),
 * which gives the "latter overrides former on conflict" merge semantics required by the spec.
 */
public final class NodeConfig {

    private final NodeMatcher matcher;

    private Boolean compacted;
    private Integer weight;
    private Boolean hidden;

    /** Fast path for literal keys (the only kind today). Insertion-ordered for stable output. */
    private final Map<String, NodeConfig> literalChildren = new LinkedHashMap<>();
    /** Non-literal (pattern) children, consulted after literals. Empty until patterns are enabled. */
    private final List<NodeConfig> patternChildren = new ArrayList<>();

    public NodeConfig(NodeMatcher matcher) {
        this.matcher = matcher;
    }

    public NodeMatcher matcher() {
        return matcher;
    }

    // ---- properties -------------------------------------------------------

    public void setCompacted(@Nullable Boolean value) {
        if (value != null) {
            this.compacted = value;
        }
    }

    public void setWeight(@Nullable Integer value) {
        if (value != null) {
            this.weight = value;
        }
    }

    public void setHidden(@Nullable Boolean value) {
        if (value != null) {
            this.hidden = value;
        }
    }

    public boolean isCompacted() {
        return Boolean.TRUE.equals(compacted);
    }

    public boolean isHidden() {
        return Boolean.TRUE.equals(hidden);
    }

    /** Weight for ordering; unset means the default of 0. */
    public int weightOrDefault() {
        return weight == null ? 0 : weight;
    }

    // ---- children ---------------------------------------------------------

    /**
     * Returns the existing child for {@code key}, creating it if necessary. Used while parsing;
     * this is what makes slash-expanded keys and repeated keys merge into the same node.
     */
    public NodeConfig childForParsing(String key) {
        NodeConfig existing = literalChildren.get(key);
        if (existing != null) {
            return existing;
        }
        NodeMatcher m = NodeMatcher.of(key);
        NodeConfig created = new NodeConfig(m);
        if (m.isLiteral()) {
            literalChildren.put(key, created);
        } else {
            patternChildren.add(created);
        }
        return created;
    }

    /**
     * Resolves a child for an actual filesystem segment (used at render time). Literal children
     * are checked first; pattern children are consulted afterwards (none today).
     *
     * @return the matching child config, or {@code null} if this branch is not described.
     */
    @Nullable
    public NodeConfig child(String segment) {
        NodeConfig literal = literalChildren.get(segment);
        if (literal != null) {
            return literal;
        }
        for (NodeConfig candidate : patternChildren) {
            if (candidate.matcher().matches(segment)) {
                return candidate;
            }
        }
        return null;
    }

    public Map<String, NodeConfig> literalChildren() {
        return literalChildren;
    }
}
