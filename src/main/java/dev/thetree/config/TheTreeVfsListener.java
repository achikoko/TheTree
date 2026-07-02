package dev.thetree.config;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Listens for VFS changes and, whenever a {@code .thetree.yaml} is created, edited, moved or
 * deleted, invalidates the cache and refreshes the project view so the tree reflects the change
 * without a manual refresh. Registered per-project via {@code <projectListeners>} in plugin.xml.
 */
public final class TheTreeVfsListener implements BulkFileListener {

    private final Project project;

    public TheTreeVfsListener(Project project) {
        this.project = project;
    }

    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
        boolean touched = false;
        for (VFileEvent event : events) {
            String path = event.getPath();
            if (path != null && path.endsWith("/" + TheTreeConfigService.CONFIG_FILE_NAME)
                    || path != null && path.equals(TheTreeConfigService.CONFIG_FILE_NAME)) {
                touched = true;
                break;
            }
        }
        if (!touched || project.isDisposed()) {
            return;
        }
        TheTreeConfigService.getInstance(project).invalidate();
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed()) {
                ProjectView.getInstance(project).refresh();
            }
        });
    }
}
