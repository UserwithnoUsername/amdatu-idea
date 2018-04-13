package org.amdatu.idea.actions.index

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.io.File
import java.nio.file.FileSystems
import javax.swing.JComponent

class GenerateIndexDialog(project: Project?, private var dir: File) : DialogWrapper(project) {

    val form = GenerateIndexForm(dir)

    init {
        init()
        title = "Generate index"
    }


    fun resources(): Set<File> {
        return form.resources.toSet()
    }
    fun indexFileName(): File {
        return File(dir, form.indexFileName)
    }

    fun compressed() : Boolean {
        return form.isCompressed
    }

    override fun createCenterPanel(): JComponent? {
        return form.apply {
            updateFileList(toIndex(baseDir, resourcePatten))
        }.rootPane

    }

    private fun toIndex(baseDir: String, resourcePattern: String): List<File> {
        if (!File(baseDir).isDirectory) {
            return emptyList()
        }

        val pathMatcher = FileSystems.getDefault().getPathMatcher("glob:$resourcePattern")
        return File(baseDir).walk()
                .filter { pathMatcher.matches(it.toPath()) }
                .toList()
    }

}
