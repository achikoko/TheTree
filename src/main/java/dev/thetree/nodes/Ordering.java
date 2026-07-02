package dev.thetree.nodes;

/**
 * Shared ordering logic for The Tree's nodes.
 *
 * <p>Semantics required by the spec: a node's configured {@code weight} defaults to 0; a lower
 * weight renders <em>above</em> nodes with a higher weight, and this ordering crosses the
 * folder/file divide (a file with weight -1 sits above an unmarked folder). Nodes with equal
 * weight keep the IDE's usual order (folders first, then alphabetical).
 *
 * <p>The IntelliJ project view sorts children with a comparator, not by a per-node "final index",
 * so we express weight through the two hooks that comparator consults:
 * <ul>
 *   <li>{@code getWeight()} &ndash; used by {@code AlphaComparator} when "Folders Always on Top"
 *       is OFF. Lower value sorts earlier.</li>
 *   <li>{@code getTypeSortWeight(boolean)} &ndash; used by {@code GroupByTypeComparator} when
 *       "Folders Always on Top" is ON (the default). Higher value sorts earlier.</li>
 * </ul>
 *
 * <p>The configured-weight term dominates the small folder/file term in both formulas, so weight
 * always wins and ties fall back to folders-first + name.
 *
 * <p>NOTE: if you ever enable "Sort by Type" in the project view, the platform groups by file
 * type first and weight then applies within a group. If ordering ever looks inverted under
 * "Folders Always on Top", flip the sign of the weight term in {@link #typeSortWeight}.
 */
public final class Ordering {

    /** How strongly configured weight dominates the folder/file tie-breaker. */
    private static final int WEIGHT_STEP = 100;

    /** Large base so virtual "bottom" nodes can be pushed below everything with a big weight. */
    private static final int TYPE_BASE = 1_000_000;

    private Ordering() {
    }

    /** Ascending: lower result sorts earlier. */
    public static int weight(int configuredWeight, boolean directory) {
        return configuredWeight * WEIGHT_STEP + (directory ? 0 : 1);
    }

    /** Descending: higher result sorts earlier. */
    public static int typeSortWeight(int configuredWeight, boolean directory) {
        return TYPE_BASE - configuredWeight * WEIGHT_STEP + (directory ? 1 : 0);
    }
}
