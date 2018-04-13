/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.amdatu.idea.imp;

import aQute.bnd.build.Container;
import aQute.bnd.build.Project;
import aQute.bnd.build.Workspace;
import aQute.bnd.osgi.Builder;
import aQute.bnd.osgi.Constants;
import aQute.bnd.osgi.Instructions;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ExportableOrderEntry;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleJdkOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ModuleSourceOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiPackage;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.amdatu.idea.AmdatuIdeaPlugin;
import org.amdatu.idea.AmdatuIdeaPluginImpl;
import org.amdatu.idea.inspections.PackageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.amdatu.idea.i18n.OsmorcBundle.message;

public class BndProjectImporter {

    private static final Logger LOG = Logger.getInstance(BndProjectImporter.class);

    private static final String BND_LIB_PREFIX = "bnd:";
    private static final String SRC_ROOT = "OSGI-OPT/src";
    private static final String JDK_DEPENDENCY = "ee.j2se";

    private static final Comparator<OrderEntry> ORDER_ENTRY_COMPARATOR = new Comparator<OrderEntry>() {
        @Override
        public int compare(OrderEntry o1, OrderEntry o2) {
            return weight(o1) - weight(o2);
        }

        private int weight(OrderEntry e) {
            return e instanceof JdkOrderEntry ? 2 :
                            e instanceof ModuleSourceOrderEntry ? 0 :
                                            1;
        }
    };

    private static boolean isUnitTestMode() {
        return ApplicationManager.getApplication().isUnitTestMode();
    }

    private final com.intellij.openapi.project.Project myProject;
    private final Workspace myWorkspace;
    private final Collection<Project> myProjects;
    private final Map<String, String> mySourcesMap = ContainerUtil.newTroveMap(FileUtil.PATH_HASHING_STRATEGY);

    public BndProjectImporter(@NotNull com.intellij.openapi.project.Project project,
                    @NotNull Workspace workspace,
                    @NotNull Collection<Project> toImport) {
        myProject = project;
        myWorkspace = workspace;
        myProjects = toImport;
    }

    @NotNull
    public Module createRootModule(@NotNull ModifiableModuleModel model) {
        String rootDir = myProject.getBasePath();
        assert rootDir != null : myProject;
        String imlPath = rootDir + File.separator + myProject.getName() + ModuleFileType.DOT_DEFAULT_EXTENSION;
        Module module = model.newModule(imlPath, StdModuleTypes.JAVA.getId());
        ModuleRootModificationUtil.addContentRoot(module, rootDir);
        ModuleRootModificationUtil.setSdkInherited(module);
        return module;
    }

    public void setupProject() {
        LanguageLevel sourceLevel = LanguageLevel.parse(myWorkspace.getProperty(Constants.JAVAC_SOURCE));
        if (sourceLevel != null) {
            LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(sourceLevel);
        }

        String targetLevel = myWorkspace.getProperty(Constants.JAVAC_TARGET);
        CompilerConfiguration.getInstance(myProject).setProjectBytecodeTarget(targetLevel);

        // compilation options (see Project#getCommonJavac())
        JpsJavaCompilerOptions javacOptions = JavacConfiguration.getOptions(myProject, JavacConfiguration.class);
        javacOptions.DEBUGGING_INFO = booleanProperty(myWorkspace.getProperty("javac.debug", "true"));
        javacOptions.DEPRECATION = booleanProperty(myWorkspace.getProperty("java.deprecation"));
        javacOptions.ADDITIONAL_OPTIONS_STRING = myWorkspace.getProperty("java.options", "");
    }

