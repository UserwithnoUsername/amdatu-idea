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

package org.amdatu.idea.jps;

import aQute.bnd.build.Project;
import aQute.bnd.build.ProjectBuilder;
import aQute.bnd.build.Workspace;
import aQute.bnd.header.Parameters;
import aQute.bnd.osgi.Builder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.impl.BuildRootDescriptorImpl;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AmdatuIdeaModuleBasedBuildTarget extends ModuleBasedTarget<BuildRootDescriptor> {

    private static final Logger LOGGER = Logger.getInstance(AmdatuIdeaModuleBasedBuildTarget.class);
    private static final Pattern INCLUDE_RESOURCE_SOURCE_DEST_SPLIT = Pattern.compile("\\s*=\\s*");

    private final Workspace bndWorkspace;

    AmdatuIdeaModuleBasedBuildTarget(Workspace bndWorkspace, JpsModule module) {
        super(AmdatuIdeaModuleBasedTargetType.INSTANCE, module);
        this.bndWorkspace = bndWorkspace;
    }

    Workspace getBndWorkspace() {
        return bndWorkspace;
    }

    @Override
    public String getId() {
        return getModule().getName();
    }

    @Override
    public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry, TargetOutputIndex outputIndex) {
        BuildTargetRegistry.ModuleTargetSelector selector = BuildTargetRegistry.ModuleTargetSelector.PRODUCTION;

        Collection<BuildTarget<?>> dependencies = new HashSet<>();
        dependencies.addAll(targetRegistry.getModuleBasedTargets(getModule(), selector));

        Project project = bndWorkspace.getProject(getModule().getName());
        try {

            List<String> dependson = project.getDependson().stream().map(Project::getName).collect(Collectors.toList());
            if (!dependson.isEmpty()) {
                targetRegistry.getAllTargets(AmdatuIdeaModuleBasedTargetType.INSTANCE)
                        .stream()
                        .filter(target -> dependson.contains(target.getId()))
                        .forEach(dependencies::add);

            }

        } catch (Exception e) {
            LOGGER.error("Failed to compute dependecies", e);
            throw new RuntimeException(e);
        }

        return Collections.unmodifiableCollection(dependencies);
    }

    @NotNull
    @Override
    public List<BuildRootDescriptor> computeRootDescriptors(JpsModel model, ModuleExcludeIndex index,
                                                            IgnoredFileIndex ignoredFileIndex, BuildDataPaths dataPaths) {
        List<BuildRootDescriptor> rootDescriptors = new ArrayList<>();

        try (Project project = bndWorkspace.getProject(getModule().getName())){
            rootDescriptors.add(new BuildRootDescriptorImpl(this, project.getPropertiesFile()));

            ProjectBuilder builder = project.getBuilder(null);
            for (Builder subBuilder : builder.getSubBuilders()) {
                // When there is just a single builder the properties file will be null, ok to ignore as this would be the
                // project properties file which we already added
                if (subBuilder.getPropertiesFile() != null) {
                    rootDescriptors.add(new BuildRootDescriptorImpl(this, subBuilder.getPropertiesFile()));
                }

                // Add included resources so the bundle will rebuild when an included resource has changed.
                Parameters includeResource = subBuilder.getIncludeResource();
                for (String name : includeResource.keySet()) {
                    if (name.startsWith("{") && name.endsWith("}")) {
                        name = name.substring(1, name.length() - 1).trim();
                    }

                    String[] parts = INCLUDE_RESOURCE_SOURCE_DEST_SPLIT.split(name);
                    String source = parts[0];
                    if (parts.length == 2)
                        source = parts[1];

                    if (source.startsWith("-")) {
                        source = source.substring(1);
                    }
                    File file = builder.getFile(source);
                    rootDescriptors.add(new BuildRootDescriptorImpl(this, file));
                }
            }

            File root = JpsJavaExtensionService.getInstance().getOutputDirectory(myModule, false);
            if (root != null) {
                rootDescriptors.add(new BuildRootDescriptorImpl(this, root, false));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to compute root descriptors for module " + getModule().getName(), e);
            throw new RuntimeException(e);
        }

        return rootDescriptors;
    }

    @Nullable
    @Override
    public BuildRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
        return ContainerUtil.find(rootIndex.getTargetRoots(this, null), descriptor -> descriptor.getRootId().equals(rootId));
    }

    @NotNull
    @Override
    public String getPresentableName() {
        return "OSGi in module '" + getModule().getName() + "'";
    }

    @NotNull
    @Override
    public Collection<File> getOutputRoots(CompileContext context) {
        try {
            return ContainerUtil.createMaybeSingletonList(bndWorkspace.getProject(getModule().getName()).getTarget());
        } catch (Exception e) {
            LOGGER.error("Failed to get output roots for: " + getModule().getName(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isTests() {
        return false;
    }

}