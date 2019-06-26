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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
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
import aQute.bnd.header.Attrs;
import aQute.bnd.osgi.Constants;

public class AmdatuIdeaModuleBasedTargetType extends ModuleBasedBuildTargetType<AmdatuIdeaModuleBasedBuildTarget> {

    private static final Logger LOGGER = Logger.getInstance(AmdatuIdeaModuleBasedBuildTarget.class);

    public static final AmdatuIdeaModuleBasedTargetType INSTANCE = new AmdatuIdeaModuleBasedTargetType();

    private AmdatuIdeaModuleBasedTargetType() {
        super("AmdatuIdea");
    }

    private final Map<String, AmdatuIdeaModuleBasedBuildTarget> targets = new HashMap<>();

    static {
        Workspace.setDriver(Constants.BNDDRIVER_INTELLIJ);
        Workspace.addGestalt(Constants.GESTALT_INTERACTIVE, new Attrs());
        // Add offline gestalt this wil prevent OSGiRepositories starting polling process and
        // force using cached bundles instead of re-downloading them.
        Workspace.addGestalt(Constants.GESTALT_OFFLINE, new Attrs());
    }

    @NotNull
    @Override
    public List<AmdatuIdeaModuleBasedBuildTarget> computeAllTargets(@NotNull JpsModel model) {
        File baseDirectory = JpsModelSerializationDataService.getBaseDirectory(model.getProject());
        if (!new File(baseDirectory, "cnf/build.bnd").exists()) {
            return Collections.emptyList();
        }

        synchronized (targets) {
            targets.clear();
            try {
                Workspace bndWorkspace = new Workspace(baseDirectory);

                for (JpsModule module : model.getProject().getModules()) {
                    Project bndProject = bndWorkspace.getProject(module.getName());

                    if (bndProject != null && shouldBuild(bndProject)) {
                        targets.put(module.getName(), new AmdatuIdeaModuleBasedBuildTarget(bndWorkspace, module));
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
    public BuildTargetLoader<AmdatuIdeaModuleBasedBuildTarget> createLoader(@NotNull JpsModel model) {
        return new BuildTargetLoader<AmdatuIdeaModuleBasedBuildTarget>() {
            @Nullable
            @Override
            public AmdatuIdeaModuleBasedBuildTarget createTarget(@NotNull String targetId) {
                synchronized (targets) {
                    return targets.get(targetId);
                }
            }
        };
    }

}
