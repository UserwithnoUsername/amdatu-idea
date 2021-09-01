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

package org.amdatu.idea

import com.intellij.openapi.compiler.CompilationStatusListener
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.compiler.CompilerTopics
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.util.regex.Pattern

sealed class BaseliningSuggestion
data class BaseliningBundleSuggestion(val source: VirtualFile, val currentVersion: String, val suggestedVersion: String) : BaseliningSuggestion()
data class BaseliningPackageSuggestion(val source: VirtualFile, val suggestedVersion: String) : BaseliningSuggestion()


interface BaseliningErrorService {
    fun getAllSuggestions() : List<BaseliningSuggestion>
    fun getBundleSuggestion(file: VirtualFile): BaseliningBundleSuggestion?
    fun getPackageSuggestion(file: VirtualFile): BaseliningPackageSuggestion?
}

class BaseliningErrorServiceImpl(val project: Project) : BaseliningErrorService, CompilationStatusListener {

    private val bundleSuggestions: MutableMap<VirtualFile, BaseliningBundleSuggestion> = HashMap()
    private val packageSuggestions: MutableMap<VirtualFile, BaseliningPackageSuggestion> = HashMap()

    init {
        val messageBusConnection = project.messageBus.connect()
        messageBusConnection.subscribe(CompilerTopics.COMPILATION_STATUS, this)
    }

    override fun getBundleSuggestion(file: VirtualFile): BaseliningBundleSuggestion? {
        return bundleSuggestions[file]
    }

    override fun getPackageSuggestion(file: VirtualFile): BaseliningPackageSuggestion? {
        return packageSuggestions[file]
    }

    override fun getAllSuggestions(): List<BaseliningSuggestion> {
        return bundleSuggestions.map { it.value }
                .plus(packageSuggestions.map { it.value } )
    }

    override fun compilationFinished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext) {
        super.compilationFinished(aborted, errors, warnings, compileContext)


        val allCompilerMessages = compileContext.getMessages(CompilerMessageCategory.INFORMATION) +
                compileContext.getMessages(CompilerMessageCategory.WARNING) +
                compileContext.getMessages(CompilerMessageCategory.ERROR)

        bundleSuggestions.clear()

        allCompilerMessages
                .filter { it.message.contains("The bundle version ") }
                .forEach { warning ->
                    parseBundleVersionSuggestion(warning.virtualFile, warning.message).let { suggestion ->
                        bundleSuggestions[suggestion.source] = suggestion
                    }
                }

        packageSuggestions.clear()

        allCompilerMessages
                .filter { it.message.contains("Baseline mismatch for package ") }
                .forEach { warning ->
                    parsePackageVersionSuggestion(warning.virtualFile, warning.message)?.let { suggestion ->
                        packageSuggestions[suggestion.source] = suggestion
                      }
                }

    }

    private fun parseBundleVersionSuggestion(source: VirtualFile, warning: String): BaseliningBundleSuggestion {
        val message = warning.lines().first()

        val suggestionMarker = "must be at least "
        val suggestedVersion = message.substring(message.indexOf(suggestionMarker) + suggestionMarker.length)
        val currentMarker = "The bundle version ("
        val currentMarkerIndex = message.indexOf(currentMarker) + currentMarker.length
        val currentVersion = message.substring(currentMarkerIndex, currentMarkerIndex + 5)

        return BaseliningBundleSuggestion(source, currentVersion, suggestedVersion)
    }

    private fun parsePackageVersionSuggestion(source: VirtualFile, warning: String): BaseliningPackageSuggestion? {
        val message = warning.lines().first()
        val patternString = ".*package ([.0-9a-zA-Z]*),.*suggest (.*) or.*"
        val pattern = Pattern.compile(patternString, Pattern.DOTALL)
        val matcher = pattern.matcher(message)
        val matches = matcher.matches()

        return if (matches) {
            val pkg = matcher.group(1)
            val virtualFile = fixSourceFile(source, pkg)
            val version = matcher.group(2)
            BaseliningPackageSuggestion(virtualFile, version)
        } else {
            null
        }
    }

    /**
     * Workaround for bnd reporting the wrong file as the source for the baselining issue.
     *
     * At least present in bnd 4.1 and older
     * https://github.com/bndtools/bnd/issues/2877
     */
    private fun fixSourceFile(virtualFile: VirtualFile, pkg: String): VirtualFile {
        if (virtualFile.name.endsWith(".bnd")) {
            val bndProject: aQute.bnd.build.Project= project.service<AmdatuIdeaPlugin>()
                    .withWorkspace { workspace ->  workspace.getProjectFromFile(File(virtualFile.path).parentFile) }

            for (sourceDir in bndProject.sourcePath) {
                val packageDir = File(sourceDir, pkg.replace(".", "/"))
                val packageInfoJavaFile = File(packageDir, "package-info.java")
                if (packageInfoJavaFile.exists()) {
                    return LocalFileSystem.getInstance().findFileByIoFile(packageInfoJavaFile) ?: virtualFile
                } else {
                    val packageinfoFile = File(packageDir, "packageinfo")
                    if (packageinfoFile.exists()) {
                        return LocalFileSystem.getInstance().findFileByIoFile(packageinfoFile) ?: virtualFile
                    }
                }
            }
        }
        return virtualFile
    }

}
