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

import aQute.bnd.build.Project;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.ProjectImportBuilder;
import icons.OsmorcIdeaIcons;
import org.amdatu.idea.AmdatuIdeaPlugin;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public class BndProjectImportBuilder extends ProjectImportBuilder<Project> {
    private boolean myOpenProjectSettings = false;

    @NotNull
    @Override
    public String getName() {
        return "Bnd/Bndtools";
    }

    @Override
    public Icon getIcon() {
        return OsmorcIdeaIcons.Bnd;
    }


    @Override
    public List<Project> getList() {
        return Collections.emptyList();
    }

    @Override
    public void setList(List<Project> list) {

    }

    @Override
    public boolean isMarked(Project project) {
        return false;
    }

    @Override
    public boolean isOpenProjectSettingsAfter() {
        return myOpenProjectSettings;
    }

    @Override
    public void setOpenProjectSettingsAfter(boolean openProjectSettings) {
        myOpenProjectSettings = openProjectSettings;
    }

    @NotNull
    @Override
    public List<Module> commit(com.intellij.openapi.project.Project project,
                    ModifiableModuleModel model,
                    ModulesProvider modulesProvider,
                    ModifiableArtifactModel artifactModel) {
        if (model == null) {
            model = ModuleManager.getInstance(project).getModifiableModel();
            try {
                List<Module> result = commit(project, model, modulesProvider, artifactModel);
                WriteAction.run(model::commit);
                return result;
            }
            catch (RuntimeException | Error e) {
                WriteAction.run(model::dispose);
                throw e;
            }
        }

        final BndProjectImporter importer = new BndProjectImporter(project, Collections.emptyList());
        Module rootModule = importer.createRootModule(model);

        AmdatuIdeaPlugin amdatuIdeaPlugin = project.getService(AmdatuIdeaPlugin.class);

        amdatuIdeaPlugin.initialize();
        amdatuIdeaPlugin.withWorkspace(workspace -> {
            importer.setupProject(workspace);
            return null;
        });

        return Collections.singletonList(rootModule);
    }

    @Override
    public void cleanup() {
        super.cleanup();
    }
}