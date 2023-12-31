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

import aQute.bnd.osgi.Processor
import aQute.service.reporter.Report
import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager

class AmdatuIdeaNotificationService(private val myProject: Project) {

    private val myTitle = "Amdatu"

    private val myNotificationGroup = NotificationGroup("Amdatu", NotificationDisplayType.TOOL_WINDOW, true)

    fun info(message: String) {
        info(message, null)
    }

    fun info(message: String, processor: Processor?) {
        message(NotificationType.INFORMATION, message, processor)
    }

    fun warning(message: String) {
        warning(message, null)
    }

    fun warning(message: String, processor: Processor? = null) {
        message(NotificationType.WARNING, message, processor)
    }

    fun error(message: String, processor: Processor? = null) {
        message(NotificationType.ERROR, message, processor)
    }

    fun error(location: Report.Location) {
        message(NotificationType.ERROR, myTitle, location.message, location)
    }

    fun report(processor: Processor, reportWarnings: Boolean = true) : Boolean {
        processor.errors?.forEach {
            error(it, processor)
        }

        if (reportWarnings) {
            processor.warnings?.forEach {
                warning(it, processor)
            }
        }

        return processor.errors?.firstOrNull() != null ||
                (reportWarnings && processor.warnings?.firstOrNull() != null)
    }

    private fun message(type: NotificationType, message: String, processor: Processor?) {
        var title = myTitle

        if (processor is aQute.bnd.build.Project) {
            title = String.format("%s [%s]", title, processor.name)
        }

        var location: Report.Location? = null
        if (processor != null) {
            location = processor.getLocation(message) ?: Report.Location()
            if (location.file == null) {
                location.file = processor.propertiesFile?.path
            }
        }

        message(type, title, message, location)
    }

    fun message(type: NotificationType, title: String, message: String, location: Report.Location?) {
        val notification = myNotificationGroup.createNotification(title, message, type, null)
        if (location != null) {
            addOpenLocation(notification, location)
        }
        notification.notify(myProject)
    }

    fun notification(type: NotificationType, title: String, message: String) {
        val notification = myNotificationGroup.createNotification(title, message, type, null)
        notification.notify(myProject)
    }

    private fun addOpenLocation(notification: Notification, location: Report.Location?) {
        if (location?.file != null) {
            val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://" + location.file)

            if (virtualFile != null) {
                notification.addAction(object : AnAction("Open " + virtualFile.name) {

                    override fun actionPerformed(e: AnActionEvent?) {
                        if (e == null || e.project == null) {
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