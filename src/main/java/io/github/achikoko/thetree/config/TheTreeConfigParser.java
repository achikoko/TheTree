package io.github.achikoko.thetree.config;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses {@code thetree.yaml} text into a {@link NodeConfig} tree.
 *
 * <p>Rules:
 * <ul>
 *     <li>A mapping key starting with {@code /} sets a property on the current node
 *         ({@code /compact}, {@code /order}, {@code /hide}, {@code /rename}). The older
 *         {@code /compacted}, {@code /weight} and {@code /hidden} are still accepted as aliases.
 *         Unknown {@code /}-keys are ignored (a warning is logged) for forward compatibility.</li>
 *     <li>Any other key names a child folder/file, and may be a glob ({@code *.iml}) or the deep
 *         wildcard ({@code **}). Its value (a nested mapping) describes that child.</li>
 *     <li>A key that contains {@code /} is syntactic sugar for the nested expansion, e.g.
 *         {@code src/main/protobuf: {...}} is equivalent to nested {@code src: {main: {protobuf: {...}}}}.
 *         The value/properties are applied to the deepest segment.</li>
 *     <li>When the same node is described more than once, later values overwrite earlier ones
 *         (children are merged recursively). This includes repeating the very same key, so a
 *         block can be commented out without touching other blocks describing that node.</li>
 *     <li>{@code .} segments are ignored, so {@code ./src/main} is the same as {@code src/main}.</li>
 * </ul>
 *
 * <p>Example:
 * <pre>
 * src/main/protobuf:
 *   /compact: true
 *   /order: -1
 *   /rename: [{attrs: RB, text: proto}, {attrs: G, text: " (generated)"}]
 * docs:
 *   /hide: true
 * generated:
 *   /order: 5
 *   keep.txt:
 *     /order: -1
 * '*.iml':
 *   /hide: true
 * </pre>
 */
public final class TheTreeConfigParser {
    
    private static final Logger LOG = Logger.getInstance(TheTreeConfigParser.class);

    private TheTreeConfigParser() {
    }

    /**
     * Parses the given YAML text. Never throws: {@code null}, blank or malformed input yields a
     * config reporting {@link TheTreeConfig#isPresent()} {@code == false}, so a broken file
     * degrades to "no config" instead of breaking the project view.
     */
    public static TheTreeConfig parse(@Nullable String yamlText) {
        NodeConfig root = new NodeConfig(NodeMatcher.of(""));
        if (yamlText == null || yamlText.isBlank()) {
            return new TheTreeConfig(root, false);
        }
        Node loaded;
        try {
            // Composed (not constructed) so repeated keys survive: a Map would keep only the last.
            // new Yaml() is not thread-safe for reuse; create one per parse.
            loaded = new Yaml().compose(new StringReader(yamlText));
        } catch (RuntimeException e) {
            // Malformed YAML: behave as if there were no config rather than breaking the tree.
            return new TheTreeConfig(root, false);
        }
        if (loaded instanceof MappingNode mapping) {
            fill(root, mapping);
        }
        return new TheTreeConfig(root, true);
    }

    /**
     * Applies one YAML mapping onto {@code node}, in document order: properties on itself, other
     * keys as children. Repeated keys are all applied, the later ones on top.
     */
    private static void fill(NodeConfig node, MappingNode map) {
        for (NodeTuple entry : map.getValue()) {
            if (!(entry.getKeyNode() instanceof ScalarNode keyNode)) {
                continue;
            }
            String key = keyNode.getValue();
            Node value = entry.getValueNode();
            if (key.startsWith("/")) {
                applyProperty(node, key, value);
            } else {
                NodeConfig target = expand(node, key);
                if (value instanceof MappingNode mapping) {
                    fill(target, mapping);
                }
                // A null value (bare "name:") simply declares the node with defaults.
                // Scalars are ignored: a folder/file entry is expected to be a mapping.
            }
        }
    }

    /** Expands a possibly-slashed key into a chain of child nodes, returning the deepest one. */
    private static NodeConfig expand(NodeConfig node, String key) {
        NodeConfig current = node;
        for (String segment : key.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            current = current.childForParsing(segment);
        }
        return current;
    }

    /**
     * Applies a single {@code /}-prefixed property; unknown names are logged and ignored. The
     * past-tense names ({@code /compacted}, {@code /weight}, {@code /hidden}) are kept as aliases.
     */
    private static void applyProperty(NodeConfig node, String key, Node value) {
        switch (key) {
            case "/compact", "/compacted" -> node.setCompacted(toBoolean(scalarOf(value)));
            case "/order", "/weight" -> node.setWeight(toInteger(scalarOf(value)));
            case "/hide", "/hidden" -> node.setHidden(toBoolean(scalarOf(value)));
            case "/rename" -> node.setRename(toFragments(value));
            default ->
                // Unknown property: ignore (forward compatible).
                LOG.warn("Provided key \"" + key + "\" starts with a '/' but does not exist.");
        }
    }

    /** The text of a non-null scalar, or {@code null} for a null, mapping or sequence value. */
    @Nullable
    private static String scalarOf(Node value) {
        return value instanceof ScalarNode scalar && !Tag.NULL.equals(scalar.getTag()) ? scalar.getValue() : null;
    }

    /**
     * Reads a {@code /rename} value: a plain string (one regular fragment), or a sequence whose
     * items are strings or {@code {attrs: <TextStyle>, text: <string>}} mappings.
     *
     * @return the fragments, or {@code null} if the value describes no text at all.
     */
    @Nullable
    private static List<NameFragment> toFragments(Node value) {
        String plain = scalarOf(value);
        if (plain != null) {
            return List.of(new NameFragment(plain, TextStyle.R));
        }
        if (!(value instanceof SequenceNode sequence)) {
            return null;
        }
        List<NameFragment> out = new ArrayList<>();
        for (Node item : sequence.getValue()) {
            String text = scalarOf(item);
            if (text != null) {
                out.add(new NameFragment(text, TextStyle.R));
            } else if (item instanceof MappingNode mapping) {
                NameFragment fragment = toFragment(mapping);
                if (fragment != null) {
                    out.add(fragment);
                }
            }
        }
        return out.isEmpty() ? null : out;
    }

    /** Reads one {@code {attrs, text}} fragment; an unknown {@code attrs} falls back to regular. */
    @Nullable
    private static NameFragment toFragment(MappingNode mapping) {
        String text = null;
        String attrs = null;
        for (NodeTuple entry : mapping.getValue()) {
            String key = scalarOf(entry.getKeyNode());
            if ("text".equals(key)) {
                text = scalarOf(entry.getValueNode());
            } else if ("attrs".equals(key)) {
                attrs = scalarOf(entry.getValueNode());
            }
        }
        if (text == null) {
            return null;
        }
        TextStyle style = TextStyle.of(attrs);
        if (style == null) {
            if (attrs != null) {
                LOG.warn("Unknown /rename attrs \"" + attrs + "\"; using R.");
            }
            style = TextStyle.R;
        }
        return new NameFragment(text, style);
    }

    /** Lenient boolean coercion (true/false, yes/no, non-zero numbers); {@code null} if unusable. */
    @Nullable
    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof String) {
            String s = ((String) value).trim();
            if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes")) return Boolean.TRUE;
            if (s.equalsIgnoreCase("false") || s.equalsIgnoreCase("no")) return Boolean.FALSE;
        }
        return null;
    }

    /** Lenient integer coercion (numbers and numeric strings); {@code null} if unusable. */
    @Nullable
    private static Integer toInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
