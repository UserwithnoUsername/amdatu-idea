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

package org.amdatu.idea.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.ui.layout.LCFlags
import com.intellij.ui.layout.panel
import com.intellij.ui.wizard.WizardDialog
import com.intellij.ui.wizard.WizardModel
import com.intellij.ui.wizard.WizardNavigationState
import com.intellij.ui.wizard.WizardStep
import org.amdatu.idea.templating.RepoTemplateLoader
import org.amdatu.idea.templating.applyWorkspaceTemplate
import org.amdatu.idea.ui.metatype.MetaTypeEditPanelFactory
import org.amdatu.idea.ui.template.TemplateSelectionPanelFactory
import org.bndtools.templating.Template
import javax.swing.JComponent
import javax.swing.JPanel

class UpdateConfigurationProjectAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {

        val project = e.project ?: return

        val templateLoader = RepoTemplateLoader()
        val templates = templateLoader.findTemplates(project, "workspace")

        val wizardModel = TemplateWizardModel(project, templates)
        val wizardDialog = WizardDialog<TemplateWizardModel>(project, false, wizardModel)
        if (wizardDialog.showAndGet()) {
            val template = wizardModel.template ?: return
            applyWorkspaceTemplate(project, template)
        }
    }
}

class TemplateWizardModel(private val myProject: Project, templates: List<Template>) : WizardModel("template") {

    private var myTemplate: Template? = null
    private var myTemplateParamsMap = HashMap<String, List<Any>>()

    val project: Project
        get() = myProject

    val templateParamsMap: MutableMap<String, List<Any>>
        get() = myTemplateParamsMap

    var template: Template?
        get() = myTemplate
        set(value) {
            myTemplate = value
        }

    init {
        add(SelectTemplateStep(this, templates))
        add(TemplateParamsStep(this))
    }
}

class SelectTemplateStep(
        private var myWizardModel: TemplateWizardModel,
        private var myTemplates: List<Template>
) : WizardStep<TemplateWizardModel>() {

    private var myPanel: JComponent? = null

    override fun prepare(state: WizardNavigationState): JComponent {
        updateState(state)

        var panel = myPanel
        if (panel === null) {
            panel = TemplateSelectionPanelFactory().create(myTemplates, { selectedTemplate ->
                myWizardModel.template = selectedTemplate
                updateState(state)
            })
            myPanel = panel
        }

        return panel
    }

    private fun updateState(state: WizardNavigationState) {

        val template = myWizardModel.template
        if (template == null) {
            state.NEXT.isEnabled = false
            state.FINISH.isEnabled = false
        } else {
            val hasParams = template.metadata != null
                    && template.metadata.getAttributeDefinitions(-1) != null
                    && template.metadata.getAttributeDefinitions(-1).isNotEmpty()

            state.NEXT.isEnabled = hasParams
            state.FINISH.isEnabled = !hasParams
        }

    }

}

class TemplateParamsStep(private var model: TemplateWizardModel) : WizardStep<TemplateWizardModel>() {
    private var container: JPanel = panel(LCFlags.fillX) {}

    override fun prepare(state: WizardNavigationState?): JComponent {

        // Could be the second time we got here ( prev + next in wizard ) make sure old content is gone
        for (component in container.components) {
            container.remove(component)
        }

        val template = model.template ?: throw IllegalStateException("Template should not be null")

        container.add(
                MetaTypeEditPanelFactory(model.project).create(template.metadata, { id: String, value: List<Any> ->
                    if (value.isEmpty()) {
                        model.templateParamsMap.remove(id)
                    } else {
                        model.templateParamsMap[id] = value
                    }
                })
        )
        container.revalidate()
        container.repaint()
        return container
    }

}

