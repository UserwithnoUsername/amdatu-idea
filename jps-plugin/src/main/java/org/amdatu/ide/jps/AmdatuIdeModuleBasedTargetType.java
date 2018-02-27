package org.amdatu.ide.jps;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.ModuleBasedBuildTargetType;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;

import com.intellij.openapi.diagnostic.Logger;

import aQute.bnd.build.Project;
import aQute.bnd.build.Workspace;
import aQute.bnd.osgi.Constants;

public class AmdatuIdeModuleBasedTargetType extends ModuleBasedBuildTargetType<AmdatuIdeModuleBasedBuildTarget> {

    private static final Logger LOGGER = Logger.getInstance(AmdatuIdeModuleBasedBuildTarget.class);

    public static final AmdatuIdeModuleBasedTargetType INSTANCE = new AmdatuIdeModuleBasedTargetType();

    private AmdatuIdeModuleBasedTargetType() {
        super("AmdatuIde");
    }

    private Map<String, AmdatuIdeModuleBasedBuildTarget> targets = new HashMap<>();

    @NotNull
    @Override
    public List<AmdatuIdeModuleBasedBuildTarget> computeAllTargets(@NotNull JpsModel model) {
        synchronized (targets) {
            targets.clear();
            File baseDirectory = JpsModelSerializationDataService.getBaseDirectory(model.getProject());
            try {
                Workspace bndWorkspace = new Workspace(baseDirectory);

                for (JpsModule module : model.getProject().getModules()) {
                    Project bndProject = bndWorkspace.getProject(module.getName());

                    if (bndProject != null && shouldBuild(bndProject)) {
                        targets.put(module.getName(), new AmdatuIdeModuleBasedBuildTarget(bndWorkspace, module));
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Compute all targets failed for project: " + model.getProject().getName(), e);
                throw new RuntimeException(e);
            }
            return new ArrayList<>(targets.values());
        }
    }

    private boolean shouldBuild(Project bndProject) {
        Boolean noBundles = Boolean.valueOf(bndProject.get(Constants.NOBUNDLES, "false"));
        return !noBundles;
    }

    @NotNull
    @Override
    public BuildTargetLoader<AmdatuIdeModuleBasedBuildTarget> createLoader(@NotNull JpsModel model) {
        return new BuildTargetLoader<AmdatuIdeModuleBasedBuildTarget>() {
            @Nullable
            @Override
            public AmdatuIdeModuleBasedBuildTarget createTarget(@NotNull String targetId) {
                synchronized (targets) {
                    return targets.get(targetId);
                }
            }
        };
    }

}
