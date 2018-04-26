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

import aQute.bnd.build.ProjectBuilder;
import aQute.bnd.build.Workspace;
import aQute.bnd.header.Attrs;
import aQute.bnd.header.Parameters;
import aQute.bnd.osgi.Builder;
import aQute.bnd.osgi.Constants;
import aQute.bnd.osgi.Jar;
import aQute.bnd.repository.maven.provider.MavenBndRepository;
import aQute.bnd.service.Refreshable;
import aQute.bnd.service.RepositoryPlugin;
import aQute.service.reporter.Report;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.amdatu.idea.imp.BndProjectImporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AmdatuIdeaPluginImpl implements AmdatuIdeaPlugin {

    public static final String IDEA_TMP_GENERATED = ".idea-tmp-generated";

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

        synchronized (workspaceLock) {
            if (myWorkspace == null) {
                try {
                    myWorkspace = new Workspace(new File(myProject.getBasePath()));
                    if (myWorkspace.getErrors() != null) {
                        myWorkspaceErrors = myWorkspace.getErrors().stream()
                                        .map(myWorkspace::getLocation)
                                        .collect(Collectors.toList());
                    }

                    myNotificationService.info("Created bnd workspace");

                    myPackageInfoService = new PackageInfoService(myProject, this);

                    reportWorkspaceIssues();

                    RepoUtilKt.validateRepoLocations(this);

                    // TODO: This could slow down startup a bit but we need these.
                    generateExportedContentsJars(true);

                    reImportProjects();

                    MessageBusConnection messageBusConnection = myProject.getMessageBus().connect();
                    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BndFileChangedListener());
                }
                catch (Exception e) {
                    LOG.error("Failed to create bnd workspace", e);
                }
            }
            return myWorkspace;
        }
    }

    @Override
    public void refreshWorkspace(boolean forceRefresh) {
        long start = System.currentTimeMillis();
        synchronized (workspaceLock) {
            if (myWorkspace == null) {
                myNotificationService.info("Workspace not initialized, not refreshing'");
                return;
            }
            myWorkspace.clear();
            if (myWorkspace.refresh()) {
                if (myWorkspace.getErrors() != null) {
                    myWorkspaceErrors = myWorkspace.getErrors().stream()
                                    .map(myWorkspace::getLocation)
                                    .collect(Collectors.toList());
                }

            }
            else if (forceRefresh) {
                LOG.info("Forced workspace refresh");
                myWorkspace.forceRefresh();
                if (myWorkspace.getErrors() != null) {
                    myWorkspaceErrors = myWorkspace.getErrors().stream()
                                    .map(myWorkspace::getLocation)
                                    .collect(Collectors.toList());
                }
            }
            else {
                // not refreshed
                return;
            }
            reportWorkspaceIssues();
        }

        if (myWorkspaceErrors.isEmpty()) {
            refreshRepositories();

            RepoUtilKt.validateRepoLocations(this);

            // TODO: This is not the best place, come up with a good moment to generate these jars.
            generateExportedContentsJars(forceRefresh);

            reImportProjects();

            WorkspaceRefreshedNotifier workspaceRefreshedNotifier =
                            myProject.getMessageBus().syncPublisher(WorkspaceRefreshedNotifier.WORKSPACE_REFRESHED);
            workspaceRefreshedNotifier.workpaceRefreshed();

            myNotificationService.info("Workspace refreshed in " + (System.currentTimeMillis() - start) + " ms");
        }
        else {
            myNotificationService.warning("Workspace has errors, not re-importing projects.");
        }

    }

    private void reImportProjects() {
        for (aQute.bnd.build.Project project : myWorkspace.getCurrentProjects()) {
            project.clear();
            if (!project.refresh()) {
                project.forceRefresh();
            }
        }

        BndProjectImporter.reimportWorkspace(myProject);
        myNotificationService.info("Projects re-imported");
    }

    private void refreshRepositories() {
        List<RepositoryPlugin> plugins = myWorkspace.getPlugins(RepositoryPlugin.class);
        for (RepositoryPlugin plugin : plugins) {
            if (plugin instanceof Refreshable) {
                try {
                    ((Refreshable) plugin).refresh();
                }
                catch (Exception e) {
                    if (plugin instanceof MavenBndRepository && e instanceof NullPointerException) {
                        // This repo doesn't init until it's used and throws an NPE on refresh
                        // TODO: Report as BND issue (if not already fixed in next)
                        LOG.info("Failed to refresh repository, '" + plugin.getName() + "'", e);
                    }
                    else {
                        LOG.error("Failed to refresh repository, '" + plugin.getName() + "'", e);
                    }
                }
            }
        }
    }

    private void reportWorkspaceIssues() {
        for (Report.Location error : myWorkspaceErrors) {
            myNotificationService.error(error);
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
            boolean refreshWorkspace = false;
            boolean importProjects = false;
            Set<String> modulesToRefresh = ContainerUtil.newHashSet();
            for (VFileEvent event : events) {
                VirtualFile file = event.getFile();
                if (file == null) {
                    continue;
                }

                if (workspaceFileNames().contains(file.getPath())) {
                    // A workspace file has changed (.bnd file in cnf folder) refresh the workspace
                    refreshWorkspace = true;
                    break;
                }

                if (file.getName().endsWith(".bnd")) {
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
                    }
                    else if (finalImportProjects && !myWorkspaceErrors.isEmpty()) {
                        refreshWorkspace(false);
                    }
                    else if (finalImportProjects) {
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
                                }
                                catch (Exception e) {
                                    LOG.error("Failed to refresh project for module " + moduleName, e);
                                }
                            }

                            BndProjectImporter.reimportProjects(myProject, bndProjectPaths);

                            // TODO: Create specific event for changed module might be better
                            WorkspaceRefreshedNotifier workspaceRefreshedNotifier =
                                            myProject.getMessageBus()
                                                            .syncPublisher(WorkspaceRefreshedNotifier.WORKSPACE_REFRESHED);
                            workspaceRefreshedNotifier.workpaceRefreshed();

                        }
                    }
                }
            }.queue();
        }
    }

    private void generateExportedContentsJars(boolean rebuildExisting) {
        try {
            myWorkspace.getAllProjects().forEach(p -> {
                try {
                    p.getIncluded();
                    File properties = p.getPropertiesFile();
                    File base = properties.getParentFile();
                    File target = new File(base, IDEA_TMP_GENERATED);

                    ProjectBuilder builder = p.getBuilder(null);
                    for (Builder subBuilder : builder.getSubBuilders()) {

                        File outputFile = new File(target, subBuilder.getBsn() + ".jar");

                        if (isExportingNonModuleClasses(subBuilder)) {
                            aQute.bnd.build.Project project = new aQute.bnd.build.Project(myWorkspace, base);

                            project.setBase(base);
                            project.set(Constants.DEFAULT_PROP_BIN_DIR, "bin_dummy");
                            project.set(Constants.DEFAULT_PROP_TARGET_DIR, IDEA_TMP_GENERATED);
                            project.prepare();

                            Builder projectBuilder = new ProjectBuilder(project);
                            if (subBuilder.getPropertiesFile() != null) {
                                projectBuilder = projectBuilder.getSubBuilder(subBuilder.getPropertiesFile());
                            }
                            projectBuilder.setBase(base);

                            if (!outputFile.exists() || rebuildExisting) {
                                if (outputFile.exists() && !outputFile.delete()) {
                                    LOG.warn("Failed to delete exporteds content jar: " + outputFile.getName());
                                }

                                Jar build = projectBuilder.build();
                                build.write(outputFile);
                            }
                        }
                        else if (outputFile.exists() && !outputFile.delete()) {
                            LOG.error("Failed to delete no longer required exported contents jar: " +
                                            outputFile.getName());
                        }
                    }
                }
                catch (Exception e) {
                    LOG.error("Failed to generate magic buildpath jar for project: " + p, e);
                }
            });
        }
        catch (Exception e) {
            LOG.error("Failed to generate magic buildpath jar", e);
        }

    }

    private boolean isExportingNonModuleClasses(Builder builder) {
        return ApplicationManager.getApplication().runReadAction((Computable<Boolean>) () -> {
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
                        continue;
                    }

                    //noinspection ConstantConditions Check twice the first call somehow returns null sometimes where the second call doesn't
                    if (psiPackage.getDirectories() == null && psiPackage.getDirectories() == null) {
                        LOG.info("No dirs for package: " + pkg);
                    }

                    for (PsiDirectory psiDirectory : psiPackage.getDirectories()) {
                        if (index.getModuleForFile(psiDirectory.getVirtualFile()) == null) {
                            return true;
                        }
                    }

                }
                catch (Exception e) {
                    LOG.error("Failed to determine if package '" + pkg + "' is part of a module", e);
                }
            }
            return false;
        });
    }

    @Override
    public AmdatuIdeaNotificationService getNotificationService() {
        return myNotificationService;
    }

    @Override
    public PackageInfoService getPackageInfoSevice() {
        return myPackageInfoService;
    }

}
