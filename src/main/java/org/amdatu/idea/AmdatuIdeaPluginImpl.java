/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.amdatu.idea;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.amdatu.idea.imp.BndProjectImporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vcs.BranchChangeListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;

import aQute.bnd.build.Workspace;
import aQute.bnd.header.Attrs;
import aQute.bnd.osgi.Constants;
import aQute.bnd.repository.maven.provider.MavenBndRepository;
import aQute.bnd.service.Refreshable;
import aQute.bnd.service.RepositoryPlugin;
import aQute.service.reporter.Report;

public class AmdatuIdeaPluginImpl implements AmdatuIdeaPlugin {

    private static final Logger LOG = Logger.getInstance(AmdatuIdeaPluginImpl.class);

    private final Object workspaceLock = new Object();
    private final Project myProject;
    private Workspace myWorkspace;
    private List<Report.Location> myWorkspaceErrors;
    private final AmdatuIdeaNotificationService myNotificationService;
    private PackageInfoService myPackageInfoService;

    static {
        Workspace.setDriver(Constants.BNDDRIVER_INTELLIJ);
        Workspace.addGestalt(Constants.GESTALT_INTERACTIVE, new Attrs());
    }

    public AmdatuIdeaPluginImpl(Project project) {
        myProject = project;
        myNotificationService = new AmdatuIdeaNotificationService(project);
    }

    @Override
    public boolean isBndWorkspace() {
        if (myProject.getBasePath() == null) {
            return false;
        }

        File projectBase = new File(myProject.getBasePath());
        File cnfDir = new File(projectBase, Workspace.CNFDIR);
        return cnfDir.isDirectory();
    }

