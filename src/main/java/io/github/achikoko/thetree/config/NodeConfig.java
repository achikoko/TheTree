package io.github.achikoko.thetree.config;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One node of the tree described by {@code .thetree.yaml}. A node carries optional properties
 * ({@code /compacted}, {@code /weight}, {@code /hidden}) and a set of named children.
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

    /** Fast path for literal keys. Insertion-ordered so output is stable across parses. */
    private final Map<String, NodeConfig> literalChildren = new LinkedHashMap<>();
    /** Glob and {@code **} children, consulted only after an exact literal fails to match. */
    private final List<NodeConfig> patternChildren = new ArrayList<>();

    /** @param matcher decides which filesystem segments this node describes. */
    public NodeConfig(NodeMatcher matcher) {
        this.matcher = matcher;
    }

    /** @return the matcher this node was created with. */
    public NodeMatcher matcher() {
        return matcher;
    }

    // ---- properties -------------------------------------------------------

    /** Sets {@code /compacted}; a {@code null} value leaves any previous setting untouched. */
    public void setCompacted(@Nullable Boolean value) {
        if (value != null) {
            this.compacted = value;
        }
    }

    /** Sets {@code /weight}; a {@code null} value leaves any previous setting untouched. */
    public void setWeight(@Nullable Integer value) {
        if (value != null) {
            this.weight = value;
        }
    }

    /** Sets {@code /hidden}; a {@code null} value leaves any previous setting untouched. */
    public void setHidden(@Nullable Boolean value) {
        if (value != null) {
            this.hidden = value;
        }
    }

    /** @return true if {@code /compacted: true} was declared on this node. */
    public boolean isCompacted() {
        return Boolean.TRUE.equals(compacted);
    }

    /** @return true if {@code /hidden: true} was declared on this node. */
    public boolean isHidden() {
        return Boolean.TRUE.equals(hidden);
    }
    
    /**
     * Whether anywhere below this node a descendant is explicitly un-hidden ({@code /hidden: false}).
     * A hidden folder that carries such a descendant must still be traversed, otherwise the
     * re-shown child would be lost with its parent.
     */
    public boolean hasExplicitlyShownChildren(){
        for (NodeConfig child : literalChildren.values()) {
            if(Boolean.FALSE.equals(child.hidden))
                return true;
            if(child.hasExplicitlyShownChildren())
                return true;
        }
        return false;
    }
    
    /** The explicit weight, or null if the YAML did not set one. */
    @Nullable
    public Integer weightOrNull() {
        return weight;
    }
    
    // ---- children ---------------------------------------------------------
    
    /** Exact literal child only (no pattern fallback). */
    @Nullable
    public NodeConfig literalChild(String segment) {
        return literalChildren.get(segment);
    }
    
    /**
     * Pattern children in precedence order: more specific globs first (more literal characters wins,
     * ties broken alphabetically for determinism), {@code **} always last.
     */
    public List<NodeConfig> orderedPatternChildren() {
        List<NodeConfig> list = new ArrayList<>(patternChildren);
        list.sort((a, b) -> {
            boolean da = a.matcher().isDeep(), db = b.matcher().isDeep();
            if (da != db) return da ? 1 : -1;                 // ** always last
            int sa = specificity(a.matcher().pattern());
            int sb = specificity(b.matcher().pattern());
            if (sa != sb) return sb - sa;                     // more literal chars = more specific
            return a.matcher().pattern().compareTo(b.matcher().pattern());
        });
        return list;
    }
    
    /** How many characters of a pattern are literal — the ranking key for glob precedence. */
    private static int specificity(String p) {
        int n = 0;
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c != '*' && c != '?') n++;
        }
        return n;
    }
    
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
     * Resolves a child for an actual filesystem segment, checking literals first and then patterns
     * in declaration order. This is the shallow, single-segment lookup;
     * {@code TheTreeConfig#resolve} is the full walk that also handles {@code **}.
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

    /** @return the live, insertion-ordered map of literal children (used to walk the config). */
    public Map<String, NodeConfig> literalChildren() {
        return literalChildren;
    }
}
