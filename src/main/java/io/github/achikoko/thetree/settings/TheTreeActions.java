package io.github.achikoko.thetree.settings;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleOptionAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Toggle actions added to the Project tool window "Appearance" menu. Adapted from the original
 * FolderCompact plugin and extended with The Tree's own options.
 */
public final class TheTreeActions {

    private TheTreeActions() {
    }

    /**
     * Adapts a getter/setter pair on {@link TheTreeConfigState} into the {@code Option} the
     * platform's {@link ToggleOptionAction} expects, refreshing the project view on change.
     */
    static final class OptionSupplier implements Function<AnActionEvent, ToggleOptionAction.Option> {
        private final Function<TheTreeConfigState, Boolean> getter;
        private final BiConsumer<TheTreeConfigState, Boolean> setter;

        OptionSupplier(Function<TheTreeConfigState, Boolean> getter,
                       BiConsumer<TheTreeConfigState, Boolean> setter) {
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public ToggleOptionAction.Option apply(AnActionEvent e) {
            Project project = e.getProject();
            final TheTreeConfigState state = project == null ? null : TheTreeConfigState.getInstance(project);
            return new ToggleOptionAction.Option() {
                @Override
                public boolean isSelected() {
                    return state != null && getter.apply(state);
                }

                @Override
                public void setSelected(boolean selected) {
                    if (state == null) {
                        return;
                    }
                    boolean changed = getter.apply(state) != selected;
                    setter.accept(state, selected);
                    if (changed && project != null) {
                        ProjectView.getInstance(project).refresh();
                    }
                }
            };
        }
    }

    /** Toggles compaction of source roots and declared-compacted folders. */
    public static class CompactSourceFolders extends ToggleOptionAction {
        public CompactSourceFolders() {
            super(new OptionSupplier(TheTreeConfigState::getCompactSourceFolder,
                    TheTreeConfigState::setCompactSourceFolder));
        }
    }

    /** Toggles the virtual "Libraries" node shown per module. */
    public static class ShowModuleLibraries extends ToggleOptionAction {
        public ShowModuleLibraries() {
            super(new OptionSupplier(TheTreeConfigState::getShowModuleLibraries,
                    TheTreeConfigState::setShowModuleLibraries));
        }
    }

    /** Toggles whether {@code .thetree.yaml} is honored at all. */
    public static class UseConfigFile extends ToggleOptionAction {
        public UseConfigFile() {
            super(new OptionSupplier(TheTreeConfigState::getUseConfigFile,
                    TheTreeConfigState::setUseConfigFile));
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            super.update(e);
        }
    }
}
