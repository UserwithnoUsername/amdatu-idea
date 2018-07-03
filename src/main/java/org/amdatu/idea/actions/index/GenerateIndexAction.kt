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
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import org.amdatu.idea.AmdatuIdeaPlugin
import java.io.File


class GenerateIndexAction : AnAction() {

    override fun update(e: AnActionEvent) {
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
                    val amdatuIdePlugin = project.getComponent(AmdatuIdeaPlugin::class.java)
                    val workspace = amdatuIdePlugin?.workspace

                    if (workspace == null) {
                        amdatuIdePlugin.notificationService.error("Failed to generate repostiory index, bnd workspace not available")
                        return
                    }

                    SimpleIndexer()
                            .files(toIndex)
                            .base(indexFile.parentFile.toURI())
                            .compress(compressed)
                            .index(indexFile)

                    amdatuIdePlugin.notificationService.info("Generated repository index: " + indexFile.toString())
                    LocalFileSystem.getInstance().refreshIoFiles(listOf(indexFile.parentFile), false, true, null)
                }
            }.queue()

}