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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.amdatu.idea.AmdatuIdeaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;

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
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleJdkOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;

import aQute.bnd.build.Container;
import aQute.bnd.build.Project;
import aQute.bnd.build.ProjectBuilder;
import aQute.bnd.build.Workspace;
import aQute.bnd.header.Parameters;
import aQute.bnd.osgi.Builder;
import aQute.bnd.osgi.Constants;
import aQute.bnd.osgi.Instructions;
import aQute.bnd.osgi.Jar;
import static org.amdatu.idea.i18n.OsmorcBundle.message;

public class BndProjectImporter {

    private static final Logger LOG = Logger.getInstance(BndProjectImporter.class);

    private static final String IDEA_TMP_GENERATED = ".idea-tmp-generated";
    private static final String BND_LIB_PREFIX = "bnd:";
    private static final String BND_EXPORTED_CONTENTS_PREFIX = "bnd-exported:";
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
    private final Collection<Project> myProjects;
    private final Map<String, String> mySourcesMap = ContainerUtil.newTroveMap(FileUtil.PATH_HASHING_STRATEGY);

    public BndProjectImporter(@NotNull com.intellij.openapi.project.Project project,
                              @NotNull Collection<Project> toImport) {
        myProject = project;
        myProjects = toImport;
    }

    @NotNull
    Module createRootModule(@NotNull ModifiableModuleModel model) {
        String rootDir = myProject.getBasePath();
        assert rootDir != null : myProject;
        String imlPath = rootDir + File.separator + myProject.getName() + ModuleFileType.DOT_DEFAULT_EXTENSION;
        Module module = model.newModule(imlPath, StdModuleTypes.JAVA.getId());
        ModuleRootModificationUtil.addContentRoot(module, rootDir);
        ModuleRootModificationUtil.setSdkInherited(module);
        return module;
    }

