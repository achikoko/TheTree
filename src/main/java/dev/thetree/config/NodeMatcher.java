package dev.thetree.config;

/**
 * Matches a single path segment (a file or folder name) against a key from {@code .thetree.yaml}.
 *
 * <p>Today only {@link Literal} exact matching is implemented. The abstraction exists so that
 * gitignore-style patterns can be added later without touching the rest of the plugin:
 * <ul>
 *     <li>{@code *}      &rarr; any single segment</li>
 *     <li>{@code *.ext}  &rarr; any file segment ending in {@code .ext}</li>
 *     <li>{@code **}     &rarr; any number of nested segments (a "deep" matcher)</li>
 * </ul>
 * To add these, introduce new {@code NodeMatcher} implementations and teach
 * {@link NodeConfig#child(String)} / a resolver to consult non-literal matchers (and to
 * treat {@code **} as spanning multiple segments). Nothing else needs to change.
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

    static NodeMatcher of(String key) {
        // For now every key is treated literally. When pattern support is enabled this factory
        // is the single place that decides which matcher a key maps to.
        return new Literal(key);
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
