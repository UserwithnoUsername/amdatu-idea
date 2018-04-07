package org.amdatu.ide.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class AmdatuIdeToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {

        val contentFactory = ContentFactory.SERVICE.getInstance()
        val content = contentFactory.createContent(RepositoriesPanel(project).createRepositoriesPanel(), "", true)
        toolWindow.contentManager.addContent(content)
    }
}
