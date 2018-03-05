package org.amdatu.ide.jps;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetRegistry;
import org.jetbrains.jps.builders.ModuleBasedTarget;
import org.jetbrains.jps.builders.TargetOutputIndex;
import org.jetbrains.jps.builders.impl.BuildRootDescriptorImpl;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;

import aQute.bnd.build.Project;
import aQute.bnd.build.Workspace;
import aQute.bnd.osgi.Constants;
import aQute.bnd.osgi.Instruction;

public class AmdatuIdeModuleBasedBuildTarget extends ModuleBasedTarget<BuildRootDescriptor> {

    private static final Logger LOGGER = Logger.getInstance(AmdatuIdeModuleBasedBuildTarget.class);

    private final Workspace bndWorkspace;

    public AmdatuIdeModuleBasedBuildTarget(Workspace bndWorkspace, JpsModule module) {
        super(AmdatuIdeModuleBasedTargetType.INSTANCE, module);
        this.bndWorkspace = bndWorkspace;
    }

    public Workspace getBndWorkspace() {
        return bndWorkspace;
    }

    @Override
    public String getId() {
        return getModule().getName();
    }

    @Override
    public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry, TargetOutputIndex outputIndex) {
        BuildTargetRegistry.ModuleTargetSelector selector = BuildTargetRegistry.ModuleTargetSelector.PRODUCTION;
        return Collections.unmodifiableCollection(targetRegistry.getModuleBasedTargets(getModule(), selector));
    }

    @NotNull
    @Override
    public List<BuildRootDescriptor> computeRootDescriptors(JpsModel model, ModuleExcludeIndex index,
                                                            IgnoredFileIndex ignoredFileIndex, BuildDataPaths dataPaths) {
        List<BuildRootDescriptor> rootDescriptors = ContainerUtil.newArrayList();

        try {
            Project project = bndWorkspace.getProject(getModule().getName());

            rootDescriptors.add(new BuildRootDescriptorImpl(this, project.getPropertiesFile()));

            String sub = project.get(Constants.SUB);
            if (sub != null) {
                List<File> files = new ArrayList<>();
                tree(files, project.getPropertiesFile().getParentFile(), new Instruction(sub));
                files.forEach(file -> rootDescriptors.add(new BuildRootDescriptorImpl(this, file)));

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

    void tree(List<File> list, File current, Instruction instr) {

        String subs[] = current.list();
        if (subs != null) {
            for (String sub : subs) {
                File f = new File(current, sub);
                if (f.isFile()) {
                    if (instr.matches(sub) && !instr.isNegated())
                        list.add(f);
                } else
                    tree(list, f, instr);
            }
        }
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