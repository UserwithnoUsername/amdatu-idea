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

package org.amdatu.idea.actions

import aQute.bnd.osgi.resource.CapReqBuilder
import aQute.bnd.repository.osgi.OSGiRepository
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.amdatu.idea.AmdatuIdeaNotificationService
import org.amdatu.idea.AmdatuIdeaPlugin
import org.osgi.resource.Namespace
import java.util.*

class CheckForBlueprintUpdate : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        checkForUpdate(project)
    }

    fun checkForUpdate(project: Project) {
        val amdatuIdeaPlugin = project.getComponent(AmdatuIdeaPlugin::class.java) ?: return
        val workspace = amdatuIdeaPlugin.workspace ?: return

        if (!workspace.getFile("cnf/ext/blueprint.bnd").exists()) {
            return // not an Amdatu Blueprint workspace no need to check
        }

        val workspaceVersion: String? = workspace.get("blueprintVersion")


        val repoVersion = OSGiRepository().use { osGiRepository ->
            osGiRepository.setRegistry(workspace)
            osGiRepository.setReporter(workspace)
            val map = HashMap<String, String>()
            map["name"] = "Amdatu preferences template repos"
            map["poll.time"] = "-1"
            map["max.stale"] = "0"

            map["locations"] = "https://repository.amdatu.org/amdatu-blueprint/latest.xml"
            osGiRepository.setProperties(map)


            val filterStr = String.format("(%s=*)", "blueprintVersion")
            val requirement = CapReqBuilder("org.amdatu.blueprint.template")
                    .addDirective(Namespace.REQUIREMENT_FILTER_DIRECTIVE, filterStr)
                    .buildSyntheticRequirement()


            osGiRepository.findProviders(listOf(requirement))
                    .getOrDefault(requirement, emptyList())
                    .filter({ cap -> cap.attributes["blueprintVersion"] != null })
                    .map { cap -> cap.attributes["blueprintVersion"] as String }
                    .filter { version -> version != "snapshot" }
                    .sorted()
                    .lastOrNull()
        }

        val notificationService = project.getComponent(AmdatuIdeaNotificationService::class.java)
        when {
            repoVersion == null -> {
                notificationService.error("Failed to check for Amdatu Blueprint updates. Could not retrieve latest version.")
            }
            workspaceVersion == null -> {
                notificationService.notification(NotificationType.INFORMATION, "Amdatu",
                        "Amdatu Blueprint update available workspace version unknown last released version '$repoVersion'",
                        getUpdateAction("Update to $repoVersion"))
            }
            workspaceVersion == repoVersion -> {
                notificationService.info("Amdatu Blueprint up to date.")
            }
            workspaceVersion == "snapshot" -> {
                notificationService.notification(NotificationType.INFORMATION, "Amdatu",
                        "This workspace is using a snapshot version, latest releases version is $repoVersion",
                        getUpdateAction("Update to $repoVersion"))
            }
            workspaceVersion.substring(1).toInt() < repoVersion.substring(1).toInt() -> {
                notificationService.notification(NotificationType.INFORMATION, "Amdatu",
                        "Amdatu Blueprint update available workspace version '$workspaceVersion' last released version '$repoVersion'",
                        getUpdateAction("Update to $repoVersion"))
            }
            else -> {
                notificationService.notification(NotificationType.INFORMATION, "Amdatu",
                        "This is strange the Amdatu Blueprint version in this workspace ($workspaceVersion) is higher than the latest release ($repoVersion)",
                        getUpdateAction("Revert to $repoVersion"))
            }
        }
    }

    private fun getUpdateAction(text: String) : AnAction {
        return  object : AnAction(text) {

            override fun actionPerformed(e: AnActionEvent) {
                UpdateConfigurationProjectAction().actionPerformed(e)
            }
        }
    }
}