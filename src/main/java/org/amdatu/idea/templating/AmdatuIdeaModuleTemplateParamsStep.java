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

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.project.Project;
import kotlin.Unit;
import org.amdatu.idea.ui.metatype.MetaTypeEditPanelFactory;
import org.bndtools.templating.Template;
import org.osgi.service.metatype.AttributeDefinition;
import org.osgi.service.metatype.ObjectClassDefinition;

import javax.swing.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.amdatu.idea.templating.AmdatuIdeaModuleBuilder.KEY_ATTRS;
import static org.amdatu.idea.templating.AmdatuIdeaModuleBuilder.KEY_TEMPLATE;

class AmdatuIdeaModuleTemplateParamsStep extends ModuleWizardStep {

    private static final List<String> DEFAULT_CONTEXT_ATTRS = Arrays.asList("basePackageDir", "basePackageName", "srcDir", "testSrcDir");

    private WizardContext myContext;
    private final Map<String, List<Object>> map = new HashMap<>();

    AmdatuIdeaModuleTemplateParamsStep(WizardContext context) {
        myContext = context;
    }

    @Override
    public JComponent getComponent() {
        Template template = getTemplate();

        try {
            if (template == null) {
                return new JLabel("No template selected");
            } else if (template.getMetadata() == null) {
                return new JLabel("Template has no metadata");
            }

            ObjectClassDefinition metadata = template.getMetadata();
            Project project = myContext.getProject();
            if (project == null) {
                return new JLabel("No project available");
            }

            return new MetaTypeEditPanelFactory(project).create(metadata, (key, value) -> {
                if (value != null) {
                    //noinspection unchecked
                    map.put(key, (List<Object>) value);
                } else {
                    map.remove(key);
                }
                return Unit.INSTANCE;
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Template getTemplate() {
        return myContext.getUserData(KEY_TEMPLATE);
    }

    @Override
    public boolean isStepVisible() {
        try {
            Template template = getTemplate();
            return template != null
                            && template.getMetadata() != null
                            && template.getMetadata().getAttributeDefinitions(-1) != null
                            && Arrays.stream(template.getMetadata().getAttributeDefinitions(-1))
                                .map(AttributeDefinition::getID)
                                .anyMatch(ad -> !DEFAULT_CONTEXT_ATTRS.contains(ad));
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateDataModel() {
        myContext.putUserData(KEY_ATTRS, map);
    }

}
