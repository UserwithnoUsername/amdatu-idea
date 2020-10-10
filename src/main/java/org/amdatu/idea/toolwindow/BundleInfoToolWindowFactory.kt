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

package org.amdatu.idea.toolwindow

import aQute.bnd.header.Attrs
import aQute.bnd.osgi.Builder
import aQute.bnd.osgi.Clazz
import aQute.bnd.osgi.Constants
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.panel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.AbstractTreeModel
import org.amdatu.idea.AmdatuIdeaConstants
import org.amdatu.idea.AmdatuIdeaPlugin
import java.io.File
import java.util.*
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class BundleInfoToolWindowFactory : ToolWindowFactory {

    private val calculatedImportsTreeModel = CalculatedImportsTreeModel()
    private val calculatedImportsTree = Tree(calculatedImportsTreeModel)
    private val calculateImportsBtn = JButton("Calculate imports")
    private var file: VirtualFile? = null

    override fun isApplicable(project: Project): Boolean {
        return project.getComponent(AmdatuIdeaPlugin::class.java)?.isBndWorkspace() == true
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.stripeTitle = "Bundle info"
        calculatedImportsTree.apply {
            isEditable = false
            isRootVisible = false
            cellRenderer = object : ColoredTreeCellRenderer() {
                override fun customizeCellRenderer(tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {
                    when (value) {
                        is Package -> {
                            append(value.fqn, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                            val attrsString = value.attrs.toString()
                            if (attrsString.isNotEmpty()) {
                                append(";$attrsString")
                            }
                        }
                        is Class -> {
                            icon = when {
                                value.isAnnotation -> AllIcons.Nodes.Annotationtype
                                value.isInterface -> AllIcons.Nodes.Interface
                                value.isEnum -> AllIcons.Nodes.Enum
                                value.isAbstract -> AllIcons.Nodes.AbstractClass
                                else -> AllIcons.Nodes.Class
                            }
                            append(value.fqn)
                        }
                        else -> append(value.toString())
                    }
                }
            }
        }

        calculateImportsBtn.addActionListener {
            updateToolWindow(project)
        }

        val content = ContentFactory.SERVICE.getInstance().createContent(createBundleInfoPanel(), "Calculated imports  ", false)

        toolWindow.contentManager.addContent(content)


        val messageBusConnection = project.messageBus.connect()

        messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                if (!project.isOpen) return

                if (event.newFile == null || AmdatuIdeaConstants.BND_EXT != event.newFile?.extension) {
                    file = null
                    calculatedImportsTreeModel.setImports(null)
                    toolWindow.hide(null)
                    calculatedImportsTree.updateUI()
                    calculateImportsBtn.isEnabled = false
                    return
                } else {
                    file = event.newFile
                    calculatedImportsTreeModel.setImports(null)
                    calculatedImportsTree.updateUI()
                    calculateImportsBtn.isEnabled = true
                }
            }
        })

        messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: MutableList<out VFileEvent>) {
                if (!project.isOpen) return

                val currentPath = file?.path ?: return
                if (events.any { it.path == currentPath}) {
                    // TODO: Set message that the list is potentially out of date and should be re-calculated?!
//                    calculatedImportsTreeModel.setImports(null)
                    calculatedImportsTree.updateUI()
                    calculateImportsBtn.isEnabled = true
                }
            }
        })
    }

    private fun updateToolWindow(project: Project) {
        val f = file ?: return
        object : Task.Backgroundable(project, "Calculating imports", false) {
            override fun run(indicator: ProgressIndicator) {
                getBuilderForFile(project, f)?.use { builder ->
                    calculatedImportsTreeModel.setImports(null)
                    calculatedImportsTree.updateUI()

                    // Disable baselining and sources to speed up the build
                    builder.set(Constants.BASELINE, "")
                    builder.set(Constants.SOURCES, false.toString())

                    builder.build()

                    val packages = builder.imports
                    val imports = if (packages === null) {
                        null
                    } else {
                        packages
                                .map { entry ->
                                    val importingClasses = findImportingClasses(entry.key.fqn, builder)
                                            .sortedWith(compareBy { it.fqn })
                                            .map { Class(it.fqn, it.isAbstract, it.isAnnotation, it.isEnum, it.isInterface) }
                                    Package(entry.key.fqn, entry.value, importingClasses)
                                }
                                .sortedWith(compareBy { it.fqn })
                    }

                    calculatedImportsTreeModel.setImports(imports)
                    calculatedImportsTree.updateUI()
                    calculateImportsBtn.isEnabled = false
                }
            }
        }.queue()
    }

    private fun findImportingClasses(pkgName: String, builder: Builder): List<Clazz> {
        val classes = LinkedList<Clazz>()
        val importers = builder.getClasses("", "IMPORTING", pkgName)

        for (clazz in importers) {
            val fqn = clazz.fqn
            val dot = fqn.lastIndexOf('.')
            if (dot >= 0) {
                val pkg = fqn.substring(0, dot)
                if (pkgName != pkg)
                    classes.add(clazz)
            }
        }
        return classes
    }

    private fun getBuilderForFile(project: Project, file: VirtualFile): Builder? {
        val projectFileIndex = ProjectFileIndex.getInstance(project)
        val moduleForFile = projectFileIndex.getModuleForFile(file) ?: return null
        val bndProject = project.getComponent(AmdatuIdeaPlugin::class.java)?.withWorkspace { ws -> ws.getProject(moduleForFile.name) } ?: return null

        return if (bndProject.get(Constants.SUB) == null || file.name == "bnd.bnd") {
            bndProject.getBuilder(null)
        } else {
            bndProject.getSubBuilder(File(file.path))
        }
    }

    private fun createBundleInfoPanel(): JComponent {
        return panel {
            row {
                cell(isVerticalFlow = true) {
                    this@row.row {
                        calculateImportsBtn()
                    }
                    this@row.row {
                        JScrollPane(calculatedImportsTree)(CCFlags.growX, CCFlags.pushX, CCFlags.growY, CCFlags.pushY)
                    }
                }
            }
        }
    }

    data class Package(val fqn: String, val attrs: Attrs, val importingClasses: List<Class>)
    data class Class(val fqn: String,
                     val isAbstract: Boolean,
                     val isAnnotation: Boolean,
                     val isEnum: Boolean,
                     val isInterface: Boolean)



    class CalculatedImportsTreeModel : AbstractTreeModel() {

        private val myRoot = DefaultMutableTreeNode(0)
        private var imports: List<Package>? = null

        fun setImports(imports: List<Package>?) {
            this.imports = imports
        }

        override fun getChild(parent: Any?, index: Int): Any {
            return when (parent) {
                root -> imports?.get(index) as Any
                is Package -> parent.importingClasses[index]
                else -> 0
            }
        }

        override fun getRoot() = myRoot

        override fun isLeaf(node: Any?): Boolean {
            return when (node) {
                is Class -> true
                is Package -> node.importingClasses.isEmpty()
                else -> false
            }
        }

        override fun getChildCount(parent: Any?): Int {
            return when (parent) {
                root -> imports?.size ?: 0
                is Package -> parent.importingClasses.size
                else -> 0
            }
        }

        override fun valueForPathChanged(path: TreePath?, newValue: Any?) {

        }

        override fun getIndexOfChild(parent: Any?, child: Any?): Int {
            return when (parent) {
                root -> imports?.indexOf(child) ?: -1
                is Package -> parent.importingClasses.indexOf(child)
                else -> -1
            }
        }

    }
}