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

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.amdatu.idea.AmdatuIdeaPlugin

class RefreshWorkspaceAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        event.project?.getComponent(AmdatuIdeaPlugin::class.java)?.apply {
            if (isBndWorkspace)  {
                refreshWorkspace(true)
            }
        }
    }

}