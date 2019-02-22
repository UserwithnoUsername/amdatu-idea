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

import aQute.bnd.build.Run
import aQute.bnd.osgi.Jar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.Row
import com.intellij.ui.layout.panel
import com.intellij.ui.wizard.WizardDialog
import com.intellij.ui.wizard.WizardModel
import com.intellij.ui.wizard.WizardNavigationState
import com.intellij.ui.wizard.WizardStep
import org.amdatu.idea.AmdatuIdeaConstants
import org.amdatu.idea.AmdatuIdeaPlugin
import org.amdatu.idea.templating.FelixOCDAdapter
import org.amdatu.idea.ui.metatype.MetaTypeEditPanelFactory
import org.apache.felix.metatype.Designate
import org.apache.felix.metatype.MetaData
import org.apache.felix.metatype.MetaDataReader
import org.apache.felix.metatype.OCD
import java.awt.Dimension
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.event.DocumentEvent

val LOG = Logger.getInstance(CreateConfigurationAction::class.java)

class CreateConfigurationAction : AmdatuIdeaAction() {

    override fun update(e: AnActionEvent) {
        super.update(e)

        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)

        val isBndrun = virtualFile?.name?.endsWith(AmdatuIdeaConstants.BND_RUN_EXT) ?: false
        e.presentation.isEnabled = isBndrun
    }

    override fun actionPerformed(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val project = e.project ?: return
        val amdatuIdeaPlugin = project.getComponent(AmdatuIdeaPlugin::class.java) ?: return


        val launcher = amdatuIdeaPlugin.withWorkspace { workspace -> Run.createRun(workspace, File(virtualFile.path)).projectLauncher }

        val metaDataList = launcher.project.runbundles
                .filter { runBundle -> runBundle.file.name.endsWith(".jar") }
                .flatMap { runBundle ->


                    val jar = Jar(runBundle.file)
                    jar.resources
                            .filter { (path, _) ->
                                path.startsWith("OSGI-INF/metatype")
                                        && path.endsWith(".xml")
                            }
                            .mapNotNull { (path, resource) ->
                                try {
                                    resource.openInputStream().use { stream ->
                                        MetaDataReader().parse(stream)
                                    }
                                } catch (e: IOException) {
                                    LOG.warn("Failed to read metattype resource '$path' from Bundle: '${jar.bsn}' version '${jar.version}'")
                                    null
                                }
                            }
                }

        if (metaDataList.isEmpty()) {
            return
        }

        metaDataList.forEach {
            println("Found: ${it.designates}")
        }

        val wizardModel = CreateConfigurationTemplateModel(project, metaDataList)
        if (object : WizardDialog<CreateConfigurationTemplateModel>(project, false, wizardModel) {
                    override fun getWindowPreferredSize(): Dimension {
                        return Dimension(400, 300)
                    }
                }.showAndGet()) {
            val properties = Properties()
            wizardModel.templateParamsMap!!.forEach { (key, value) ->
                properties[key] = value.firstOrNull().toString()
                println("$key - $value")
            }
            val confDir = File("${virtualFile.parent.path}/conf")
            if (!confDir.exists()) {
                if (!confDir.mkdir()) {
                    LOG.error("Failed to create conf dir '${confDir.absolutePath}'")
                }
            }

            val fileName = if (wizardModel.designate?.pid != null) {
                "${wizardModel.designate!!.pid}.cfg"
            } else {
                "${wizardModel.designate!!.factoryPid}-${wizardModel.subName}.cfg"
            }

            val file = File(confDir, fileName)
            FileOutputStream(file).use {
                properties.store(it, null)
            }
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
        }

    }
}

class CreateConfigurationTemplateModel(val project: Project, private val myMetaDataList: List<MetaData>) : WizardModel("Create configuration") {

    val metaDataList: List<MetaData>
        get() = myMetaDataList
    var designate: Designate? = null
    var subName: String? = null
    var templateParamsMap: MutableMap<String, List<Any>>? = null


    init {
        add(SelectStep(this))
        add(EditStep(this))
    }
}


class SelectStep(private val myModel: CreateConfigurationTemplateModel) : WizardStep<CreateConfigurationTemplateModel>("Select") {

    override fun prepare(state: WizardNavigationState?): JComponent {

        val designateModel = DefaultComboBoxModel<Designate>()
        myModel.metaDataList.flatMap { it.designates }
                .forEach { designateModel.addElement(it as Designate) }

        val designateComboBox = ComboBox<Designate>(designateModel)
        designateComboBox.renderer = object : ColoredListCellRenderer<Designate>() {
            override fun customizeCellRenderer(list: JList<out Designate>, value: Designate?, index: Int, selected: Boolean, hasFocus: Boolean) {

                val label = when {
                    value?.pid != null -> value.pid
                    value?.factoryPid != null -> "${value.factoryPid} (factory)"
                    else -> "Oops, something went wrong"
                }
                append(label)
            }
        }


        val model = DefaultComboBoxModel<MetaData>()
        myModel.metaDataList.forEach(model::addElement)


        val textField = JBTextField()
        textField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun changedUpdate(e: DocumentEvent?) {
                myModel.subName = textField.text
            }

            override fun insertUpdate(e: DocumentEvent?) {
                myModel.subName = textField.text
            }

            override fun removeUpdate(e: DocumentEvent?) {
                myModel.subName = textField.text
            }
        })

        var a: Row? = null


        val panel = panel {
            row("select") { designateComboBox() }
            a = row("subName") { textField() }
        }

        designateComboBox.addActionListener {
            val designate = designateComboBox.selectedItem as Designate
            myModel.designate = designate
            myModel.subName = null

            a?.visible = designate.factoryPid != null
            panel.updateUI()
        }
        designateComboBox.selectedIndex = 0

        return panel
    }
}

class EditStep(private val myModel: CreateConfigurationTemplateModel) : WizardStep<CreateConfigurationTemplateModel>("Edit") {
    override fun prepare(state: WizardNavigationState?): JComponent {

        val ocd = myModel.metaDataList.firstOrNull { it.objectClassDefinitions.containsKey(myModel.designate?.`object`?.ocdRef) }
                ?.objectClassDefinitions?.get(myModel.designate?.`object`?.ocdRef) as OCD

        val ocdAdapter = FelixOCDAdapter(ocd)
        myModel.templateParamsMap = HashMap()
        return MetaTypeEditPanelFactory(myModel.project).create(ocdAdapter) { id: String, value: List<Any> ->
            if (value.isEmpty()) {
                myModel.templateParamsMap!!.remove(id)
            } else {
                myModel.templateParamsMap!![id] = value
            }
        }
    }

}