    void setupProject() {
        AmdatuIdeaPlugin amdatuIdeaPlugin= myProject.getComponent(AmdatuIdeaPlugin.class);
        Workspace workspace = amdatuIdeaPlugin.getWorkspace();

        LanguageLevel sourceLevel = LanguageLevel.parse(workspace.getProperty(Constants.JAVAC_SOURCE));
        if (sourceLevel != null) {
            LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(sourceLevel);
        }

        String targetLevel = workspace.getProperty(Constants.JAVAC_TARGET);
        CompilerConfiguration.getInstance(myProject).setProjectBytecodeTarget(targetLevel);

        // compilation options (see Project#getCommonJavac())
        JpsJavaCompilerOptions javacOptions = JavacConfiguration.getOptions(myProject, JavacConfiguration.class);

        javacOptions.DEBUGGING_INFO = booleanProperty(workspace.getProperty("javac.debug", "true"));
        javacOptions.DEPRECATION = booleanProperty(workspace.getProperty("java.deprecation"));
        javacOptions.ADDITIONAL_OPTIONS_STRING = workspace.getProperty("java.options", "");
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
        } else {
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
            } catch (Exception e) {
                LOG.warn(e);
                return false;
            } finally {
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
        } catch (Exception ignored) {
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
                        } catch (IOException e) {
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
                // Remove modules that no longer exist
                for (Module module : moduleModel.getModules()) {
                    String moduleDir = PathUtil.getParentPath(module.getModuleFilePath());
                    if (moduleDir.equals(myProject.getBasePath())) {
                        // Don't remove the root module.
                        continue;
                    }
                    VirtualFile bndFile = LocalFileSystem.getInstance().findFileByPath(moduleDir + "/bnd.bnd");
                    if (bndFile == null || !bndFile.exists()) {
                        moduleModel.disposeModule(module);
                    }
                }

                // Create modules
                for (Project project : myProjects) {
                    try {
                        rootModels.put(project, createModule(moduleModel, project, projectLevel));
                        createExportedContentLibraries(project, libraryModel);
                    } catch (Exception e) {
                        LOG.error(e);  // should not happen, since project.prepare() is already called
                    }
                }
                // Set dependencies for modules
                for (Project project : myProjects) {
                    try {
                        setDependencies(moduleModel, libraryModel, rootModels.get(project), project);
                    } catch (Exception e) {
                        LOG.error(e);  // should not happen, since project.prepare() is already called
                    }
                }

                cleanupUnusedLibraries(moduleModel, rootModels, libraryModel);
            } finally {
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

    private void createExportedContentLibraries(Project project, LibraryTable.ModifiableModel libraryModel) {
        try {
            ProjectBuilder builder = project.getBuilder(null);
            for (Builder subBuilder : builder.getSubBuilders()) {

                String libName = BND_EXPORTED_CONTENTS_PREFIX + builder.getBsn();

                if (isExportingBuildpathContent(project, subBuilder)) {
                    File outputFile = generateExportedContentJar(project, subBuilder);

                    Library library = libraryModel.getLibraryByName(libName);
                    if (library == null) {
                        library = libraryModel.createLibrary(libName);
                    }
                    Library.ModifiableModel model = library.getModifiableModel();
                    String[] urls = model.getUrls(OrderRootType.CLASSES);
                    for (String url : urls) {
                        model.removeRoot(url, OrderRootType.CLASSES);
                    }

                    model.addRoot(url(outputFile), OrderRootType.CLASSES);

                    model.commit();
                } else {
                    Library library = libraryModel.getLibraryByName(libName);
                    if (library != null) {
                        libraryModel.removeLibrary(library);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to create exported content libraries", e);
        }
    }

    private boolean isExportingBuildpathContent(Project project, Builder builder) throws Exception {
        Parameters exportPackage = builder.getExportPackage();
        if (exportPackage == null || exportPackage.isEmpty()) {
            return false;
        }

        Collection<Container> buildpath = project.getBuildpath();
        for (Container container : buildpath) {
            if (container.getType() == Container.TYPE.REPO || container.getType() == Container.TYPE.EXTERNAL) {
                try (Jar jar = new Jar(container.getFile())) {
                    Instructions instructions = new Instructions(exportPackage);

                    for (String packageName : jar.getPackages()) {
                        if (packageName.trim().isEmpty()) {
                            continue;
                        }
                        if (instructions.matches(packageName)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @NotNull
    private File generateExportedContentJar(Project project, Builder subBuilder) throws Exception {
        File properties = project.getPropertiesFile();
        File base = properties.getParentFile();
        File target = new File(base, IDEA_TMP_GENERATED);
        File outputFile = new File(target, subBuilder.getBsn() + ".jar");

        Project tmpProject = new Project(project.getWorkspace(), base);

        tmpProject.setBase(base);
        tmpProject.set(Constants.DEFAULT_PROP_BIN_DIR, "bin_dummy");
        tmpProject.set(Constants.DEFAULT_PROP_TARGET_DIR, IDEA_TMP_GENERATED);
        tmpProject.prepare();

        Builder projectBuilder = new ProjectBuilder(tmpProject) {
            @Override
            public Manifest calcManifest() {
                return new Manifest();
            }
        };
        if (subBuilder.getPropertiesFile() != null) {
            projectBuilder = projectBuilder.getSubBuilder(subBuilder.getPropertiesFile());
        }
        projectBuilder.setBase(base);

        if (!outputFile.exists()) {
            if (outputFile.exists() && !outputFile.delete()) {
                LOG.warn("Failed to delete exported content jar: " + outputFile.getName());
            }

            Jar build = projectBuilder.build();
            build.write(outputFile);
        }
        return outputFile;
    }

    private void cleanupUnusedLibraries(ModifiableModuleModel moduleModel, Map<Project, ModifiableRootModel> rootModels, LibraryTable.ModifiableModel libraryModel) {
        // Use the updated ones as changes are not yet committed so newly added libraries won't be visible if a new model is created
        Map<Module, ModifiableRootModel> updatedModuleModels = rootModels.values().stream()
                .collect(Collectors.toMap(ModuleRootModel::getModule, modifiableRootModel -> modifiableRootModel));

        Set<String> usedLibraryNames = Arrays.stream(moduleModel.getModules())
                .map(module -> updatedModuleModels
                        .computeIfAbsent(module, m -> ModuleRootManager.getInstance(module).getModifiableModel()))
                .flatMap(modifiableModuleModel -> Arrays.stream(modifiableModuleModel.getOrderEntries())
                        .filter(LibraryOrderEntry.class::isInstance)
                        .map(LibraryOrderEntry.class::cast)
                        .map(LibraryOrderEntry::getLibrary)
                        .filter(Objects::nonNull)
                        .map(Library::getName))
                .collect(Collectors.toSet());

        Arrays.stream(libraryModel.getLibraries())
                .filter(library -> !usedLibraryNames.contains(library.getName()))
                .filter(library -> library.getName() != null && library.getName().startsWith(BND_LIB_PREFIX))
                .forEach(libraryModel::removeLibrary);
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
            } catch (IllegalArgumentException e) {
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
            if (myProject.getBasePath() != null && FileUtil.isAncestor(new File(myProject.getBasePath()), file, true)) {
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
                            isExportingDependencyModulePackage(rootModel, dependency);
                    entry.setExported(exportingDependencyModulePackage);
                }

                String fixedBsn = dependency.getBundleSymbolicName().replaceAll("\\.jar$", "");
                Library library = libraryModel.getLibraryByName(BND_EXPORTED_CONTENTS_PREFIX + fixedBsn);
                if (library != null) {
                    entry = rootModel.addLibraryEntry(library);
                }

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

    private boolean isExportingDependencyModulePackage(ModifiableRootModel rootModel, Container dependency) {
        try {
            Project dependencyProject = dependency.getProject();
            Workspace workspace = dependencyProject.getWorkspace();

            HashSet<String> dependencyModulePackages = new HashSet<>();
            for (File sourceRoot : dependencyProject.getSourcePath()) {
                collectPackages(sourceRoot, "", dependencyModulePackages);
            }

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
        } catch (Exception e) {
            LOG.error("Failed to determine exported state", e);
        }
        return false;
    }

    private void collectPackages(File sourceRoot, String baseName, Set<String> packages) {
        File[] children = sourceRoot.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                String base = baseName + (baseName.length() > 0 ? "." : "") + child.getName();
                collectPackages(child, base, packages);
            } else if ("java".equals(getExtension(child))) {
                packages.add(baseName);
            }
        }
    }

    @NotNull
    private String getExtension(File child) {
        return child.getName().substring(child.getName().lastIndexOf('.') + 1);
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

            } else {
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
    private static Collection<Project> getWorkspaceProjects(@NotNull Workspace workspace) throws Exception {
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
        } else {
            doReimportWorkspace(project);
        }
    }

    private static void doReimportWorkspace(com.intellij.openapi.project.Project project) {
        Workspace workspace = project.getComponent(AmdatuIdeaPlugin.class).getWorkspace();
        assert workspace != null : project;

        Collection<Project> projects;
        try {
            projects = getWorkspaceProjects(workspace);
        } catch (Exception e) {
            LOG.error("ws=" + workspace.getBase(), e);
            return;
        }

        Runnable task = () -> {
            BndProjectImporter importer = new BndProjectImporter(project, projects);
            importer.setupProject();
            importer.resolve(true);
        };
        if (!isUnitTestMode()) {
            ApplicationManager.getApplication().invokeLater(task, project.getDisposed());
        } else {
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
        } else {
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
        } catch (Exception e) {
            LOG.error("ws=" + workspace.getBase() + " pr=" + projectDirs, e);
            return;
        }

        Runnable task = () -> new BndProjectImporter(project, projects).resolve(true);
        if (!isUnitTestMode()) {
            ApplicationManager.getApplication().invokeLater(task, project.getDisposed());
        } else {
            task.run();
        }
    }

}