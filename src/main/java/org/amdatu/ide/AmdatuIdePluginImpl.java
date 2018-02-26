package org.amdatu.ide;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.amdatu.ide.imp.BndProjectImporter;
import org.jetbrains.annotations.NotNull;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.messages.MessageBusConnection;

import aQute.bnd.build.Workspace;
import aQute.bnd.repository.maven.provider.MavenBndRepository;
import aQute.bnd.service.Refreshable;
import aQute.bnd.service.RepositoryPlugin;
import aQute.service.reporter.Report;

public class AmdatuIdePluginImpl implements AmdatuIdePlugin {

    private static final Logger LOG = Logger.getInstance(AmdatuIdePlugin.class);

    private final Object workspaceLock = new Object();
    private final Project myProject;
    private Workspace workspace;

    public AmdatuIdePluginImpl(Project project) {
        myProject = project;
    }

    @Override
    public boolean isBndWorkspace() {
        File projectBase = new File(myProject.getBasePath());
        File cnfDir = new File(projectBase, Workspace.CNFDIR);
        return cnfDir.isDirectory() && getWorkspace() != null;
    }

    @Override
    public Workspace getWorkspace() {
        synchronized (workspaceLock) {
            if (workspace == null) {
                try {
                    workspace = new Workspace(new File(myProject.getBasePath()));

                    Notifications.Bus.notify(new Notification("amdatu-ide", "Success", "Created bnd workspace'", NotificationType.INFORMATION));
                    reportWorkspaceIssues();

                    MessageBusConnection connection = myProject.getMessageBus().connect();
                    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BndFileChangedListener());
                } catch (Exception e) {
                    LOG.error("Failed to create bnd workspace", e);
                    throw new RuntimeException(e); // TODO: Just logging should do but for now this makes errors show quickly
                }
            }
            return workspace;
        }
    }

    public void refreshWorkspace() {
        synchronized (workspaceLock) {
            if (workspace == null) {
                Notifications.Bus.notify(new Notification("amdatu-ide", "Success", "Workspace not initialized, not refresing'", NotificationType.INFORMATION));
                return;
            }
            long start = System.currentTimeMillis();
            workspace.clear();
            workspace.refresh();

            refreshRepositories();

            Notifications.Bus.notify(new Notification("amdatu-ide", "Success", "Workspace refreshed in " + (System.currentTimeMillis() - start) + " ms", NotificationType.INFORMATION));
            reportWorkspaceIssues();
        }
    }

    @Override
    public void reImportProjects() {
        /* TODO: Only refresh if the workspace has no errors?!
         * This makes sense but not sure if a project error will bubble up to the workspace as well
         */
        if (workspace.isOk()) {
            BndProjectImporter.reimportWorkspace(myProject);
            Notifications.Bus.notify(new Notification("amdatu-ide", "Success", "Projects re-imported", NotificationType.INFORMATION));
        } else {
            LOG.warn("Workspace not ok, not re-importing projects.");
        }
    }

    private void refreshRepositories() {
        List<RepositoryPlugin> plugins = workspace.getPlugins(RepositoryPlugin.class);
        for (RepositoryPlugin plugin : plugins) {
            if (plugin instanceof Refreshable) {
                try {
                    ((Refreshable) plugin).refresh();
                } catch (Exception e) {
                    if (plugin instanceof MavenBndRepository && e instanceof NullPointerException) {
                        // This repo doesn't init until it's used and throws an NPE on refresh
                        // TODO: Report as BND issue (if not already fixed in next)
                        LOG.info("Failed to refresh repository, '" + plugin.getName() + "'", e);
                    } else {
                        LOG.error("Failed to refresh repository, '" + plugin.getName() + "'", e);
                    }

                }
            }
        }
    }

    private void reportWorkspaceIssues() {
        if (workspace.getWarnings() != null && !workspace.getWarnings().isEmpty()) {
            for (String warning : workspace.getWarnings()) {
                Notifications.Bus.notify(new Notification("amdatu-ide", "Warning", formatMessage(warning), NotificationType.WARNING));
            }
        }

        if (workspace.getErrors() != null && !workspace.getErrors().isEmpty()) {
            for (String error : workspace.getErrors()) {
                Notifications.Bus.notify(new Notification("amdatu-ide", "Error",  formatMessage(error), NotificationType.ERROR));
            }
        }
    }

    private String formatMessage(String message) {
        Report.Location location = workspace.getLocation(message);
        if (location == null) {
            return message;
        }
        else {
            int line = location.line + 1; // lines seem to start at 0 for bnd
            return "[file: '" + location.file + "', line: " + line + "]: " + message;
        }
    }

    /**
     * {@link BulkFileListener} handling changes in bnd files.
     * <p>
     * <ul>
     * <li>Refreshes the workspace if on changes in workspace configuration files (cnf/build.bnd and all other files
     * that are part that are part of the workspace configuration either included using -include statements or by
     * placing them in cnf/ext)</li>
     * <p>
     * <li>Re-Imports projects on changes in other bnd files</li>
     * </ul>
     */
    private class BndFileChangedListener implements BulkFileListener {

        private final Set<String> myWorkspaceFileNames = new HashSet<>();

        public BndFileChangedListener() {
            updateWorkspaceFileNames();
        }

        private void updateWorkspaceFileNames() {
            List<File> workspaceFiles = new ArrayList<>(workspace.getIncluded());
            workspaceFiles.add(workspace.getPropertiesFile());

            myWorkspaceFileNames.clear();
            myWorkspaceFileNames.addAll(workspaceFiles.stream()
                    .map(File::getAbsolutePath)
                    .collect(Collectors.toSet()));
        }

        @Override
        public void after(@NotNull List<? extends VFileEvent> events) {
            boolean refreshWorkspace = false;
            boolean importProjects = false;
            for (VFileEvent event : events) {
                if (myWorkspaceFileNames.contains(event.getFile().getCanonicalPath())) {
                    // A workspace file has changed (.bnd file in cnf folder) refresh the workspace

                    // TODO: Do we need to de-bounce this refresh operation??
                    refreshWorkspace = true;
                    break;
                }

                if (event.getFile().getName().endsWith(".bnd")) {
                    // Bnd file not part set of workspace configuration files has changed
                    importProjects = true;
                }
            }
            if (refreshWorkspace) {
                refreshWorkspace();
                updateWorkspaceFileNames();
            }
            if (refreshWorkspace || importProjects) {
                reImportProjects();
            }
        }
    }
}
