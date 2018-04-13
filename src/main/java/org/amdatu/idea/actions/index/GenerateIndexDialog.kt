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
