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

import aQute.bnd.build.Workspace
import aQute.bnd.repository.maven.provider.MavenBndRepository
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.PathUtil
import com.intellij.util.containers.isNullOrEmpty
import org.amdatu.idea.imp.BndProjectImporter
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

sealed class Action
object RefreshWorkspace : Action()
object ReImportProject : Action()
data class ImportModules(val modulesToImport: Set<String>) : Action()
object None : Action()


/**
 * BulkFileListener that updates IntelliJ modules when changes are detected in the bnd model.
 *
 *
 *  - When a file that's part of the configuration project (cnf) has changed
 *    - Bnd workspace is refreshed
 *    - IntelliJ modules are re-imported
 *  - If a bnd project is added the workspace projects are refreshed and intellij modules are re-imported
 *    - Bnd workspace projects are refreshed
 *    - IntelliJ modules are re-imported
 *  - If a bnd project file is updated
 *    - The changed project is refreshed
 *    - The IntelliJ module for the changed project will be re-imported
 *
 */
class WorkspaceModelSync(val project: Project, val amdatuIdeaPlugin: AmdatuIdeaPlugin) : BulkFileListener {

    val log = Logger.getInstance(WorkspaceModelSync::class.java)

    val instance: ProjectFileIndex = ProjectFileIndex.getInstance(project)

    private val handleChanges = AtomicBoolean(true)

    @Volatile
    private var deferredAction: Action = None

    fun enabled(): Boolean {
        return handleChanges.get()
    }

    fun pause(reason: String): Boolean {
        val wasEnabled = handleChanges.getAndSet(false)
        if (wasEnabled) log.info("Pause WorkspaceFileChangeListener reason: '$reason'")
        return wasEnabled
    }

    fun resume(reason: String) {
        log.info("Resume WorkspaceFileChangeListener reason: '$reason'")
        val action = synchronized(handleChanges) {
            handleChanges.set(true)

            val action = deferredAction
            deferredAction = None
            action
        }

        if (action !is None) {
            handleChange(action)
        }
    }

    fun syncNow() {
        handleChange(RefreshWorkspace)
    }

    override fun after(events: MutableList<out VFileEvent>) {
        val action = synchronized(handleChanges) {
            if (!handleChanges.get()) {
                // Sync has been paused, wait update the deferred action that will run when the Sync is resumed
                if (deferredAction is RefreshWorkspace) {
                    // No need to do further checks here, workspace refresh is already planned
                } else {
                    val action = determineAction(events)
                    deferredAction = when (deferredAction) {
                        RefreshWorkspace -> RefreshWorkspace
                        ReImportProject -> {
                            when (action) {
                                RefreshWorkspace -> RefreshWorkspace
                                ReImportProject -> ReImportProject
                                is ImportModules -> ReImportProject
                                None -> deferredAction
                            }
                        }
                        is ImportModules -> when (action) {
                            RefreshWorkspace -> RefreshWorkspace
                            ReImportProject -> ReImportProject
                            is ImportModules -> ImportModules((deferredAction as ImportModules).modulesToImport.plus(action.modulesToImport))
                            None -> deferredAction
                        }
                        None -> action
                    }
                }

                None
            } else {
                determineAction(events)
            }
        }

        if (action !is None) {
            handleChange(action)
        }
    }

