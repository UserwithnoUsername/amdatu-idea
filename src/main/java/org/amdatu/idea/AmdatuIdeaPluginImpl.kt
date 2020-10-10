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
import aQute.bnd.header.Attrs
import aQute.bnd.osgi.Constants
import aQute.bnd.osgi.Processor
import aQute.bnd.repository.osgi.OSGiRepository
import aQute.bnd.service.RepositoryPlugin
import aQute.service.reporter.Report
import com.intellij.ide.file.BatchFileChangeListener
import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import org.amdatu.idea.imp.BndProjectImporter
import java.io.File

val AMDATU_IDEA_NOTIFICATION_GROUP = NotificationGroup("Amdatu", NotificationDisplayType.TOOL_WINDOW, true)

interface AmdatuIdeaPlugin {

    fun isBndWorkspace(): Boolean

    fun isInitialized() : Boolean

    fun initialize()

    fun <T> withWorkspace(workspaceFunction: (workspace: Workspace) -> T): T

    fun refreshWorkspace()

    /**
     *
     * @return
     */
    fun pauseWorkspaceModelSync(reason: String): Boolean

    /**
     *
     * @return
     */
    fun resumeWorkspaceModelSync(reason: String)

    /**
     *
     * @return
     */
    fun workspaceModelSyncEnabled(): Boolean

    fun info(content: String, block: ((block: Notification) -> Unit)? = null)
    fun warning(content: String, block: ((block: Notification) -> Unit)? = null)
    fun error(content: String, block: ((block: Notification) -> Unit)? = null)
    fun report(processor: Processor, reportWarnings: Boolean = true)
}


class AmdatuIdeaPluginImpl(val project: Project) : AmdatuIdeaPlugin {

    private var myWorkspace: Workspace? = null
    private var myWorkspaceModelSync: WorkspaceModelSync? = null

    override fun initialize() {
        if (!project.isBndWorkspaceProject()) {
            throw IllegalStateException("Not a bnd workspace")
        }

        Workspace.setDriver(Constants.BNDDRIVER_INTELLIJ)
        Workspace.addGestalt(Constants.GESTALT_INTERACTIVE, Attrs())

        val initTask = object : Task.WithResult<Workspace, java.lang.Exception>(project, "Initializing workspace", false) {
            override fun compute(indicator: ProgressIndicator): Workspace {
                val workspace = Workspace(File(project.guessProjectDir()!!.path))
                workspace.plugins // Just get the plugins once to trigger initialization

                workspace.validateRepositories(project, indicator)

                workspace.errors.stream()
                        .map<Report.Location> { msg ->
                            var location: Report.Location? = workspace.getLocation(msg)
                            if (location == null) {
                                location = object : Report.Location() {

                                }
                                location.message = msg
                            }
                            location
                        }
                        .forEach {
                            it.notify(NotificationType.ERROR, project)
                        }

                val importer = BndProjectImporter(project, workspace.allProjects)
                importer.setupProject(workspace)
                importer.resolve(true)

                return workspace
            }

        }
        initTask.queue()
        myWorkspace = initTask.result

        val messageBusConnection = project.messageBus.connect()
        val workspaceModelSync = WorkspaceModelSync(project, this)
        myWorkspaceModelSync = workspaceModelSync
        messageBusConnection.subscribe<BulkFileListener>(VirtualFileManager.VFS_CHANGES, workspaceModelSync)

        messageBusConnection.subscribe(BatchFileChangeListener.TOPIC, object : BatchFileChangeListener {

            var batchListenerPause = false;

            override fun batchChangeStarted(p: Project, activityName: String?) {
                if (p == project) {
                    synchronized(batchListenerPause) {
                        batchListenerPause = myWorkspaceModelSync?.pause("Batch file change started") ?: false
                    }
                }

            }

            override fun batchChangeCompleted(project: Project) {
                synchronized(batchListenerPause) {
                    if (batchListenerPause) {
                        batchListenerPause = false
                        myWorkspaceModelSync?.resume("Batch file change completed")
                    }
                }
            }
        })

        info("Created bnd workspace")
    }

    override fun isBndWorkspace(): Boolean {
        return project.isBndWorkspaceProject()
    }

    override fun isInitialized(): Boolean {
        return myWorkspace != null
    }

    override fun <T> withWorkspace(workspaceFunction: (workspace: Workspace) -> T): T {
        myWorkspace
                ?.let {
                    return it.writeLocked { workspaceFunction.invoke(it) }
                }
                ?: throw notInitializedException()
    }

    override fun refreshWorkspace() {
        myWorkspaceModelSync?.syncNow() ?: throw notInitializedException()
    }

    override fun pauseWorkspaceModelSync(reason: String): Boolean {
        return myWorkspaceModelSync?.pause(reason) ?: throw notInitializedException()
    }

    override fun resumeWorkspaceModelSync(reason: String) {
        myWorkspaceModelSync?.resume(reason) ?: throw notInitializedException()
    }

    override fun workspaceModelSyncEnabled(): Boolean {
        return myWorkspaceModelSync?.enabled() ?: throw notInitializedException()
    }

    private fun notInitializedException(): IllegalStateException {
        return IllegalStateException("Workspace not initialized")
    }


