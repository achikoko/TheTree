package io.github.achikoko.thetree.config;

import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The text styles a {@code /rename} fragment may use, named by short codes in {@code attrs}:
 * {@code R}egular, {@code E}rror and {@code G}rayed, with {@code B}old, {@code I}talic or
 * {@code SM}all variants. Each maps to one {@link SimpleTextAttributes} constant.
 */
public enum TextStyle {
    R(SimpleTextAttributes.REGULAR_ATTRIBUTES),
    RB(SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES),
    RI(SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES),
    E(SimpleTextAttributes.ERROR_ATTRIBUTES),
    G(SimpleTextAttributes.GRAYED_ATTRIBUTES),
    GB(SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES),
    GI(SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES),
    GSM(SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);

    private final SimpleTextAttributes attributes;

    TextStyle(SimpleTextAttributes attributes) {
        this.attributes = attributes;
    }

    /** @return the platform attributes this style renders with. */
    public @NotNull SimpleTextAttributes attributes() {
        return attributes;
    }

    /** @return the style named by {@code code} (case-insensitive), or {@code null} if unknown. */
    public static @Nullable TextStyle of(@Nullable String code) {
        if (code == null) {
            return null;
        }
        for (TextStyle style : values()) {
            if (style.name().equalsIgnoreCase(code.trim())) {
                return style;
            }
        }
        return null;
    }
}