    @Nullable
    @Override
    public Workspace getWorkspace() {
        if (myWorkspace == null && myProject.getBasePath() != null && !isBndWorkspace()) {
            return null;
        }

        if (myWorkspace != null) {
            return myWorkspace;
        }

        synchronized (workspaceLock) {
            if (myWorkspace == null) {
                try {
                    Task.WithResult<Workspace, Exception> createdBndWorkspace =
                            new Task.WithResult<Workspace, Exception>(myProject, "Creating Workspace", false) {

                                @Override
                                protected Workspace compute(@NotNull ProgressIndicator indicator) throws Exception {
                                    //noinspection ConstantConditions - checked above
                                    Workspace workspace = new Workspace(new File(getProject().getBasePath()));
                                    if (workspace.getErrors() != null) {
                                        myWorkspaceErrors = workspace.getErrors().stream()
                                                .map(msg -> {
                                                    Report.Location location = workspace.getLocation(msg);
                                                    if (location == null) {
                                                        location = new Report.Location() {};
                                                        location.message = msg;
                                                    }
                                                    return location;
                                                })
                                                .collect(Collectors.toList());
                                    }

                                    myNotificationService.info("Created bnd workspace");
                                    return workspace;
                                }
                            };
                    createdBndWorkspace.queue();
                    myWorkspace = createdBndWorkspace.getResult();
                    myPackageInfoService = new PackageInfoService(myProject, this);

                    reportWorkspaceIssues();

                    RepoUtilKt.validateRepoLocations(this);

                    reImportProjects();

                    MessageBusConnection messageBusConnection = myProject.getMessageBus().connect();
                    BndFileChangedListener fileChangedListener = new BndFileChangedListener();
                    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, fileChangedListener);
                    messageBusConnection.subscribe(BranchChangeListener.VCS_BRANCH_CHANGED, fileChangedListener);
                } catch (Exception e) {
                    LOG.error("Failed to create bnd workspace", e);
                }
            }
            return myWorkspace;
        }
    }

    @Override
    public void refreshWorkspace(boolean forceRefresh) {
        long start = System.currentTimeMillis();

        if (myWorkspace == null) {
            myNotificationService.info("Workspace not initialized, not refreshing'");
            return;
        }

        new Task.Backgroundable(myProject, "Refreshing Bnd Workspace", false) {

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                synchronized (workspaceLock) {
                    myWorkspace.clear();
                    if (myWorkspace.refresh()) {
                        if (myWorkspace.getErrors() != null) {
                            myWorkspaceErrors = myWorkspace.getErrors().stream()
                                    .map(myWorkspace::getLocation)
                                    .collect(Collectors.toList());
                        }

                    } else if (forceRefresh) {
                        LOG.info("Forced workspace refresh");
                        myWorkspace.forceRefresh();
                        for (aQute.bnd.build.Project project : myWorkspace.getCurrentProjects()) {
                            project.forceRefresh();
                        }
                    } else {
                        // not refreshed
                        return;
                    }

                    if (myWorkspace.getErrors() != null) {
                        myWorkspaceErrors = myWorkspace.getErrors().stream()
                                .map(myWorkspace::getLocation)
                                .collect(Collectors.toList());
                    }

                    reportWorkspaceIssues();

                    if (myWorkspaceErrors.isEmpty()) {
                        indicator.setText("Refreshing Repositories");
                        refreshRepositories(indicator);
                        RepoUtilKt.validateRepoLocations(AmdatuIdeaPluginImpl.this);

                        reImportProjects();

                        WorkspaceRefreshedNotifier workspaceRefreshedNotifier =
                                myProject.getMessageBus().syncPublisher(WorkspaceRefreshedNotifier.WORKSPACE_REFRESHED);
                        workspaceRefreshedNotifier.workpaceRefreshed();

                        myNotificationService.info("Workspace refreshed in " + (System.currentTimeMillis() - start) + " ms");
                    } else {
                        myNotificationService.warning("Workspace has errors, not re-importing projects.");
                    }
                }
            }
        }.queue();
    }

    private void reImportProjects() {
        BndProjectImporter.reimportWorkspace(myProject);
    }

    private void refreshRepositories(ProgressIndicator indicator) {
        List<RepositoryPlugin> plugins = myWorkspace.getPlugins(RepositoryPlugin.class);
        for (int i = 0; i < plugins.size(); i++) {
            RepositoryPlugin plugin = plugins.get(i);
            if (plugin instanceof Refreshable) {
                try {
                    indicator.setText("Refreshing repo " + plugin.getName());
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
            indicator.setFraction((double) i / (double) plugins.size());
        }
    }

    private void reportWorkspaceIssues() {
        for (Report.Location error : myWorkspaceErrors) {
            myNotificationService.error(error);
        }
    }

    /**
     * {@link BulkFileListener} handling changes in bnd files.
     *
     * <ul>
     * <li>Refreshes the workspace if on changes in workspace configuration files (cnf/build.bnd and all other files
     * that are part that are part of the workspace configuration either included using -include statements or by
     * placing them in cnf/ext)</li>
     *
     * <li>Re-Imports projects on changes in other bnd files</li>
     * </ul>
     */
    private class BndFileChangedListener implements BulkFileListener, BranchChangeListener {

        private volatile boolean branchWillChange;
        private volatile String branchName;

        private final VcsRepositoryManager myVcsRepositoryManager = VcsRepositoryManager.getInstance(myProject);

        BndFileChangedListener() {
            Repository vcsRepository = myVcsRepositoryManager.getRepositoryForFile(myProject.getBaseDir());
            if (vcsRepository != null) {
                vcsRepository.update();
                branchName = vcsRepository.getCurrentBranchName();
            }
        }

        private Set<String> workspaceFileNames() {
            List<File> workspaceFiles = new ArrayList<>();
            if (myWorkspace.getIncluded() != null) {
                workspaceFiles.addAll(myWorkspace.getIncluded());
            }
            workspaceFiles.add(myWorkspace.getPropertiesFile());

            return workspaceFiles.stream()
                    .map(File::getAbsolutePath)
                    .collect(Collectors.toSet());
        }

        @Override
        public void after(@NotNull List<? extends VFileEvent> events) {

            if (branchWillChange) {
                // vsc branch is about to change once that's done we'll do a workspace refresh
                return;
            }

            Repository vcsRepository = myVcsRepositoryManager.getRepositoryForFile(myProject.getBaseDir());
            if (vcsRepository != null) {
                vcsRepository.update();

                if (vcsRepository.getState() == Repository.State.REBASING
                        || vcsRepository.getState() == Repository.State.MERGING) {
                    // wait for the final change, clear the current branch will trigger a full workspace refresh on the
                    // first change after rebase / merge.
                    branchName = null;
                    return;
                }

                // Detect branch changes performed by an external VCS client (e.g. command line git or Sourcetree.
                String currentBranchName = vcsRepository.getCurrentBranchName();
                if (branchName == null || !branchName.equals(currentBranchName)) {
                    branchWillChange = true; // prevent additional triggers during refresh
                    branchHasChanged(currentBranchName);
                    return;
                }
            }

            boolean refreshWorkspace = false;
            boolean importProjects = false;
            Set<String> modulesToRefresh = ContainerUtil.newHashSet();
            for (VFileEvent event : events) {

                if (workspaceFileNames().contains(event.getPath())) {
                    // A workspace file has changed (.bnd file in cnf folder) refresh the workspace
                    refreshWorkspace = true;
                    break;
                }

                if (AmdatuIdeaConstants.BND_EXT.equals(PathUtil.getFileExtension(event.getPath()))) {
                    VirtualFile file = event.getFile();
                    if (file == null) {
                        continue;
                    }

                    Module module = ProjectFileIndex.getInstance(myProject).getModuleForFile(file);
                    if (module != null) {
                        // Bnd file not part set of workspace configuration files has changed
                        importProjects = true;
                        modulesToRefresh.add(module.getName());
                    }
                }
            }

            boolean finalRefreshWorkspace = refreshWorkspace;
            boolean finalImportProjects = importProjects;

            new Task.Backgroundable(myProject, "Refreshing", true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    if (finalRefreshWorkspace) {
                        refreshWorkspace(false);
                    } else if (finalImportProjects && !myWorkspaceErrors.isEmpty()) {
                        refreshWorkspace(false);
                    } else if (finalImportProjects) {
                        synchronized (workspaceLock) {
                            List<String> bndProjectPaths = ContainerUtil.newArrayList();
                            for (String moduleName : modulesToRefresh) {
                                try {
                                    aQute.bnd.build.Project project = myWorkspace.getProject(moduleName);
                                    if (project != null) {
                                        project.clear();
                                        project.refresh();
                                        bndProjectPaths.add(project.getPropertiesFile().getParentFile().getAbsolutePath());
                                    }
                                } catch (Exception e) {
                                    LOG.error("Failed to refresh project for module " + moduleName, e);
                                }
                            }

                            BndProjectImporter.reimportProjects(myProject, bndProjectPaths);

                            // TODO: Create specific event for changed module might be better
                            WorkspaceRefreshedNotifier workspaceRefreshedNotifier =
                                    myProject.getMessageBus().syncPublisher(WorkspaceRefreshedNotifier.WORKSPACE_REFRESHED);
                            workspaceRefreshedNotifier.workpaceRefreshed();

                        }
                    }
                }
            }.queue();
        }

        @Override
        public void branchWillChange(@NotNull String branchName) {
            branchWillChange = true;
        }

        @Override
        public void branchHasChanged(@Nullable String branchName) {
            this.branchName = branchName;
            refreshWorkspace(true);
            branchWillChange = false;
        }
    }

    @Override
    public AmdatuIdeaNotificationService getNotificationService() {
        return myNotificationService;
    }

    @Override
    public PackageInfoService getPackageInfoService() {
        return myPackageInfoService;
    }

}
