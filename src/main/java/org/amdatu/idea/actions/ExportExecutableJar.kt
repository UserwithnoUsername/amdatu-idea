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

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import org.amdatu.idea.AmdatuIdeaPlugin

class ExportExecutableJar : AmdatuIdeaAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val project = e.project ?: return
        project.getComponent(AmdatuIdeaPlugin::class.java)?.withWorkspace { workspace ->
            val moduleForFile = ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile) ?: return@withWorkspace

            val bndProject = workspace.getProject(moduleForFile.name)

            val targetFile = FileChooserFactory.getInstance()
                    .createSaveFileDialog(FileSaverDescriptor("Export executable jar", "todo"), project)
                    .save(null as VirtualFile?, virtualFile.nameWithoutExtension + ".jar") ?: return@withWorkspace

            bndProject.export(virtualFile.path, true, targetFile.file)
        }
    }
}
