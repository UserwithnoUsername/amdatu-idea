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

package org.amdatu.idea.run

import com.intellij.execution.configurations.LocatableRunConfigurationOptions

class BndRunConfigurationOptions : LocatableRunConfigurationOptions() {

    var bndRunFile: String? by string()
    var useAlternativeJre by property(false)
    var alternativeJrePath: String? by string()

    var test: String? by string()
    var moduleName: String? by string()
    var workingDirectory: String? by string()
    var passParentEnvs by property(false)
    var envs: MutableMap<String, String> by map()
    var programParameters: String? by string()


}