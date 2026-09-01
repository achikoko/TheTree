package io.github.achikoko.thetree.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent, per-project settings for The Tree. Toggles are surfaced as actions in the
 * Project tool window's appearance menu.
 */
@State(
        name = "dev.thetree.settings.TheTreeConfigState",
        storages = @Storage("TheTreePlugin.xml")
)
public final class TheTreeConfigState implements PersistentStateComponent<TheTreeConfigState> {

    /** @return the project-level settings instance. */
    public static TheTreeConfigState getInstance(Project project) {
        return project.getService(TheTreeConfigState.class);
    }

    private boolean compactSourceFolder = true;
    private boolean showModuleLibraries = false;
    private boolean useConfigFile = true;

    /** Whether source roots and declared-compacted folders are hoisted into single nodes. */
    public boolean getCompactSourceFolder() {
        return compactSourceFolder;
    }

    public void setCompactSourceFolder(boolean value) {
        this.compactSourceFolder = value;
    }

    /** Whether a virtual "Libraries" node is appended to each module. */
    public boolean getShowModuleLibraries() {
        return showModuleLibraries;
    }

    public void setShowModuleLibraries(boolean value) {
        this.showModuleLibraries = value;
    }

    /** Whether {@code .thetree.yaml} is honored (declared-compacted folders, weights, hidden). */
    public boolean getUseConfigFile() {
        return useConfigFile;
    }

    public void setUseConfigFile(boolean value) {
        this.useConfigFile = value;
    }

    @Nullable
    @Override
    public TheTreeConfigState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull TheTreeConfigState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