    override fun info(content: String, block: ((block: Notification) -> Unit)?) {
        notification(NotificationType.INFORMATION, content, block)
    }


    override fun warning(content: String, block: ((block: Notification) -> Unit)?) {
        notification(NotificationType.ERROR, content, block)
    }

    override fun error(content: String, block: ((block: Notification) -> Unit)?) {
        notification(NotificationType.ERROR, content, block)
    }

    private fun notification(type: NotificationType, content: String, block: ((block: Notification) -> Unit)?) {
        val notification = AMDATU_IDEA_NOTIFICATION_GROUP
                .createNotification(type)
                .setTitle("Amdatu")
                .setContent(content)

        block?.invoke(notification)

        notification.notify(project)
    }

    override fun report(processor: Processor, reportWarnings: Boolean) {
        processor.errors?.forEach {
            message(NotificationType.ERROR, it, processor)
        }

        if (reportWarnings) {
            processor.warnings?.forEach {
                message(NotificationType.WARNING, it, processor)
            }
        }
    }

    private fun message(type: NotificationType, message: String, processor: Processor) {
        notification(type, message) { notification ->
            if (processor is aQute.bnd.build.Project) {
                notification.setTitle("${notification.title} [${processor.name}]")
            }

            val location = processor.getLocation(message) ?: Report.Location()
            if (location.file == null) {
                location.file = processor.propertiesFile?.path
            }

            addOpenLocation(notification, location)
        }
    }

    private fun addOpenLocation(notification: Notification, location: Report.Location?) {
        if (location?.file != null) {
            val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://" + location.file)

            if (virtualFile != null) {
                notification.addAction(object : AnAction("Open " + virtualFile.name) {

                    override fun actionPerformed(e: AnActionEvent) {
                        if (e.project == null) {
                            return
                        }

                        val fileEditorManager = FileEditorManager.getInstance(e.project!!)
                        fileEditorManager.openFile(virtualFile, true)
                    }
                })
            }
        }
    }

}

fun Workspace.validateRepositories(project: Project, indicator: ProgressIndicator): Boolean {
    indicator.text = "Refreshing Repositories"
    val plugins = getPlugins(RepositoryPlugin::class.java)
    var ok = true

    for (i in plugins.indices) {
        val plugin = plugins[i]

        try {
            plugin.list("*")
        } catch (e: Exception) {
            LOG.error("Failed to list repo contents for repo: " + plugin.name, e)
            if (plugin is OSGiRepository) {
                var refreshAction: AnAction? = null
                try {
                    val actions = plugin.actions()
                    if (actions.size == 1) {
                        val action = actions.entries.iterator().next()
                        refreshAction = object : AnAction(action.key) {

                            override fun actionPerformed(e: AnActionEvent) {
                                action.value.run()
                                project.getComponent(AmdatuIdeaPlugin::class.java)?.refreshWorkspace()
                            }
                        }

                    } else {
                        LOG.error("Oops only expected a single action here")
                    }

                } catch (ee: Exception) {
                    LOG.error(ee)
                }

                val notification = AMDATU_IDEA_NOTIFICATION_GROUP
                        .createNotification(NotificationType.ERROR)
                        .setTitle("Amdatu: Repository ${plugin.name} is failing with exception '${e.message}'")
                        .setContent("Try to refresh the repo? ")

                refreshAction?.let(notification::addAction)
                notification.notify(project)
            }

            ok = false
        }

        indicator.fraction = i.toDouble() / plugins.size.toDouble()
    }
    ok = ok and project.getComponent(RepositoryValidationService::class.java).validateRepositories(this)
    return ok
}

fun Project.isBndWorkspaceProject(): Boolean {
    return basePath
            ?.let { File(it) }
            ?.let { File(it, Workspace.CNFDIR) }
            ?.isDirectory ?: false

}

fun Report.Location.notify(notificationType: NotificationType, project: Project) {

    AMDATU_IDEA_NOTIFICATION_GROUP
            .createNotification(notificationType)
            .setTitle("Workspace issue")
            .setContent(message)
            .apply {
                file
                        ?.let { locationFile ->
                            VirtualFileManager.getInstance().findFileByUrl("file://$locationFile")
                        }
                        ?.let { virtualFile ->
                            addAction(object : AnAction("Open ${virtualFile.name}") {

                                override fun actionPerformed(e: AnActionEvent) {
                                    if (e.project == null) {
                                        return
                                    }

                                    val fileEditorManager = FileEditorManager.getInstance(e.project!!)
                                    fileEditorManager.openFile(virtualFile, true)
                                }
                            })
                        }
            }
            .notify(project)

}

fun Project.info(content: String, block: (block: Notification) -> Unit) {
    val notification = AMDATU_IDEA_NOTIFICATION_GROUP
            .createNotification(NotificationType.INFORMATION)
            .setTitle("Amdatu")
            .setContent(content)

    notification.apply(block)

    notification.notify(this)
}

fun Project.info(content: String) {
    this.message(NotificationType.INFORMATION, content)
}

fun Project.error(content: String) {
    this.message(NotificationType.INFORMATION, content)
}

fun Project.message(type: NotificationType, content: String) {
    AMDATU_IDEA_NOTIFICATION_GROUP
            .createNotification(type)
            .setTitle("Amdatu")
            .setContent(content)
            .notify(this)
}
