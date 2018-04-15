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

import com.intellij.ide.util.projectWizard.SdkSettingsStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import kotlin.Unit;
import org.amdatu.idea.ui.template.TemplateSelectionPanelFactory;
import org.bndtools.templating.Template;

import javax.swing.*;
import java.util.List;

public class AmdatuIdeaModuleSelectTemplateWizardStep extends SdkSettingsStep {

    private WizardContext myWizardContext;
    private Template mySelectedTemplate;
    private final List<Template> myTemplates;

    AmdatuIdeaModuleSelectTemplateWizardStep(WizardContext context, AmdatuIdeaModuleBuilder moduleBuilder) {
        super(context, moduleBuilder, moduleBuilder::isSuitableSdkType, null);
        myWizardContext = context;
        RepoTemplateLoader templateLoader = new RepoTemplateLoader();

        String templateType;
        if (context.isCreatingNewProject()) {
            templateType = "workspace";
        }
        else {
            templateType = "project";
        }

        myTemplates = templateLoader.findTemplates(context.getProject(), templateType);

    }

    @Override
    public JComponent getComponent() {
        return new TemplateSelectionPanelFactory().create(myTemplates, template -> {
            mySelectedTemplate = template;
            return Unit.INSTANCE;
        });
    }

    @Override
    public void _init() {

    }

    @Override
    public void _commit(boolean finishChosen) {

    }

    @Override
    public Icon getIcon() {
        return null;
    }

    @Override
    public void updateDataModel() {
        super.updateDataModel();
        myWizardContext.putUserData(AmdatuIdeaModuleBuilder.KEY_TEMPLATE, mySelectedTemplate);
        myWizardContext.setProjectBuilder(new AmdatuIdeaModuleBuilder(myWizardContext));
    }
}
