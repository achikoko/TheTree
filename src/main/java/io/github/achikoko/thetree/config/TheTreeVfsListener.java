package io.github.achikoko.thetree.config;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Refreshes the project view when something changes that the platform won't propagate to the
 * content root on its own: a {@code thetree.yaml} edit, or any structural change (folder/file
 * created, deleted, moved, renamed) that can flip a compacted branch's collapse decision.
 */
public final class TheTreeVfsListener implements BulkFileListener {
    
    private final Project project;
    private final AtomicBoolean refreshScheduled = new AtomicBoolean(false);
    
    /** @param project injected by the platform when the listener is registered. */
    public TheTreeVfsListener(Project project) {
        this.project = project;
    }
    
    /**
     * Inspects a batch of VFS events: a config touch invalidates the cache, and any structural
     * change under the project schedules a single coalesced project-view rebuild.
     */
    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
        if (project.isDisposed()) {
            return;
        }
        String basePath = project.getBasePath();
        boolean configTouched = false;
        boolean structureTouched = false;
        
        for (VFileEvent event : events) {
            String path = event.getPath();
//            if (path == null) {
//                continue;
//            }
            if (isConfigFile(path)) {
                configTouched = true;
                structureTouched = true;
            } else if (isStructural(event) && underProject(path, basePath)) {
                structureTouched = true;
            }
        }
        
        if (configTouched) {
            TheTreeConfigService.getInstance(project).invalidate();
        }
        if (structureTouched) {
            scheduleRefresh();
        }
    }
    
    /** Whether the path points at a config file (any accepted name), wherever in the project it lives. */
    private static boolean isConfigFile(@NotNull String path) {
        return TheTreeConfigService.isConfigFileName(path.substring(path.lastIndexOf('/') + 1));
    }
    
    /** A change that alters tree shape — not a plain content edit. */
    private static boolean isStructural(@NotNull VFileEvent event) {
        if (event instanceof VFileContentChangeEvent) {
            return false;
        }
        if (event instanceof VFileCreateEvent
            || event instanceof VFileDeleteEvent
            || event instanceof VFileMoveEvent
            || event instanceof VFileCopyEvent) {
            return true;
        }
        if (event instanceof VFilePropertyChangeEvent) {
            return VirtualFile.PROP_NAME.equals(((VFilePropertyChangeEvent) event).getPropertyName());
        }
        return false;
    }
    
    /** Whether the path lies inside the project (an unknown base path is treated as "yes"). */
    private static boolean underProject(@NotNull String path, String basePath) {
        return basePath == null || path.equals(basePath) || path.startsWith(basePath + "/");
    }
    
    /** Queues at most one full project-view rebuild on the EDT, coalescing event bursts. */
    private void scheduleRefresh() {
        if (!refreshScheduled.compareAndSet(false, true)) {
            return; // one already queued — coalesce bursts (checkout, build output, etc.)
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            refreshScheduled.set(false);
            if (project.isDisposed()) {
                return;
            }
            ProjectView view = ProjectView.getInstance(project);
            AbstractProjectViewPane pane = view.getCurrentProjectViewPane();
            if (pane != null) {
                pane.updateFromRoot(true); // full rebuild from root, keep expansion state
            } else {
                view.refresh();
            }
        });
    }
}
