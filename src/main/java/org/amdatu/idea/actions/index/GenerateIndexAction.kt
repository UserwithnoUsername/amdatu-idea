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

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import org.amdatu.idea.AmdatuIdeaPlugin
import org.osgi.service.indexer.ResourceIndexer
import org.osgi.service.indexer.impl.RepoIndex
import java.io.File
import java.io.FileOutputStream


class GenerateIndexAction : AnAction() {

    override fun update(e: AnActionEvent) {
        val isDir = e.getData(CommonDataKeys.VIRTUAL_FILE)?.isDirectory ?: false

        e.presentation.isEnabled = isDir
//        e.presentation.isVisible = isDir
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

    //                val resourceIndexer = workspace.getPlugin(ResourceIndexer::class.java)
                    val config = HashMap<String, String>()
                    config[ResourceIndexer.PRETTY] = (!compressed).toString()
                    config[ResourceIndexer.COMPRESSED] = compressed.toString()
                    config[ResourceIndexer.ROOT_URL] = indexFile.parentFile.toURI().toASCIIString()

                    val resourceIndexer = RepoIndex()
                    resourceIndexer.index(toIndex, FileOutputStream(indexFile), config)
                    amdatuIdePlugin.notificationService.info("Generated repository index: " + indexFile.toString())
                }
            }.queue()

}