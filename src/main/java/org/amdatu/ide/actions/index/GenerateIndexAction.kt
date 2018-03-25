package org.amdatu.ide.actions.index

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import org.amdatu.ide.AmdatuIdePlugin
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
                    val amdatuIdePlugin = project.getComponent(AmdatuIdePlugin::class.java)
                    val workspace = amdatuIdePlugin?.workspace

                    if (workspace == null) {
                        amdatuIdePlugin.error("Failed to generate repostiory index, bnd workspace not available")
                        return
                    }

    //                val resourceIndexer = workspace.getPlugin(ResourceIndexer::class.java)
                    val config = HashMap<String, String>()
                    config[ResourceIndexer.PRETTY] = (!compressed).toString()
                    config[ResourceIndexer.COMPRESSED] = compressed.toString()
                    config[ResourceIndexer.ROOT_URL] = indexFile.parentFile.toURI().toASCIIString()

                    val resourceIndexer = RepoIndex()
                    resourceIndexer.index(toIndex, FileOutputStream(indexFile), config)
                    amdatuIdePlugin.info("Generated repository index: " + indexFile.toString())
                }
            }.queue()

}