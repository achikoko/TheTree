package io.github.achikoko.thetree.config;

/**
 * Matches a single path segment (a file or folder name) against a key from {@code .thetree.yaml}.
 *
 * <p>Three flavours exist, all created through {@link #of(String)}:
 * <ul>
 *     <li>{@link Literal} &rarr; exact, case-sensitive segment match ({@code src})</li>
 *     <li>{@link Glob}    &rarr; single-segment glob, where {@code *} matches any run of characters
 *         and {@code ?} exactly one ({@code *.iml}, {@code file?.txt})</li>
 *     <li>{@link Deep}    &rarr; the {@code **} wildcard, spanning zero or more nested segments</li>
 * </ul>
 *
 * <p>A matcher only decides whether one segment is accepted. Precedence between competing matchers
 * (literal first, then globs by specificity, {@code **} last) lives in
 * {@link NodeConfig#orderedPatternChildren()} and the walk in {@code TheTreeConfig#resolve}.
 */
public interface NodeMatcher {

    /** @return true if this matcher accepts the given single path segment. */
    boolean matches(String segment);

    /** @return true if this matcher is a plain literal (fast-path, exact key). */
    boolean isLiteral();

    /** @return the literal text if {@link #isLiteral()} is true, otherwise the raw pattern. */
    String pattern();

    /**
     * Whether the matcher may span more than one nested segment (i.e. {@code **}).
     * Always false for now; wired for future use.
     */
    default boolean isDeep() {
        return false;
    }

    /**
     * Creates the matcher a YAML key calls for: {@link Deep} for {@code **}, {@link Glob} when the
     * key contains {@code *} or {@code ?}, and {@link Literal} otherwise.
     *
     * @param key one path segment as written in {@code .thetree.yaml} (never slash-separated;
     *            slashed keys are expanded by the parser before reaching here).
     */
    static NodeMatcher of(String key) {
        if (key.equals("**")) {
            return new Deep();
        }
        if (key.indexOf('*') >= 0 || key.indexOf('?') >= 0) {
            return new Glob(key);
        }
        return new Literal(key);
    }
    
    /**
     * Single-segment glob: {@code *} = any run of characters, {@code ?} = exactly one, e.g.
     * {@code *.iml}. The glob never crosses a {@code /}; use {@link Deep} to span segments.
     */
    final class Glob implements NodeMatcher {
        private final String raw;
        private final java.util.regex.Pattern regex;
        
        Glob(String raw) {
            this.raw = raw;
            this.regex = java.util.regex.Pattern.compile(toRegex(raw));
        }
        
        /** Translates the glob into an anchored regex, escaping every regex metacharacter. */
        private static String toRegex(String glob) {
            StringBuilder sb = new StringBuilder("^");
            for (int i = 0; i < glob.length(); i++) {
                char c = glob.charAt(i);
                switch (c) {
                    case '*' -> sb.append("[^/]*");
                    case '?' -> sb.append("[^/]");
                    case '.', '\\', '+', '(', ')', '[', ']', '{', '}', '^', '$', '|' ->
                            sb.append('\\').append(c);
                    default -> sb.append(c);
                }
            }
            return sb.append('$').toString();
        }
        
        @Override public boolean matches(String segment) { return regex.matcher(segment).matches(); }
        @Override public boolean isLiteral() { return false; }
        @Override public String pattern() { return raw; }
    }
    
    /** Deep wildcard {@code **}: matches zero or more nested segments. */
    final class Deep implements NodeMatcher {
        @Override public boolean matches(String segment) { return true; }
        @Override public boolean isLiteral() { return false; }
        @Override public boolean isDeep() { return true; }
        @Override public String pattern() { return "**"; }
    }
    
    /** Exact, case-sensitive segment match. */
    final class Literal implements NodeMatcher {
        private final String value;

        Literal(String value) {
            this.value = value;
        }

        @Override
        public boolean matches(String segment) {
            return value.equals(segment);
        }

        @Override
        public boolean isLiteral() {
            return true;
        }

        @Override
        public String pattern() {
            return value;
        }
    }
}
