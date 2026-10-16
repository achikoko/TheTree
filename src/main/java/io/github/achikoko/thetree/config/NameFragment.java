package io.github.achikoko.thetree.config;

import org.jetbrains.annotations.NotNull;

/**
 * One styled piece of a {@code /rename}d node label.
 *
 * @param text  the text to show.
 * @param style how to render it.
 */
public record NameFragment(@NotNull String text, @NotNull TextStyle style) {
}
