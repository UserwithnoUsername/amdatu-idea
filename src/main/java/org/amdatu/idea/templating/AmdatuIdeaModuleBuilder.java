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
package org.amdatu.idea.templating;

import aQute.bnd.build.Workspace;
import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Key;
import org.amdatu.idea.AmdatuIdeaPlugin;
import org.amdatu.idea.imp.BndProjectImporter;
import org.bndtools.templating.Template;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AmdatuIdeaModuleBuilder extends JavaModuleBuilder {

    static final Key<Template> KEY_TEMPLATE = Key.create("template");
    static final Key<Map<String, List<Object>>> KEY_ATTRS = Key.create("attrs");

    private WizardContext myWizardContext;

    AmdatuIdeaModuleBuilder() {
        addModuleConfigurationUpdater(new ModuleConfigurationUpdater() {
            @Override
            public void update(@NotNull Module module, @NotNull ModifiableRootModel rootModel) {
                module.setModuleType(StdModuleTypes.JAVA.getId());
            }
        });
    }

    AmdatuIdeaModuleBuilder(WizardContext wizardContext) {
        this();
        myWizardContext = wizardContext;
    }

    @Override
    public ModuleType getModuleType() {
        return AmdatuIdeaModuleType.getInstance();
    }

    @Nullable
    @Override
    public ModuleWizardStep getCustomOptionsStep(WizardContext context, Disposable parentDisposable) {
        return new AmdatuIdeaModuleSelectTemplateWizardStep(context, this);
    }

    @Override
    public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext,
                    @NotNull ModulesProvider modulesProvider) {
        return new ModuleWizardStep[] { new AmdatuIdeaModuleTemplateParamsStep(wizardContext) };
    }

    @Nullable
    @Override
    public Module commitModule(@NotNull Project project, @Nullable ModifiableModuleModel model) {
        Module module = super.commitModule(project, model);

        if (module == null) {
            return null;
        }


        Template myTemplate = myWizardContext.getUserData(KEY_TEMPLATE);
        if (myTemplate != null) {
            if (myWizardContext.isCreatingNewProject()) {
                TemplateHelperKt.applyWorkspaceTemplate(module.getProject(), myTemplate);
            } else {
                Map<String, List<Object>> map = new HashMap<>();
                Map<String, List<Object>> userData = myWizardContext.getUserData(KEY_ATTRS);
                if (userData != null) {
                    map.putAll(userData);
                }
                TemplateHelperKt.applyModuleTemplate(module, myTemplate, map);
            }
        }

        if (myWizardContext.isCreatingNewProject()) {
            String rootDir = project.getBasePath();
            assert rootDir != null : project;
            ModuleRootModificationUtil.addContentRoot(module, rootDir);
            ModuleRootModificationUtil.setSdkInherited(module);
        }
        else {
            Workspace workspace = project.getComponent(AmdatuIdeaPlugin.class).getWorkspace();
            workspace.refresh();
            try {
                aQute.bnd.build.Project bndProject = workspace.getProject(module.getName());
                new BndProjectImporter(project, Collections.singletonList(bndProject)).resolve(true);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        return module;
    }

}
