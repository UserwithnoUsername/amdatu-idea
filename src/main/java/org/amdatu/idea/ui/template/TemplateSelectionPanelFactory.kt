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
package org.amdatu.idea.ui.template

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.layout.panel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.IconUtil
import com.intellij.util.ui.tree.AbstractTreeModel
import eu.maxschuster.dataurl.DataUrlSerializer
import org.apache.commons.io.IOUtils
import org.bndtools.templating.Template
import java.awt.Dimension
import java.awt.Image
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

val LOG = Logger.getInstance(TemplateSelectionPanelFactory::class.java)

class TemplateSelectionPanelFactory {

    fun create(templates: List<Template>,
               templateSelectionChanged: (template: Template?) -> Unit): JComponent {
        val templateTree = Tree()
        val descriptionPane = JTextPane()
        val onlyLatestVersion = JCheckBox(null, null, true)

        descriptionPane.apply {
            isEditable = false
            contentType = "text/html"
            isFocusable = false
            focusTraversalKeysEnabled = false
            preferredSize = Dimension(480, 60)
            minimumSize = Dimension(200, 40)
        }

        templateTree.apply {
            model = TemplateTreeModel(templates, onlyLatestVersion)
            isRootVisible = false
            isEditable = false
            cellRenderer = object : ColoredTreeCellRenderer() {
                override fun customizeCellRenderer(tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {
                    when (value) {
                        is String -> {
                            val name = sanitizeCategoryName(value)
                            append(name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                        }
                        is Template -> {
                            val icon = loadTemplateIcon(value)
                            setIcon(icon)

                            append(value.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                            append(" - " + value.version, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                            append(" (" + value.shortDescription + ")", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                        }
                    }
                }
            }
        }

        // Expand all nodes (reverse order to prevent expanding the first impacting the row count)
        for (i in templateTree.rowCount - 1 downTo 0) {
            templateTree.expandRow(i)
        }

        templateTree.addTreeSelectionListener {
            val selected = it.path.lastPathComponent
            if (selected is Template) {
                val helpText = selected.helpContent
                        ?.toURL()
                        ?.openStream()
                        ?.bufferedReader()
                        ?.use { it.readText() } ?: ""
                descriptionPane.text = helpText
                templateSelectionChanged(selected)
            } else {
                descriptionPane.text = ""
                templateSelectionChanged(null)
            }
        }

        onlyLatestVersion.addActionListener {
            templateTree.updateUI()
        }

        return panel {

            row {
                scrollPane(templateTree, growX, growY, pushX )
            }
            row {
                descriptionPane(growX, pushX)
            }
            row("Only latest version") {
                onlyLatestVersion(growX, pushX)
            }
        }
    }


    // The bndtools templates have some 'mmm /', 'nnnn /' prefix
    fun sanitizeCategoryName(category: String): String {
        return if (category.contains("/")) {
            category.substring(category.indexOf("/") + 1)
        } else {
            category
        }
    }

    internal fun loadTemplateIcon(template: Template): Icon {
        var icon = AllIcons.Nodes.Module
        val iconUri = template.icon
        if (iconUri != null) {
            try {
                val data: ByteArray
                data = if (iconUri.scheme == "data") {
                    val dataUrl = DataUrlSerializer().unserialize(iconUri.toString())
                    dataUrl.data
                } else {
                    IOUtils.toByteArray(iconUri.toURL().openStream())
                }
                val image = ImageIO.read(ByteArrayInputStream(data))
                if (image is Image) {
                    icon = IconUtil.createImageIcon(image as Image)
                }
            } catch (e: IOException) {
                LOG.warn("Failed to load icon for template: $template", e)
            }

        }
        return icon
    }

    class TemplateTreeModel(templates: List<Template>, private val onlyLatestVersion: JCheckBox) : AbstractTreeModel() {

        val c = compareBy(Template::getCategory).thenBy(Template::getName).thenByDescending(Template::getVersion)

        private val myRoot = DefaultMutableTreeNode(0)
        private val myTemplateMap = templates
                .sortedWith(c)
                .groupBy { it.category }

        private val myLatestTemplatesMap = myTemplateMap.mapValues { (_, templates) ->
            templates
                    .groupBy { it.name }
                    .mapValues { it.value.sortedByDescending { it.version }.first() }
                    .values
                    .toList()
        }

        override fun getChild(parent: Any?, index: Int): Any {
            return when (parent) {
                myRoot -> myTemplateMap.keys.toList()[index]
                is String -> getTemplateList(parent)[index]
                else -> throw RuntimeException()
            }
        }

        private fun getTemplateList(parent: String): List<Template> {
            return if (onlyLatestVersion.isSelected) {
                myLatestTemplatesMap[parent]?.toList() ?: emptyList()
            } else {
                myTemplateMap[parent]?.toList() ?: emptyList()
            }
        }

        override fun getRoot(): Any = myRoot

        override fun isLeaf(node: Any?): Boolean {
            return node is Template
        }

        override fun getChildCount(parent: Any?): Int {
            return when (parent) {
                myRoot -> myTemplateMap.keys.size
                is String -> getTemplateList(parent).size
                else -> 0
            }
        }

        override fun valueForPathChanged(path: TreePath?, newValue: Any?) {

        }

        override fun getIndexOfChild(parent: Any?, child: Any?): Int {
            return when (parent) {
                myRoot -> myTemplateMap.keys.toList().indexOf(child)
                is String -> getTemplateList(parent).indexOf(child)
                else -> -1
            }
        }
    }
}