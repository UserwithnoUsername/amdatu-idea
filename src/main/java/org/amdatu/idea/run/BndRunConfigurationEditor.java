// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package org.amdatu.idea.run;

import com.intellij.execution.ui.CommonProgramParametersPanel;
import com.intellij.execution.ui.DefaultJreSelector;
import com.intellij.execution.ui.JrePathEditor;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.amdatu.idea.i18n.OsmorcBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JPanel;

import java.awt.*;

import static org.amdatu.idea.AmdatuIdeaConstants.BND_EXT;
import static org.amdatu.idea.AmdatuIdeaConstants.BND_RUN_EXT;

public class BndRunConfigurationEditor extends SettingsEditor<BndRunConfigurationBase> {
    private JPanel myPanel;
    private TextFieldWithBrowseButton myChooser;
    private JrePathEditor myJrePathEditor;
    private CommonProgramParametersPanel myCommonProgramParametersPanel;

    public BndRunConfigurationEditor(Project project) {
        myJrePathEditor.setDefaultJreSelector(DefaultJreSelector.projectSdk(project));
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory
                .createSingleFileNoJarsDescriptor()
                .withFileFilter(file -> {
                    String ext = file.getExtension();
                    return BND_EXT.equals(ext) || BND_RUN_EXT.equals(ext);
                });
        myChooser.addBrowseFolderListener(OsmorcBundle.message("bnd.run.file.chooser.title"), null, project,
                descriptor);
    }

    @NotNull
    @Override
    protected JComponent createEditor() {
        return myPanel;
    }

    @Override
    protected void resetEditorFrom(@NotNull BndRunConfigurationBase configuration) {
        BndRunConfigurationOptions options = configuration.getOptions();
        myChooser.setText(options.getBndRunFile());
        myJrePathEditor.setPathOrName(options.getAlternativeJrePath(), options.getUseAlternativeJre());
        myCommonProgramParametersPanel.reset(configuration);
    }

    @Override
    protected void applyEditorTo(@NotNull BndRunConfigurationBase configuration) {
        BndRunConfigurationOptions options = configuration.getOptions();
        options.setBndRunFile(myChooser.getText());
        options.setUseAlternativeJre(myJrePathEditor.isAlternativeJreSelected());
        options.setAlternativeJrePath(myJrePathEditor.getJrePathOrName());
        myCommonProgramParametersPanel.applyTo(configuration);
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        myPanel = new JPanel();
        myPanel.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
        final JBLabel jBLabel1 = new JBLabel();
        jBLabel1.setText("Bnd run descriptor:");
        myPanel.add(jBLabel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myChooser = new TextFieldWithBrowseButton();
        myPanel.add(myChooser, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myJrePathEditor = new JrePathEditor();
        myPanel.add(myJrePathEditor, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        myPanel.add(spacer1, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        myCommonProgramParametersPanel = new CommonProgramParametersPanel();
        myPanel.add(myCommonProgramParametersPanel, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return myPanel;
    }
}