    private fun handleChange(action: Action) {
        when (action) {
            RefreshWorkspace ->

                object : Task.Backgroundable(project, "Refreshing workspace", true) {
                    override fun run(indicator: ProgressIndicator) {

                        val start = System.currentTimeMillis()
                        amdatuIdeaPlugin.withWorkspace { workspace ->
                            workspace.clear()

                            closePlugins(workspace)

                            if (!workspace.refresh()) {
                                LOG.info("Forced workspace refresh")
                                workspace.forceRefresh()
                            }

                            workspace.plugins

                            if (!workspace.validateRepositories(project, amdatuIdeaPlugin, indicator)) {
                                amdatuIdeaPlugin.error("Repository validation failed, could not read from one or more repositories.") {
                                    it.setTitle("Workspace refresh failed")
                                }

                                return@withWorkspace
                            }

                            if (!workspace.errors.isNullOrEmpty()) {
                                amdatuIdeaPlugin.error("The workspace has errors") {
                                    it.setTitle("Workspace refresh failed")
                                }
                            }

                            workspace.refreshProjects()
                            // WORKAROUND BND issue: Invalid projects remain in the workspace tracker .. https://github.com/bndtools/bnd/issues/3004
                            val validProjects = workspace.allProjects.filter { it.isValid }
                            validProjects
                                    .forEach {
                                        it.clear()
                                        if (!it.refresh()) {
                                            // We have additional reasons for refreshing, bnd could ignore the refresh because it thinks it's not needed but ... we know better
                                            it.forceRefresh()
                                        }
                                    }

                            val importer = BndProjectImporter(project, validProjects)
                            importer.setupProject(workspace)
                            importer.resolve(true)

                        }
                        val workspaceRefreshedNotifier = project.messageBus.syncPublisher(WorkspaceRefreshedNotifier.WORKSPACE_REFRESHED)
                        workspaceRefreshedNotifier.workspaceRefreshed()
                        amdatuIdeaPlugin.info("Workspace refreshed in ${System.currentTimeMillis() - start} ms.") {
                            it.setTitle("Workspace refresh successful")
                        }
                    }
                }
                        .queue()
            ReImportProject -> {
                object : Task.Backgroundable(project, "Re-importing project", true) {
                    override fun run(indicator: ProgressIndicator) {
                        val start = System.currentTimeMillis()
                        amdatuIdeaPlugin.withWorkspace { workspace ->
                            workspace.refreshProjects()

                            // WORKAROUND BND issue: Invalid projects remain in the workspace tracker .. https://github.com/bndtools/bnd/issues/3004
                            val validProjects = workspace.allProjects.filter { it.isValid }
                            validProjects
                                    .forEach {
                                        it.clear()
                                        if (!it.refresh()) {
                                            // We have additional reasons for refreshing, bnd could ignore the refresh because it thinks it's not needed but ... we know better
                                            it.forceRefresh()
                                        }
                                    }

                            val importer = BndProjectImporter(project, validProjects)
                            importer.setupProject(workspace)
                            importer.resolve(true)
                        }

                        val workspaceRefreshedNotifier = project.messageBus.syncPublisher(WorkspaceRefreshedNotifier.WORKSPACE_REFRESHED)
                        workspaceRefreshedNotifier.workspaceRefreshed()
                        amdatuIdeaPlugin.info("Project re-imported in ${System.currentTimeMillis() - start} ms.") {
                            it.setTitle("Project re-imported")
                        }

                    }
                }.queue()
            }
            is ImportModules -> {

                object : Task.Backgroundable(project, "Re-importing modules", true) {
                    override fun run(indicator: ProgressIndicator) {
                        val start = System.currentTimeMillis()
                        amdatuIdeaPlugin.withWorkspace { workspace ->

                            val projectsToImport = action.modulesToImport.mapNotNull { moduleName ->
                                try {
                                    val project = workspace.getProject(moduleName)
                                    if (project != null) {
                                        project.clear()
                                        if (!project.refresh()) {
                                            // We have additional reasons for refreshing, bnd could ignore the refresh because it thinks it's not needed but ... we know better
                                            project.forceRefresh()
                                        }
                                        return@mapNotNull project
                                    } else {
                                        // refresh workspace projects as it doesn't know about the module
                                        LOG.error(IllegalStateException("Bnd doesn't know about $moduleName that was unexpected!"))
                                    }
                                } catch (e: Exception) {
                                    LOG.error("Failed to refresh project for module $moduleName", e)
                                }

                                return@mapNotNull null
                            }.toList()
                            // TODO: We used to always re-import all known projects there might be a reason for that, but if there is we can simplify a bit more as we don't need the list of projects in that case.
                            // TODO: And the trigger with a list of modules could also go in that case
                            val bndProjectImporter = BndProjectImporter(project, projectsToImport)
                            bndProjectImporter.resolve(true)
                        }

                        val workspaceRefreshedNotifier = project.messageBus.syncPublisher(WorkspaceRefreshedNotifier.WORKSPACE_REFRESHED)
                        workspaceRefreshedNotifier.workspaceRefreshed()
                        amdatuIdeaPlugin.info("Modules ${action.modulesToImport} re-imported in ${System.currentTimeMillis() - start} ms.") {
                            it.setTitle("Modules re-imported")
                        }
                    }
                }.queue()
            }
            None -> {
                // Nothing here
            }
        }
    }

    // WORKAROUND I think bnd should do this but doesn't. (reported on bnd's google groups)
    private fun closePlugins(workspace: Workspace) {
        for (plugin in workspace.plugins) {
            if (plugin is Closeable) {
                if (plugin is Workspace) {
                    // The workspace is added to the plugins, don't close that
                    continue
                }

                try {
                    LOG.info("Closing plugin" + plugin.javaClass)
                    plugin.close()
                    workspace.removeClose(plugin)
                } catch (e: Exception) {
                    LOG.error("Exception closing plugin", e)
                }
            }
        }
    }

    private fun determineAction(events: MutableList<out VFileEvent>): Action {

        val workspaceFiles = amdatuIdeaPlugin.withWorkspace { workspaceFileNames(it) }
        val currentProjectNames = amdatuIdeaPlugin
                .withWorkspace { it.currentProjects }
                .map { it.name }
                .toSet()

        val modulesToReImport = mutableSetOf<String>()

        var reImportProject = false

        for (file in events.mapNotNull(VFileEvent::getFile)) {
            if (!file.path.startsWith(project.basePath!!)) {
                continue // File is not part of the workspace, ignore
            }

            if (workspaceFiles.contains(file.path)) {
                return RefreshWorkspace // No need to look at the other files, the workspace will be refreshed
            }

            if (AmdatuIdeaConstants.BND_EXT == PathUtil.getFileExtension(file.path)
                    || AmdatuIdeaConstants.BND_RUN_EXT == PathUtil.getFileExtension(file.path)) {

                val module = instance.getModuleForFile(file)
                if (module == null || !currentProjectNames.contains(module.name)) {
                    // Bnd file not part set of workspace configuration files has changed
                    reImportProject = true
                } else {
                    // Refresh module module
                    modulesToReImport.add(module.name)
                }
            }
        }

        return when {
            reImportProject -> ReImportProject
            modulesToReImport.isNotEmpty() -> ImportModules(modulesToReImport)
            else -> None
        }
    }

    private fun workspaceFileNames(workspace: Workspace): Set<String> {
        val workspaceFiles = mutableListOf<File>()
        if (workspace.included != null) {
            workspaceFiles.addAll(workspace.included)
        }
        workspaceFiles.add(workspace.propertiesFile)

        // Include maven repo index files in the list of workspace files to trigger a workspace refresh if and index file has changed
        workspace.getPlugins(MavenBndRepository::class.java)
                .forEach { repo ->
                    val configuration = repo.configuration()
                    val index = configuration.index(repo.name.toLowerCase() + ".mvn")
                    workspaceFiles.add(workspace.getFile(index))
                }

        return workspaceFiles
                .map { it.absolutePath }
                .toSet()
    }

}
