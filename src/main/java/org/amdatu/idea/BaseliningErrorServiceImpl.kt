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
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.regex.Pattern

data class BaseliningBundleSuggestion(val source: VirtualFile, val currentVersion: String, val suggestedVersion: String)
data class BaseliningPackageSuggestion(val source: VirtualFile, val suggestedVersion: String)


interface BaseliningErrorService {
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

    override fun compilationFinished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext) {
        super.compilationFinished(aborted, errors, warnings, compileContext)


        val allCompilerMessages = compileContext.getMessages(CompilerMessageCategory.INFORMATION) +
                compileContext.getMessages(CompilerMessageCategory.WARNING) +
                compileContext.getMessages(CompilerMessageCategory.ERROR)

        bundleSuggestions.clear()

        allCompilerMessages
                .filter { it.message.contains("The bundle version ") }
                .forEach { warning ->
                    val message = warning.message.lines().first()

                    val suggestionMarker = "must be at least "
                    val suggestedVersion = message.substring(message.indexOf(suggestionMarker) + suggestionMarker.length)
                    val currentMarker = "The bundle version ("
                    val currentMarkerIndex = message.indexOf(currentMarker) + currentMarker.length
                    val currentVersion = message.substring(currentMarkerIndex, currentMarkerIndex + 5)

                    bundleSuggestions[warning.virtualFile] = BaseliningBundleSuggestion(warning.virtualFile, currentVersion, suggestedVersion)
                }

        packageSuggestions.clear()

        allCompilerMessages
                .filter { it.message.contains("Baseline mismatch for package ") }
                .forEach { warning ->
                    val message = warning.message.lines().first()

                    val patternString = ".*suggest (.*) or.*"
                    val pattern = Pattern.compile(patternString, Pattern.DOTALL)
                    val matcher = pattern.matcher(message)
                    val matches = matcher.matches()
                    if (matches) {
                        val version = matcher.group(1)
                        packageSuggestions[warning.virtualFile] = BaseliningPackageSuggestion(warning.virtualFile, version)
                    }
//                    throw IllegalStateException("Could not extract version from message: $message")
                }

    }


}