    public void resolve(boolean refresh) {
        if (!isUnitTestMode()) {
            new Task.Backgroundable(myProject, message("bnd.import.resolve.task"), true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    if (resolve(indicator)) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            createProjectStructure();
                            if (refresh) {
                                VirtualFileManager.getInstance().asyncRefresh(null);
                            }
                        }, BndProjectImporter.this.myProject.getDisposed());
                    }
                }
            }.queue();
        }
        else {
            resolve(null);
            createProjectStructure();
        }
    }

    private boolean resolve(@Nullable ProgressIndicator indicator) {
        int progress = 0;
        for (Project project : myProjects) {
            LOG.info("resolving: " + project.getBase());

            if (indicator != null) {
                indicator.checkCanceled();
                indicator.setText(project.getName());
            }

            AmdatuIdeaPlugin amdatuIdeaPlugin = myProject.getComponent(AmdatuIdeaPlugin.class);
            try {
                project.prepare();
            }
            catch (Exception e) {
                LOG.warn(e);
                return false;
            }
            finally {
                amdatuIdeaPlugin.getNotificationService().report(project, true);
            }

            findSources(project);

            if (indicator != null) {
                indicator.setFraction((double) (++progress) / myProjects.size());
            }
        }

        return true;
    }

    private void findSources(Project project) {
        try {
            findSources(project.getBootclasspath());
            findSources(project.getBuildpath());
            findSources(project.getTestpath());
        }
        catch (Exception ignored) {
        }
    }

    private void findSources(Collection<Container> classpath) {
        for (Container dependency : classpath) {
            Container.TYPE type = dependency.getType();
            if (type == Container.TYPE.REPO || type == Container.TYPE.EXTERNAL) {
                File file = dependency.getFile();
                if (file.isFile() && FileUtilRt.extensionEquals(file.getName(), "jar")) {
                    String path = file.getPath();
                    if (!mySourcesMap.containsKey(path)) {
                        try {
                            try (ZipFile zipFile = new ZipFile(file)) {
                                ZipEntry srcRoot = zipFile.getEntry(SRC_ROOT);
                                if (srcRoot != null) {
                                    mySourcesMap.put(path, SRC_ROOT);
                                }
                            }
                        }
                        catch (IOException e) {
                            mySourcesMap.put(path, null);
                        }
                    }
                }
            }
        }
    }

    private void createProjectStructure() {
        if (myProject.isDisposed()) {
            return;
        }

        ApplicationManager.getApplication().runWriteAction(() -> {
            LanguageLevel projectLevel = LanguageLevelProjectExtension.getInstance(myProject).getLanguageLevel();
            Map<Project, ModifiableRootModel> rootModels = ContainerUtil.newHashMap();
            ModifiableModuleModel moduleModel = ModuleManager.getInstance(myProject).getModifiableModel();
            LibraryTable.ModifiableModel libraryModel = ProjectLibraryTable.getInstance(myProject).getModifiableModel();
            try {
                for (Project project : myProjects) {
                    try {
                        rootModels.put(project, createModule(moduleModel, project, projectLevel));
                    }
                    catch (Exception e) {
                        LOG.error(e);  // should not happen, since project.prepare() is already called
                    }
                }
                for (Project project : myProjects) {
                    try {
                        setDependencies(moduleModel, libraryModel, rootModels.get(project), project);
                    }
                    catch (Exception e) {
                        LOG.error(e);  // should not happen, since project.prepare() is already called
                    }
                }
            }
            finally {
                libraryModel.commit();
                ModifiableModelCommitter.multiCommit(rootModels.values(), moduleModel);
            }
        });
    }

    private ModifiableRootModel createModule(ModifiableModuleModel moduleModel, Project project,
                    LanguageLevel projectLevel) throws Exception {
        String name = project.getName();
        Module module = moduleModel.findModuleByName(name);
        if (module == null) {
            String path = project.getBase().getPath() + File.separator + name + ModuleFileType.DOT_DEFAULT_EXTENSION;
            module = moduleModel.newModule(path, StdModuleTypes.JAVA.getId());
        }

        ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
        for (ContentEntry entry : rootModel.getContentEntries()) {
            rootModel.removeContentEntry(entry);
        }
        for (OrderEntry entry : rootModel.getOrderEntries()) {
            if (!(entry instanceof ModuleJdkOrderEntry || entry instanceof ModuleSourceOrderEntry)) {
                rootModel.removeOrderEntry(entry);
            }
        }
        rootModel.inheritSdk();

        ContentEntry contentEntry = rootModel.addContentEntry(url(project.getBase()));
        for (File src : project.getSourcePath()) {
            contentEntry.addSourceFolder(url(src), false);
        }
        File testSrc = project.getTestSrc();
        if (testSrc != null) {
            contentEntry.addSourceFolder(url(testSrc), true);
        }
        contentEntry.addExcludeFolder(url(project.getTarget()));

        LanguageLevel sourceLevel = LanguageLevel.parse(project.getProperty(Constants.JAVAC_SOURCE));
        if (sourceLevel == projectLevel)
            sourceLevel = null;
        rootModel.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(sourceLevel);

        CompilerModuleExtension compilerExt = rootModel.getModuleExtension(CompilerModuleExtension.class);
        compilerExt.inheritCompilerOutputPath(false);
        compilerExt.setExcludeOutput(true);
        compilerExt.setCompilerOutputPath(url(project.getSrcOutput()));
        compilerExt.setCompilerOutputPathForTests(url(project.getTestOutput()));

        String targetLevel = project.getProperty(Constants.JAVAC_TARGET);
        CompilerConfiguration.getInstance(myProject).setBytecodeTargetLevel(module, targetLevel);

        return rootModel;
    }

    private void setDependencies(ModifiableModuleModel moduleModel,
                    LibraryTable.ModifiableModel libraryModel,
                    ModifiableRootModel rootModel,
                    Project project) throws Exception {
        List<String> warnings = ContainerUtil.newArrayList();

        Collection<Container> boot = project.getBootclasspath();
        Set<Container> bootSet = Collections.emptySet();
        if (!boot.isEmpty()) {
            setDependencies(moduleModel, libraryModel, rootModel, project, boot, false, bootSet, warnings);
            bootSet = ContainerUtil.newHashSet(boot);

            OrderEntry[] entries = rootModel.getOrderEntries();
            if (entries.length > 2) {
                Arrays.sort(entries, ORDER_ENTRY_COMPARATOR);
                rootModel.rearrangeOrderEntries(entries);
            }
        }

        Collection<Container> buildpath = project.getBuildpath();
        Collection<Container> testpath = project.getTestpath();

        setDependencies(moduleModel, libraryModel, rootModel, project, testpath, true, bootSet, warnings);
        setDependencies(moduleModel, libraryModel, rootModel, project, buildpath, false, bootSet, warnings);

        checkWarnings(project, warnings);
    }

    private void setDependencies(ModifiableModuleModel moduleModel,
                    LibraryTable.ModifiableModel libraryModel,
                    ModifiableRootModel rootModel,
                    Project project,
                    Collection<Container> classpath,
                    boolean tests,
                    Set<Container> excluded,
                    List<String> warnings) {
        DependencyScope scope = tests ? DependencyScope.TEST : DependencyScope.COMPILE;
        for (Container dependency : classpath) {
            if (excluded.contains(dependency)) {
                continue;  // skip boot path dependency
            }
            if (dependency.getType() == Container.TYPE.PROJECT && project == dependency.getProject()) {
                continue;  // skip self-reference
            }
            try {
                addEntry(moduleModel, libraryModel, rootModel, dependency, scope);
            }
            catch (IllegalArgumentException e) {
                warnings.add(e.getMessage());
            }
        }
    }

    private void addEntry(ModifiableModuleModel moduleModel,
                    LibraryTable.ModifiableModel libraryModel,
                    ModifiableRootModel rootModel,
                    Container dependency,
                    DependencyScope scope) throws IllegalArgumentException {
        File file = dependency.getFile();
        String bsn = dependency.getBundleSymbolicName();
        String version = dependency.getVersion();

        String path = file.getPath();
        if (path.contains(": ")) {
            throw new IllegalArgumentException("Cannot resolve " + bsn + ":" + version + ": " + path);
        }

        if (JDK_DEPENDENCY.equals(bsn)) {
            String name = BND_LIB_PREFIX + bsn + ":" + version;
            if (FileUtil.isAncestor(myWorkspace.getBase(), file, true)) {
                name += "-" + myProject.getName();
            }
            ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
            Sdk jdk = jdkTable.findJdk(name);
            if (jdk == null) {
                jdk = jdkTable.createSdk(name, JavaSdk.getInstance());
                SdkModificator jdkModel = jdk.getSdkModificator();
                jdkModel.setHomePath(file.getParent());
                jdkModel.setVersionString(version);
                VirtualFile root = VirtualFileManager.getInstance().findFileByUrl(url(file));
                assert root != null : file + " " + file.exists();
                jdkModel.addRoot(root, OrderRootType.CLASSES);
                VirtualFile srcRoot = VirtualFileManager.getInstance().findFileByUrl(url(file) + SRC_ROOT);
                if (srcRoot != null)
                    jdkModel.addRoot(srcRoot, OrderRootType.SOURCES);
                jdkModel.commitChanges();
                jdkTable.addJdk(jdk);
            }
            rootModel.setSdk(jdk);
            return;
        }

        ExportableOrderEntry entry;

        switch (dependency.getType()) {
            case PROJECT: {
                String name = dependency.getProject().getName();
                Module module = moduleModel.findModuleByName(name);
                if (module == null) {
                    throw new IllegalArgumentException("Unknown module '" + name + "'");
                }
                entry = (ModuleOrderEntry) ContainerUtil.find(
                                rootModel.getOrderEntries(),
                                e -> e instanceof ModuleOrderEntry && ((ModuleOrderEntry) e).getModule() == module);
                if (entry == null) {
                    entry = rootModel.addModuleOrderEntry(module);

                    // Check if the module to which the module dependency is added is exporting contents from the
                    // dependency, in that case mark the dependency as exported.
                    boolean exportingDependencyModulePackage =
                                    isExportingDependencyModulePackage(rootModel, dependency, module);
                    entry.setExported(exportingDependencyModulePackage);
                }

                /// TEST

                File base = new File(module.getModuleFilePath()).getParentFile();
                File nonSourceDepsBundle = new File(base, AmdatuIdeaPluginImpl.IDEA_TMP_GENERATED + "/" + bsn);
                if (nonSourceDepsBundle.exists()) {
                    String libName = "idea-" + BND_LIB_PREFIX + bsn + ":" + version;
                    Library library = libraryModel.getLibraryByName(libName);
                    if (library == null) {
                        library = libraryModel.createLibrary(libName);
                    }

                    Library.ModifiableModel model = library.getModifiableModel();
                    for (String url : model.getUrls(OrderRootType.CLASSES))
                        model.removeRoot(url, OrderRootType.CLASSES);
                    for (String url : model.getUrls(OrderRootType.SOURCES))
                        model.removeRoot(url, OrderRootType.SOURCES);
                    model.addRoot(url(nonSourceDepsBundle), OrderRootType.CLASSES);

                    model.commit();

                    entry = rootModel.addLibraryEntry(library);

                }

                ///

                break;
            }

            case REPO: {
                String name = BND_LIB_PREFIX + bsn + ":" + version;
                Library library = libraryModel.getLibraryByName(name);
                if (library == null) {
                    library = libraryModel.createLibrary(name);
                }

                Library.ModifiableModel model = library.getModifiableModel();
                for (String url : model.getUrls(OrderRootType.CLASSES))
                    model.removeRoot(url, OrderRootType.CLASSES);
                for (String url : model.getUrls(OrderRootType.SOURCES))
                    model.removeRoot(url, OrderRootType.SOURCES);
                model.addRoot(url(file), OrderRootType.CLASSES);
                String srcRoot = mySourcesMap.get(path);
                if (srcRoot != null) {
                    model.addRoot(url(file) + srcRoot, OrderRootType.SOURCES);
                }
                model.commit();

                entry = rootModel.addLibraryEntry(library);
                break;
            }

            case EXTERNAL: {
                Library library = rootModel.getModuleLibraryTable().createLibrary(file.getName());
                Library.ModifiableModel model = library.getModifiableModel();
                model.addRoot(url(file), OrderRootType.CLASSES);
                String srcRoot = mySourcesMap.get(path);
                if (srcRoot != null) {
                    model.addRoot(url(file) + srcRoot, OrderRootType.SOURCES);
                }
                model.commit();
                entry = rootModel.findLibraryOrderEntry(library);
                assert entry != null : library;
                break;
            }

            default:
                throw new IllegalArgumentException(
                                "Unknown dependency '" + dependency + "' of type " + dependency.getType());
        }

        entry.setScope(scope);
    }

    private boolean isExportingDependencyModulePackage(ModifiableRootModel rootModel, Container dependency,
                    Module module) {
        try {
            Project dependencyProject = dependency.getProject();
            Workspace workspace = dependencyProject.getWorkspace();


            Set<String> dependencyModulePackages = PackageUtil.Companion.getPsiPackagesForModule(module).stream()
                            .map(PsiPackage::getQualifiedName)
                            .collect(Collectors.toSet());

            String dependerModuleName = rootModel.getModule().getName();
            Project dependerBndProject = workspace.getProject(dependerModuleName);
            List<Builder> subBuilders = dependerBndProject.getBuilder(null).getSubBuilders();
            for (Builder subBuilder : subBuilders) {
                Instructions instructions = new Instructions(subBuilder.getExportPackage());

                if (instructions.isEmpty()) {
                    continue;
                }

                for (String dependencyModulePackage : dependencyModulePackages) {
                    if (instructions.matches(dependencyModulePackage)) {
                        return true;
                    }
                }
            }
        }
        catch (Exception e) {
            LOG.error("Failed to determine exported state", e);
        }
        return false;
    }

    private void checkWarnings(Project project, List<String> warnings) {
        if (warnings != null && !warnings.isEmpty()) {
            if (!isUnitTestMode()) {
                LOG.warn(warnings.toString());

                NotificationType type = NotificationType.WARNING;
                String text = message("bnd.import.warn.text", project.getName(),
                                "<br>" + StringUtil.join(warnings, "<br>"));

                myProject.getComponent(AmdatuIdeaPlugin.class).getNotificationService()
                                .notification(type, message("bnd.import.warn.title"), text);

            }
            else {
                throw new AssertionError(warnings.toString());
            }
        }
    }

    private static boolean booleanProperty(String value) {
        return "on".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
    }

    private static String url(File file) {
        return VfsUtil.getUrlForLibraryRoot(file);
    }

    @NotNull
    public static Collection<Project> getWorkspaceProjects(@NotNull Workspace workspace) throws Exception {
        return ContainerUtil.filter(workspace.getAllProjects(), Condition.NOT_NULL);
    }

    public static void reimportWorkspace(@NotNull com.intellij.openapi.project.Project project) {
        if (!isUnitTestMode()) {
            new Task.Backgroundable(project, message("bnd.reimport.task"), true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    doReimportWorkspace(project);
                }
            }.queue();
        }
        else {
            doReimportWorkspace(project);
        }
    }

    private static void doReimportWorkspace(com.intellij.openapi.project.Project project) {
        Workspace workspace = project.getComponent(AmdatuIdeaPlugin.class).getWorkspace();
        assert workspace != null : project;

        Collection<Project> projects;
        try {
            projects = getWorkspaceProjects(workspace);
        }
        catch (Exception e) {
            LOG.error("ws=" + workspace.getBase(), e);
            return;
        }

        Runnable task = () -> {
            BndProjectImporter importer = new BndProjectImporter(project, workspace, projects);
            importer.setupProject();
            importer.resolve(true);
        };
        if (!isUnitTestMode()) {
            ApplicationManager.getApplication().invokeLater(task, project.getDisposed());
        }
        else {
            task.run();
        }
    }

    public static void reimportProjects(@NotNull com.intellij.openapi.project.Project project,
                    @NotNull Collection<String> projectDirs) {
        if (!isUnitTestMode()) {
            new Task.Backgroundable(project, message("bnd.reimport.task"), true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    doReimportProjects(project, projectDirs, indicator);
                }
            }.queue();
        }
        else {
            doReimportProjects(project, projectDirs, null);
        }
    }

    private static void doReimportProjects(com.intellij.openapi.project.Project project,
                    Collection<String> projectDirs,
                    ProgressIndicator indicator) {
        Workspace workspace = project.getComponent(AmdatuIdeaPlugin.class).getWorkspace();
        assert workspace != null : project;

        Collection<Project> projects;
        try {
            projects = ContainerUtil.newArrayListWithCapacity(projectDirs.size());
            for (String dir : projectDirs) {
                if (indicator != null)
                    indicator.checkCanceled();
                Project p = workspace.getProject(PathUtil.getFileName(dir));
                if (p != null) {
                    projects.add(p);
                }
            }
        }
        catch (Exception e) {
            LOG.error("ws=" + workspace.getBase() + " pr=" + projectDirs, e);
            return;
        }

        Runnable task = () -> new BndProjectImporter(project, workspace, projects).resolve(true);
        if (!isUnitTestMode()) {
            ApplicationManager.getApplication().invokeLater(task, project.getDisposed());
        }
        else {
            task.run();
        }
    }

}