package org.amdatu.ide.toolwindow

import aQute.bnd.service.RepositoryPlugin
import aQute.bnd.version.Version
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.panel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.AbstractTreeModel
import org.amdatu.ide.AmdatuIdePlugin
import org.amdatu.ide.WorkspaceRefreshedNotifier
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class RepositoriesPanel(private val myProject: Project) {

    fun createRepositoriesPanel(): JPanel {
        val searchField = JTextField()
        val repositoriesTreeModel = RepositoriesTreeModel(myProject, searchField)
        val repositoriesTree = Tree(repositoriesTreeModel)

        searchField.addActionListener { _ ->
            run {
                repositoriesTree.updateUI()
            }
        }

        repositoriesTree.apply {
            isEditable = false
            isRootVisible = false
            cellRenderer = object : ColoredTreeCellRenderer() {
                override fun customizeCellRenderer(tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {
                    when (value) {
                        is RepositoryPlugin -> append(value.name)
                        is BsnWithRepoRef -> append(value.bsn)
                        else -> append(value.toString())
                    }
                }
            }
        }

        val messageBusConnection = myProject.messageBus.connect()
        messageBusConnection.subscribe(WorkspaceRefreshedNotifier.WORKSPACE_REFRESHED, WorkspaceRefreshedNotifier {

            ProgressManager.getInstance().runProcess({
                repositoriesTreeModel.refreshRepositories()
                repositoriesTree.updateUI()
            }, null)

        })

        return panel {
            row {
                searchField(CCFlags.growX, CCFlags.pushX)
                buttonGroup {
                    JButton("Search")()
                }
            }
            row {
                panel {
                    row {
                        JScrollPane(repositoriesTree)(CCFlags.growX, CCFlags.pushX, CCFlags.growY, CCFlags.pushY)
                    }
                }(CCFlags.growX, CCFlags.pushX, CCFlags.growY, CCFlags.pushY)
            }
        }
    }

    data class BsnWithRepoRef(val bsn: String, val repositoryPlugin: RepositoryPlugin)

    class RepositoriesTreeModel(val myProject: Project, private val mySearchField: JTextField) : AbstractTreeModel() {
        private val repositoryPlugins: MutableList<RepositoryPlugin> = mutableListOf()
        private val repoBundlesCache = mutableMapOf<RepositoryPlugin, List<BsnWithRepoRef>>()
        private val myRoot = DefaultMutableTreeNode(0)

        init {
//            val workspace = myProject.getComponent(AmdatuIdePlugin::class.java)?.workspace
//            repositoryPlugins = workspace
//                    ?.getPlugins(RepositoryPlugin::class.java)
//                    ?.sortedBy { repo -> repo.name }
//                    ?.toMutableList() ?: mutableListOf()
            refreshRepositories();
        }

        fun refreshRepositories() {
            val workspace = myProject.getComponent(AmdatuIdePlugin::class.java)?.workspace
            repositoryPlugins.clear()
            repoBundlesCache.clear()
            repositoryPlugins.addAll(workspace
                    ?.getPlugins(RepositoryPlugin::class.java)
                    ?.sortedBy { repo -> repo.name }
                    ?.toMutableList() ?: mutableListOf())
        }

        override fun getChild(parent: Any?, index: Int): Any {
            return when {
                parent === root -> repositoryPlugins.getOrNull(index) as Any
                parent is RepositoryPlugin -> {
                    repoBundlesCache.computeIfAbsent(parent, { parent.list(null).map { BsnWithRepoRef(it, parent) } })
                            .filter { bsnMatchesFilter(it.bsn) }
                            .getOrNull(index) as Any
                }
                parent is BsnWithRepoRef -> {
                    ArrayList(parent.repositoryPlugin.versions(parent.bsn) ?: emptySet()).getOrNull(index) as Any
                }
                else -> Unit
            }
        }

        private fun bsnMatchesFilter(bsn: String): Boolean {
            val text = mySearchField.text
            if (text == null || text.isBlank()) {
                return true
            }

            return if (text.contains('?') || text.contains('*')) {
                text.toRegex().containsMatchIn(bsn)
            } else {
                bsn.startsWith(text)
            }
        }

        override fun getRoot(): Any {
            return myRoot
        }

        override fun isLeaf(node: Any?): Boolean {
            return when (node) {
                is Version -> true
                else -> false
            }
        }

        override fun getChildCount(parent: Any?): Int {
            return when {
                parent === root -> repositoryPlugins.size
                parent is RepositoryPlugin -> {
                    repoBundlesCache.computeIfAbsent(parent, { parent.list(null).map { BsnWithRepoRef(it, parent) } })
                            .filter { bsnMatchesFilter(it.bsn) }
                            .size
                }
                parent is BsnWithRepoRef -> {
                    parent.repositoryPlugin.versions(parent.bsn).size
                }
                else -> 0
            }
        }

        override fun valueForPathChanged(path: TreePath?, newValue: Any?) {
        }

        override fun getIndexOfChild(parent: Any?, child: Any?): Int {
            return when {
                parent === root -> repositoryPlugins.indexOf(child)
                parent is RepositoryPlugin -> {
                    repoBundlesCache.computeIfAbsent(parent, { parent.list(null).map { BsnWithRepoRef(it, parent) } })
                            .filter { bsnMatchesFilter(it.bsn) }
                            .indexOf(child)
                }
                parent is BsnWithRepoRef -> {
                    parent.repositoryPlugin.versions(parent.bsn).indexOf(child)
                }
                else -> -1
            }
        }
    }
}