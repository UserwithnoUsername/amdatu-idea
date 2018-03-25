package org.amdatu.ide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.amdatu.ide.AmdatuIdePlugin

class RefreshWorkspaceAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        event.project?.getComponent(AmdatuIdePlugin::class.java)?.apply {
            if (isBndWorkspace)  {
                refreshWorkspace(true)
            }
        }
    }

}