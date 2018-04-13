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