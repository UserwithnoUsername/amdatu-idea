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
import aQute.bnd.http.HttpClient
import aQute.bnd.osgi.repository.XMLResourceParser
import aQute.bnd.repository.maven.pom.provider.BndPomRepository
import aQute.bnd.repository.osgi.OSGiRepository
import aQute.bnd.service.RepositoryPlugin
import aQute.bnd.service.url.TaggedData
import aQute.lib.io.IO
import com.intellij.openapi.project.Project
import java.io.File
import java.net.URI
import java.time.Duration

interface RepositoryValidationService {
    fun validateRepositories(workspace: Workspace): Boolean
}


class RepositoryValidationServiceImpl(val project: Project) : RepositoryValidationService {

    private val amdatuIdeaPlugin = project.getComponent(AmdatuIdeaPlugin::class.java)

    /**
     * Validate uri's used in as locations for OSGiRepository and FixedIndexedRepo instances in the workspace and report
     * issues.
     *
     * This wil report issues when
     *  * File not found
     *  * Problems downloading
     *  * Problems with parsing the file
     *
     * @param workspace Amdatu idea plugin instance
     */
    override fun validateRepositories(workspace: Workspace): Boolean {
        val client = workspace.getPlugin(HttpClient::class.java)

        val osgiReposOk: Boolean = workspace.getPlugins(OSGiRepository::class.java)
                .map { repositoryPlugin ->

                    repositoryPlugin.location.split(",")
                            .map { uriString ->

                                val uri = URI.create(uriString)

                                try {
                                    validateRepoLocationUri(uri, repositoryPlugin, client)
                                } catch (e: Exception) {
                                    LOG.error("Exception in repo uri validation: $uri", e)
                                    false
                                }
                            }
                            .all { it }
                }
                .all { it }


        val bndPomReposOk = workspace.getPlugins(BndPomRepository::class.java)
                .map { repositoryPlugin ->
                    val bndPomRepository = repositoryPlugin as BndPomRepository

                    val configuration = bndPomRepository.configuration()
                    configuration.releaseUrls().split(",")
                            .map { uriString ->
                                val uri = URI.create(uriString)
                                validateMavenUri(uri, repositoryPlugin, client)
                            }
                            .all { it }
                }
                .all { it }

        return osgiReposOk && bndPomReposOk
    }


    private fun validateRepoLocationUri(uri: URI, repositoryPlugin: RepositoryPlugin, client: HttpClient): Boolean {


        return if (uri.scheme == null || uri.scheme.toLowerCase() == "file") {
            if (!File(uri).exists()) {


                amdatuIdeaPlugin.warning("""
                            Failed to load repository index file for repo: '${repositoryPlugin.name}'.
                            File '$uri' doesn't exist.
                            """.trimIndent())
                false
            } else {
                true
            }
        } else if (uri.toURL().protocol.startsWith("http")) {

            val send: TaggedData = client.build()
                    .useCache(Duration.ofDays(365).toMillis()) // Use cached files, for a year the repo will trigger refreshing them
                    .get(TaggedData::class.java)
                    .go(uri) as TaggedData

            if (send.responseCode !in 200..399) {
                amdatuIdeaPlugin.warning("""
                            Failed to load repository index file for repo: '${repositoryPlugin.name}'.
                            Failed location: '$uri'
                            Reason: download failed, http response code '${send.responseCode}'
                            """.trimIndent())

                false
            } else {
                val downloadedFile = send.file

                try {
                    val parser = XMLResourceParser(IO.stream(downloadedFile), repositoryPlugin.name, 0, mutableSetOf(), uri)
                    parser.parse()

                    if (parser.errors.isNotEmpty()) {
                        val message = StringBuilder("""
                            Failed to load repository index file for repo: '${repositoryPlugin.name}'.
                            Failed location: '$uri'
                            Reason (from bnd reporter): """.trimIndent())
                        parser.errors.forEach {
                            message.append("\n\t$it")
                        }
                        amdatuIdeaPlugin.warning(message.toString())
                        false
                    } else {
                        true
                    }
                } catch (e: Exception) {

                    val message = StringBuilder("""
                            Failed to load repository index file for repo: '${repositoryPlugin.name}'.
                            Failed location: '$uri'
                            Reason: Failed to parse repository index exception: ${e.message}
                            Cached index file: $downloadedFile
                            """.trimIndent())
                    amdatuIdeaPlugin.warning(message.toString())
                    false
                }
            }
        } else {
            true
        }
    }

    private fun validateMavenUri(uri: URI, repositoryPlugin: RepositoryPlugin, client: HttpClient): Boolean {
        return if (uri.toURL().protocol.startsWith("http")) {
            try {

                val send: TaggedData = client.build()
                        .useCache(Duration.ofDays(365).toMillis()) // Use cached files, for a year the repo will trigger refreshing them
                        .get(TaggedData::class.java)
                        .go(uri) as TaggedData

                if (send.responseCode !in 200..399) {
                    amdatuIdeaPlugin.warning("""
                            Failed to load repository index file for repo: '${repositoryPlugin.name}'.
                            Failed location: '$uri'
                            Reason: download failed, http response code '${send.responseCode}'
                            """.trimIndent())
                    false
                } else {
                    true
                }
            } catch (e: Exception) {
                LOG.error("Exception in repo uri validation: $uri", e)
                false
            }
        } else {
            true
        }
    }
}
