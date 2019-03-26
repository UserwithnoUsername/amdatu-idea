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

/**
 * This file contains some extension methods that provide reflective access to some bnd internals.
 *
 * Concentrated in a single file so it's easier to validate that they still work when we upgrade to a newer bnd version.
 */
package org.amdatu.idea

import aQute.bnd.osgi.Processor
import aQute.bnd.repository.maven.pom.provider.BndPomRepository
import aQute.bnd.repository.maven.pom.provider.PomConfiguration
import aQute.bnd.repository.maven.provider.Configuration
import aQute.bnd.repository.maven.provider.MavenBndRepository
import com.intellij.util.containers.isNullOrEmpty

fun MavenBndRepository.configuration(): Configuration {
    return javaClass.getDeclaredField("configuration").let {
        it.isAccessible = true
        val value = it.get(this)
        return@let value as Configuration
    }
}

fun BndPomRepository.configuration(): PomConfiguration {
    return javaClass.getDeclaredField("configuration").let {
        it.isAccessible = true
        val value = it.get(this)
        return@let value as PomConfiguration
    }
}

fun Processor.isValid(allowWarnings:Boolean = true): Boolean {
    return (allowWarnings || this.warnings.isNullOrEmpty()) && this.errors.isNullOrEmpty()
}