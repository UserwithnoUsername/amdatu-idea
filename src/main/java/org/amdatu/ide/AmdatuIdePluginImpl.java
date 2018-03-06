package org.amdatu.ide;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.amdatu.ide.imp.BndProjectImporter;
import org.jetbrains.annotations.NotNull;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.util.messages.MessageBusConnection;

import aQute.bnd.build.ProjectBuilder;
import aQute.bnd.build.Workspace;
import aQute.bnd.header.Parameters;
import aQute.bnd.osgi.Builder;
import aQute.bnd.osgi.Constants;
import aQute.bnd.osgi.Jar;
import aQute.bnd.repository.maven.provider.MavenBndRepository;
import aQute.bnd.service.Refreshable;
import aQute.bnd.service.RepositoryPlugin;
import aQute.service.reporter.Report;

import static org.amdatu.ide.i18n.OsmorcBundle.message;

public class AmdatuIdePluginImpl implements AmdatuIdePlugin {

    public static final String IDEA_TMP_GENERATED = ".idea-tmp-generated";

    private static final Logger LOG = Logger.getInstance(AmdatuIdePluginImpl.class);
    public static final NotificationGroup NOTIFICATIONS =
            new NotificationGroup("Amdatu IDE", NotificationDisplayType.TOOL_WINDOW, true);


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

                    // TODO: This could slow down startup a bit but we need these.
                    generateExportedContentsJars();

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
                Notifications.Bus.notify(new Notification("amdatu-ide", "Error", formatMessage(error), NotificationType.ERROR));
            }
        }
    }

    private String formatMessage(String message) {
        Report.Location location = workspace.getLocation(message);
        if (location == null) {
            return message;
        } else {
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
            List<File> workspaceFiles = new ArrayList<>();
            if (workspace.getIncluded() != null) {
                workspaceFiles.addAll(workspace.getIncluded());
            }
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
            Set<String> modulesToRefresh = ContainerUtil.newHashSet();
            for (VFileEvent event : events) {
                VirtualFile file = event.getFile();
                if (myWorkspaceFileNames.contains(file.getCanonicalPath())) {
                    // A workspace file has changed (.bnd file in cnf folder) refresh the workspace

                    // TODO: Do we need to de-bounce this refresh operation??
                    refreshWorkspace = true;
                    break;
                }

                if (file.getName().endsWith(".bnd")) {
                    // Bnd file not part set of workspace configuration files has changed
                    importProjects = true;
                    Module module = ProjectFileIndex.getInstance(myProject).getModuleForFile(file);
                    modulesToRefresh.add(module.getName());
                }
            }

            boolean finalRefreshWorkspace = refreshWorkspace;
            boolean finalImportProjects = importProjects;

            new Task.Backgroundable(myProject, "Refreshing", true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    if (finalRefreshWorkspace) {
                        refreshWorkspace();
                        updateWorkspaceFileNames();
                        // TODO: This is not the best place, come up with a good moment to generate these jars.
                        generateExportedContentsJars();
                    }
                    if (finalRefreshWorkspace || finalImportProjects) {
//                generateExportedContentsJars(); // TODO: Moved to do this after workspace refresh only for now
                        if (!finalRefreshWorkspace) {
                            synchronized (workspaceLock) {
                                for (String moduleName : modulesToRefresh) {
                                    try {
                                        aQute.bnd.build.Project project = getWorkspace().getProject(moduleName);
                                        project.clear();
                                        project.refresh();
                                    } catch (Exception e) {
                                        LOG.error("Failed to refresh project for module " + moduleName, e);
                                    }
                                }
                            }
                        }

                        reImportProjects();
                    }
                }
            }.queue();
        }
    }

    public void generateExportedContentsJars() {
        try {
            workspace.getAllProjects().forEach(p -> {
                try {
                    p.getIncluded();
                    ProjectBuilder builder = p.getBuilder(null);
                    for (Builder subBuilder : builder.getSubBuilders()) {
                        if (isExportingNonModuleClasses(subBuilder)) {
                            File properties = subBuilder.getPropertiesFile();
                            if (properties == null) {
                                properties = p.getPropertiesFile();
                            }

                            File base = properties.getParentFile();
                            aQute.bnd.build.Project project = new aQute.bnd.build.Project(getWorkspace(), base);

                            project.setBase(base);
                            project.set(Constants.DEFAULT_PROP_BIN_DIR, "bin_dummy");
                            project.set(Constants.DEFAULT_PROP_TARGET_DIR, IDEA_TMP_GENERATED);
                            project.prepare();


                            Builder projectBuilder = new ProjectBuilder(project);
                            if (subBuilder.getPropertiesFile() != null) {
                                projectBuilder = projectBuilder.getSubBuilder(subBuilder.getPropertiesFile());
                            }
                            projectBuilder.setBase(base);
                            Jar build = projectBuilder.build();
                            File outputFile = project.getOutputFile(projectBuilder.getBsn(), projectBuilder.getVersion());
                            build.write(outputFile);
                        }
                    }
                } catch (Exception e) {
                    LOG.error("Failed to generate magic buildpath jar for project: " + p, e);
                }
            });
        } catch (Exception e) {
            LOG.error("Failed to generate magic buildpath jar", e);
        }

    }

    private boolean isExportingNonModuleClasses(Builder builder) {
        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(myProject);
        ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();

        Parameters exportPackage = builder.getExportPackage();
        for (String pkg : exportPackage.keySet()) {
            if (pkg.endsWith("*")) {
                pkg = pkg.substring(0, pkg.length() - 1);
            }
            if (pkg.endsWith(".")) {
                pkg = pkg.substring(0, pkg.length() - 1);
            }

            try {
                PsiPackage psiPackage = javaPsiFacade.findPackage(pkg);
                if (psiPackage == null) {
                    Notifications.Bus.notify(new Notification("amdatu-ide", "DEBUG", "No psi package for: " + pkg, NotificationType.INFORMATION));
                    continue;
                }

                if (psiPackage.getDirectories() == null && psiPackage.getDirectories() == null) { // Check twice the first call somehow returns null sometimes where the second call doesn't
                    Notifications.Bus.notify(new Notification("amdatu-ide", "DEBUG", "No dirs for package: " + pkg, NotificationType.INFORMATION));
                }

                for (PsiDirectory psiDirectory : psiPackage.getDirectories()) {
                    if (index.getModuleForFile(psiDirectory.getVirtualFile()) == null) {
                        return true;
                    }
                }

            } catch (Exception e) {
                LOG.error("Failed to determine if package '" + pkg + "' is part of a module", e);
            }
        }
        return false;
    }

    @Override
    public boolean reportErrors(aQute.bnd.build.Project project) {
        return report(project, NotificationType.ERROR);
    }

    @Override
    public boolean reportWarnings(aQute.bnd.build.Project project) {
        return report(project, NotificationType.WARNING);
    }

    private boolean report(aQute.bnd.build.Project project, NotificationType type) {
        List<String> messages;
        switch (type) {
            case ERROR:
                messages = project.getErrors();
                break;
            case WARNING:
                messages = project.getWarnings();
                break;
            default:
                throw new RuntimeException("Unsupported type " + type);
        }

        if (messages != null && !messages.isEmpty()) {
            for (String message : messages) {
                LOG.info("Bnd project project: " + project.getName() + " message: " + message);
                NOTIFICATIONS.createNotification("Amdatu IDE", message, type, null).notify(myProject);
            }
            return true;
        } else {
            return false;
        }
    }


}
