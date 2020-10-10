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

package org.amdatu.idea.actions.index

import aQute.bnd.osgi.repository.SimpleIndexer
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import org.amdatu.idea.AmdatuIdeaPlugin
import org.amdatu.idea.actions.AmdatuIdeaAction
import java.io.File


class GenerateIndexAction : AmdatuIdeaAction() {

    override fun update(e: AnActionEvent) {
        super.update(e)

        val isDir = e.getData(CommonDataKeys.VIRTUAL_FILE)?.isDirectory ?: false
        e.presentation.isEnabled = isDir
    }

    override fun actionPerformed(e: AnActionEvent) {
        e.project?.apply {
            val dir = File(e.getData(CommonDataKeys.VIRTUAL_FILE)!!.path) // Always there as otherwise the action is disabled
            val dialog = GenerateIndexDialog(this, dir)
            if (dialog.showAndGet()) {
                generateIndex(this, dialog.resources(), dialog.indexFileName(), dialog.compressed())
            }
        }
    }

    private fun generateIndex(project: Project, toIndex: Set<File>, indexFile: File, compressed: Boolean) =
            object : Backgroundable(project, "Generate index", true) {
                override fun run(progressIndicator: ProgressIndicator) {
                    SimpleIndexer()
                            .files(toIndex)
                            .base(indexFile.parentFile.toURI())
                            .compress(compressed)
                            .index(indexFile)

                    project.getComponent(AmdatuIdeaPlugin::class.java)
                            .info("Generated repository index: $indexFile")
                    LocalFileSystem.getInstance().refreshIoFiles(listOf(indexFile.parentFile), false, true, null)
                }
            }.queue()

}
