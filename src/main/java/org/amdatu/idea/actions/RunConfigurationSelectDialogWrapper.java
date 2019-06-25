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

package org.amdatu.idea.actions;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class RunConfigurationSelectDialogWrapper extends DialogWrapper {

    private final String testType;
    private final List<RunnerAndConfigurationSettings> runConfigurations;
    private DefaultListModel<JCheckBox> listModel;

    RunConfigurationSelectDialogWrapper(String testType, List<RunnerAndConfigurationSettings> runConfigurations) {
        super(true);
        this.testType = testType;
        this.runConfigurations = runConfigurations;
        listModel = new DefaultListModel<>();
        List<String> previouslySelectedModuleNames = retrievePreviousSelection();
        boolean selectByDefault = previouslySelectedModuleNames.isEmpty();
        for (RunnerAndConfigurationSettings runConfiguration : runConfigurations) {
            JCheckBox checkBox = new JBCheckBox(runConfiguration.getName(), previouslySelectedModuleNames.contains(runConfiguration.getName()) || selectByDefault);
            listModel.addElement(checkBox);
        }
        init();
        setTitle("Select Run Cnfigurations");
    }

    private List<String> retrievePreviousSelection() {
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        String[] values = propertiesComponent.getValues(getPropertyStorageKey());
        return values != null ? Arrays.asList(values) : Collections.emptyList();
    }

    private void storeCurrentSelection(List<String> moduleNames) {
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        propertiesComponent.setValues(this.getClass().getName() + "_" + testType, moduleNames.toArray(new String[0]));
    }

    private String getPropertyStorageKey() {
        return this.getClass().getName() + "_" + testType;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel dialogPanel = new JPanel(new BorderLayout());

        JLabel label = new JLabel("Select which " + testType + "(s) to run");
        label.setPreferredSize(new Dimension(100, 25));
        dialogPanel.add(label, BorderLayout.NORTH);

        JBList list = new CheckBoxList(listModel);
        JBScrollPane scrollPane = new JBScrollPane(list);
        scrollPane.setPreferredSize(new Dimension(600, 500));
        dialogPanel.add(scrollPane, BorderLayout.CENTER);
        
        JButton invert = new JButton("Invert selection");
        dialogPanel.add(invert, BorderLayout.SOUTH);
        invert.addActionListener(e -> invertSelection());

        return dialogPanel;
    }

    private void invertSelection() {
        SwingUtilities.invokeLater(() -> {
            Enumeration<JCheckBox> enumeration = listModel.elements();
            int index = 0;
            while (enumeration.hasMoreElements()) {
                JCheckBox checkBox = enumeration.nextElement();
                checkBox.setSelected(!checkBox.isSelected());
                listModel.set(index, checkBox);
                index ++;
            }
        });
    }

    List<RunnerAndConfigurationSettings> getSelectedConfigurations() {
        List<String> checkedNames = new ArrayList<>();
        Enumeration<JCheckBox> enumeration = listModel.elements();
        while (enumeration.hasMoreElements()) {
            JCheckBox checkBox = enumeration.nextElement();
            if (checkBox.isSelected()) {
                checkedNames.add(checkBox.getText());
            }
        }
        storeCurrentSelection(checkedNames);
        return runConfigurations.stream()
                .filter(config -> checkedNames.contains(config.getName()))
                .collect(Collectors.toList());
    }

}
