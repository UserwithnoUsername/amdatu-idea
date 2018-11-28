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
package org.amdatu.idea.ui.metatype

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.psi.JavaCodeFragmentFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.EditorTextField
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.LCFlags
import com.intellij.ui.layout.panel
import org.osgi.service.metatype.AttributeDefinition
import org.osgi.service.metatype.ObjectClassDefinition
import java.awt.Dimension
import java.util.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

typealias PropertyChangeListener = (id: String, value: List<Any>) -> Unit

val DEFAULT_CONTEXT_ATTRS: List<String> = Arrays.asList("basePackageDir", "basePackageName", "srcDir", "testSrcDir")

class MetaTypeEditPanelFactory(private val myProject: Project) {

    fun create(objectClassDefinition: ObjectClassDefinition, propertyChangeListener: PropertyChangeListener): JComponent {
        val attributeDefinitions = objectClassDefinition.getAttributeDefinitions(ObjectClassDefinition.ALL)
                .filter { attributeDefinition -> !DEFAULT_CONTEXT_ATTRS.contains(attributeDefinition.id) }

        return panel {
            row {
                for (attributeDefinition in attributeDefinitions) {
                    val name = attributeDefinition.name ?: attributeDefinition.id
                    row(name) {
                        input(attributeDefinition, propertyChangeListener)(growX, pushX)
                    }
                    if (attributeDefinition.description != null) {
                        row {
                            JTextArea("  ${attributeDefinition.description}")
                                    .apply {
                                        wrapStyleWord = true
                                        isEditable = false
                                        isFocusable = false
                                        rows = 1
                                        background = JPanel().background
                                        border = null
                                        val metrics = getFontMetrics(font)
                                        preferredSize = Dimension(400, metrics.height)
                                        maximumSize = Dimension(400, metrics.height)
                                    }(growX, pushX)
                        }
                    }
                }
            }
        }
    }

    private fun input(attributeDefinition: AttributeDefinition, propertyChangeListener: PropertyChangeListener): JComponent {
        return when {
            // TODO: We could use some custom template documentation mentioning this
            isClassInput(attributeDefinition) -> clazz(attributeDefinition, propertyChangeListener)
            isPackageInput(attributeDefinition) -> pkg(attributeDefinition, propertyChangeListener)
            attributeDefinition.optionValues?.isNotEmpty()
                    ?: false -> dropDown(attributeDefinition, propertyChangeListener)
            attributeDefinition.type == AttributeDefinition.STRING -> textField(attributeDefinition, propertyChangeListener)
            attributeDefinition.type == AttributeDefinition.BOOLEAN -> checkBox(attributeDefinition, propertyChangeListener)
            else -> textField(attributeDefinition, propertyChangeListener)
        }
    }

    private fun isClassInput(attributeDefinition: AttributeDefinition): Boolean {
        val optionValues = attributeDefinition.optionValues ?: return false
        return optionValues.size == 1
                && attributeDefinition.optionValues.firstOrNull() == "class"
    }

    private fun isPackageInput(attributeDefinition: AttributeDefinition): Boolean {
        val optionValues = attributeDefinition.optionValues ?: return false
        return optionValues.size == 1
                && attributeDefinition.optionValues.firstOrNull() == "package"
    }

    private fun textField(attributeDefinition: AttributeDefinition, propertyChangeListener: PropertyChangeListener): JComponent {
        val jTextField = JTextField()

        val defaultValue = attributeDefinition.defaultValue ?: emptyArray()
        if (defaultValue.isNotEmpty()) {
            propertyChangeListener(attributeDefinition.id, listOf<Any>(defaultValue.first()))
        }

        jTextField.apply {
            preferredSize = Dimension(2000, 25)
            text = defaultValue.firstOrNull()
            document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) {
                    handleChange()
                }

                override fun removeUpdate(e: DocumentEvent?) {
                    handleChange()
                }

                override fun changedUpdate(e: DocumentEvent?) {
                    handleChange()
                }

                fun handleChange() {
                    if (text.isNullOrBlank()) {
                        propertyChangeListener(attributeDefinition.id, emptyList())
                    } else {
                        propertyChangeListener(attributeDefinition.id, listOf(text))
                    }
                }
            })
        }
        return jTextField
    }

    private fun checkBox(attributeDefinition: AttributeDefinition, propertyChangeListener: PropertyChangeListener): JComponent {
        val defaultValue = attributeDefinition.defaultValue.firstOrNull()?.toBoolean() ?: false
        propertyChangeListener(attributeDefinition.id, listOf(defaultValue))

        return JCheckBox().apply {
            isSelected = defaultValue
            addChangeListener {
                propertyChangeListener(attributeDefinition.id, listOf(isSelected))
            }
        }
    }

    data class Option(val key: String, val value: String)

    private fun dropDown(attributeDefinition: AttributeDefinition, propertyChangeListener: PropertyChangeListener): JComponent {
        val defaultValue = attributeDefinition.defaultValue.firstOrNull()
        if (defaultValue != null) {
            propertyChangeListener(attributeDefinition.id, listOf(defaultValue))
        }

        val defaultComboBoxModel = DefaultComboBoxModel<Option>()
        var selected = 0
        for ((index, optionValue) in attributeDefinition.optionValues.withIndex()) {
            defaultComboBoxModel.addElement(Option(optionValue, attributeDefinition.optionLabels.getOrElse(index, { optionValue })))
            if (optionValue == defaultValue) {
                selected = index
            }
        }

        return ComboBox<Option>(defaultComboBoxModel).apply {
            selectedIndex = selected
            addActionListener {
                propertyChangeListener(attributeDefinition.id, listOf((selectedItem as Option).key))
            }
        }
    }

    private fun clazz(attributeDefinition: AttributeDefinition, propertyChangeListener: PropertyChangeListener): JComponent {
        return fragment(attributeDefinition, propertyChangeListener, true)
    }

    private fun pkg(attributeDefinition: AttributeDefinition, propertyChangeListener: PropertyChangeListener): JComponent {
        return fragment(attributeDefinition, propertyChangeListener, false)
    }

    private fun fragment(attributeDefinition: AttributeDefinition, propertyChangeListener: PropertyChangeListener, isClassesAccepted: Boolean): JComponent {
        val codeFragment = JavaCodeFragmentFactory.getInstance(myProject).createReferenceCodeFragment("", null, true, isClassesAccepted)
        val document = PsiDocumentManager.getInstance(myProject).getDocument(codeFragment)
        return EditorTextField(document, myProject, JavaFileType.INSTANCE).apply {
            addDocumentListener(object : com.intellij.openapi.editor.event.DocumentListener {
                override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
                    val text = document?.text
                    if (text.isNullOrBlank()) {
                        propertyChangeListener(attributeDefinition.id, emptyList())
                    } else {
                        propertyChangeListener(attributeDefinition.id, listOf(text!!))
                    }
                }
            })
        }
    }
}
