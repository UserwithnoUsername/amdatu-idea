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

import aQute.bnd.deployer.repository.FixedIndexedRepo
import aQute.bnd.deployer.repository.LocalIndexedRepo
import aQute.bnd.http.HttpClient
import aQute.bnd.osgi.Constants
import aQute.bnd.osgi.repository.XMLResourceParser
import aQute.bnd.repository.osgi.OSGiRepository
import aQute.bnd.service.RepositoryPlugin
import aQute.bnd.service.url.TaggedData
import aQute.lib.io.IO
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File
import java.net.URI
import java.time.Duration

val LOG = Logger.getInstance("org.amdatu.idea.RepoUtil")

fun getBundles(project: Project): Set<String> {
    val amdatuIdePlugin = project.getComponent(AmdatuIdeaPlugin::class.java) ?: return emptySet()

    val repositories = amdatuIdePlugin.workspace?.getPlugins(RepositoryPlugin::class.java) ?: return emptySet()

    return repositories
            .flatMap { it.list(null) }
            .toSortedSet()

}

fun getBundlesOnlyAvailableInBaselineRepo(project: Project): Set<String> {
    val amdatuIdePlugin = project.getComponent(AmdatuIdeaPlugin::class.java) ?: return emptySet()

    val workspace = amdatuIdePlugin.workspace ?: return emptySet()

    val baselineRepoName = workspace.get(Constants.BASELINEREPO) ?: return emptySet() // no baseline repo

    val repositories = workspace.getPlugins(RepositoryPlugin::class.java) ?: return emptySet()

    return repositories
            .filter { it.name == baselineRepoName }
            .flatMap { it.list(null) }
            .filter { workspace.getProject(it) == null }
            .toSet()
}

/**
 * Validate uri's used in as locations for OSGiRepository and FixedIndexedRepo instances in the workspace and report
 * issues.
 *
 * This wil report issues when
 *  * File not found
 *  * Problems downloading
 *  * Problems with parsing the file
 *
 * @param amdatuIdeaPlugin Amdatu idea plugin instance
 */
fun validateRepoLocations(amdatuIdeaPlugin: AmdatuIdeaPlugin) {

    val workspace = amdatuIdeaPlugin.workspace
    val notificationService = amdatuIdeaPlugin.notificationService
    val client = workspace.getPlugin(HttpClient::class.java)

    workspace.getPlugins(RepositoryPlugin::class.java)
            .filter { it is OSGiRepository || it is FixedIndexedRepo }
            .filter { it !is LocalIndexedRepo }
            .filter { "Build" != it.name } // Skip this repo that's added by bnd
            .forEach { repositoryPlugin ->
                for (uriString in repositoryPlugin.location.split(",")) {
                    val uri = URI.create(uriString)

                    try {
                        validateRepoLocationUri(uri, notificationService, repositoryPlugin, client)
                    } catch (e: Exception) {
                        LOG.error("Exception in repo uri validation: $uri", e)
                    }
                }
            }
}

private fun validateRepoLocationUri(uri: URI, notificationService: AmdatuIdeaNotificationService, repositoryPlugin: RepositoryPlugin, client: HttpClient) {
    if (uri.scheme == null || uri.scheme.toLowerCase() == "file") {
        if (!File(uri).exists()) {
            notificationService.warning("Failed to load repository index file for repo: '${repositoryPlugin.name}'." +
                    "\nFile '$uri' doesn't exist.")
        }
    } else if (uri.toURL().protocol.startsWith("http")) {

        val send: TaggedData = client.build()
                .useCache(Duration.ofDays(365).toMillis()) // Use cached files, for a year the repo will trigger refreshing them
                .get(TaggedData::class.java)
                .go(uri) as TaggedData

        if (send.responseCode !in 200..399) {
            notificationService.warning("Failed to load repository index file for repo: '${repositoryPlugin.name}'." +
                    "\nFailed location: '$uri'" +
                    "\nReason: download failed, http response code '${send.responseCode}'")
        } else {
            val downloadedFile = send.file

            try {
                val parser = XMLResourceParser(IO.stream(downloadedFile), repositoryPlugin.name, 0, mutableSetOf(), uri)
                parser.parse()

                if (parser.errors.isNotEmpty()) {
                    val message = StringBuilder("Failed to load repository index file for repo: '${repositoryPlugin.name}'." +
                            "\n Failed location: '$uri'" +
                            "\n Reason (from bnd reporter): ")
                    parser.errors.forEach {
                        message.append("\n\t$it")
                    }
                    notificationService.warning(message.toString())
                }
            } catch (e: Exception) {
                val message = StringBuilder("Failed to load repository index file for repo: '${repositoryPlugin.name}'." +
                        "\nFailed location: '$uri'" +
                        "\nReason: Failed to parse repository index exception: ${e.message}" +
                        "\nCached index file: $downloadedFile")
                notificationService.warning(message.toString())
            }
        }
    }
}