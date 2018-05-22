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
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.roots.ProjectFileIndex
import org.amdatu.idea.AmdatuIdeaPlugin

class ExportExecutableJar : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val project = e.project ?: return
        val workspace = project.getComponent(AmdatuIdeaPlugin::class.java)?.workspace ?: return

        val moduleForFile = ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile) ?: return

        val bndProject = workspace.getProject(moduleForFile.name)

        val targetFile = FileChooserFactory.getInstance()
                .createSaveFileDialog(FileSaverDescriptor("Export executable jar", "todo"), project)
                .save(null, virtualFile.nameWithoutExtension + ".jar") ?: return

        bndProject.export(virtualFile.path, true, targetFile.file)



    }
}