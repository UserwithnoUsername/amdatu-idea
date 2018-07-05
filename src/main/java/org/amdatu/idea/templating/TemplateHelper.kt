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

package org.amdatu.idea.templating

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import org.apache.commons.io.IOUtils
import org.bndtools.templating.ResourceMap
import org.bndtools.templating.ResourceType
import org.bndtools.templating.Template
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

val LOG = Logger.getInstance("org.amdatu.idea.templating.TemplateHelper")

fun applyWorkspaceTemplate(project: Project, template: Template) {
    if (project.basePath == null) {
        throw IllegalStateException("Unable to apply workspace template, project.basepath == null")
    }
    val projectRoot = File(project.basePath)
    applyTemplate(template, projectRoot, defaultTemplateContext())

    LocalFileSystem.getInstance().refreshIoFiles(listOf(projectRoot), false, true, null)
}

fun applyModuleTemplate(module: Module, template: Template, templateParams: Map<String, List<Any>>) {

    val moduleName = module.name

    val map = defaultTemplateContext()
    val basePackageDir = moduleName.replace("\\.".toRegex(), "/")
    map["basePackageDir"] = listOf<Any>(basePackageDir)
    map["basePackageName"] = listOf<Any>(moduleName)

    map.putAll(templateParams)

    val projectRoot = File(module.project.basePath)
    val moduleRoot = File(projectRoot, moduleName)
    applyTemplate(template, moduleRoot, map)

    // Create base package package if it does not exist after applying the template
    val sourceDir = File(moduleRoot, "src")

    if (sourceDir.isDirectory) {
        File(sourceDir, basePackageDir).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    LocalFileSystem.getInstance().refreshIoFiles(listOf(moduleRoot), false, true, null)
}

private fun defaultTemplateContext(): java.util.HashMap<String, List<Any>> {
    val map = java.util.HashMap<String, List<Any>>()
    map["srcDir"] = listOf<Any>("src")
    map["testSrcDir"] = listOf<Any>("test")
    return map
}

private fun applyTemplate(template: Template, dir: File, map: Map<String,List<Any>>) {
    val resourceMap: ResourceMap
    try {
        resourceMap = template.generateOutputs(map)
    } catch (e: Exception) {
        throw RuntimeException("Failed to process myTemplate " + template.name, e)
    }

    for ((relativePath, resource) in resourceMap.entries()) {
        val type = resource.type
        when (type) {
            ResourceType.Folder -> {
                val folder = File(dir, relativePath)
                createFolder(folder)
            }
            ResourceType.File -> {
                val file = File(dir, relativePath)

                createFolder(file.parentFile)

                try {
                    BufferedInputStream(resource.content).use { inputStream ->
                        FileOutputStream(file).use { outputStream ->
                            IOUtils.copy(inputStream, outputStream)
                        }
                    }
                } catch (e: IOException) {
                    LOG.error("Failed to write $file", e)
                }

            }
            else -> LOG.error("Template ${template.name} contains unsupported resource type $type, path: $relativePath")
        }
    }

}

private fun createFolder(folder: File) {
    if (!folder.exists()) {
        if (!folder.mkdirs()) {
            throw RuntimeException("Failed to create dir: $folder")
        }
    } else if (!folder.isDirectory) {
        throw RuntimeException("File exists but is not a dir: $folder")
    }
}