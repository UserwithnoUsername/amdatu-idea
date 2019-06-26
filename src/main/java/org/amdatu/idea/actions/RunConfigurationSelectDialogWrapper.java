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
    private JSpinner concurrencyCount;
    private JCheckBox reRunCheckBox;

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
        setTitle("Select Run Configurations");
    }

    private List<String> retrievePreviousSelection() {
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        String[] values = propertiesComponent.getValues(getSelectionPropertyStorageKey());
        return values != null ? Arrays.asList(values) : Collections.emptyList();
    }

    public void storeCurrentSelection(List<String> moduleNames) {
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        propertiesComponent.setValues(getSelectionPropertyStorageKey(), moduleNames.toArray(new String[0]));
    }

    private int retrieveConcurrencyCount() {
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        return propertiesComponent.getInt(getConcurrencyPropertyStorageKey(), 4);
    }

    private void storeConcurrencyCount(int count) {
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        propertiesComponent.setValue(getConcurrencyPropertyStorageKey(), count, 4);
    }

    private boolean retrieveMarkForRerun() {
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        return propertiesComponent.getBoolean(getReRunPropertyStorageKey(), false);
    }

    private void storeMarkForRerun(boolean mark) {
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        propertiesComponent.setValue(getReRunPropertyStorageKey(), mark, false);
    }

    private String getSelectionPropertyStorageKey() {
        return this.getClass().getName() + "_" + testType + "_selection";
    }

    private String getConcurrencyPropertyStorageKey() {
        return this.getClass().getName() + "_" + testType + "_concurrency";
    }

    private String getReRunPropertyStorageKey() {
        return this.getClass().getName() + "_" + testType + "_rerun";
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

        JPanel bottomPanel = new JPanel(new BorderLayout());

        JPanel optionsPanel = new JPanel(new GridLayout(2, 2));

        JLabel concurrencyLabel = new JLabel("Amount of tests to run in parallel");
        optionsPanel.add(concurrencyLabel);
        concurrencyCount = new JSpinner(new SpinnerNumberModel(retrieveConcurrencyCount(), 1, 24, 1));
        optionsPanel.add(concurrencyCount);
        JLabel reRunLabel = new JLabel("Automatically select failed tests for re-run");
        optionsPanel.add(reRunLabel);
        reRunCheckBox = new JBCheckBox(null, retrieveMarkForRerun());
        optionsPanel.add(reRunCheckBox);

        bottomPanel.add(optionsPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2));
        JButton invert = new JButton("Invert selection");
        buttonPanel.add(invert);
        JButton clear = new JButton("Clear selection");
        buttonPanel.add(clear);

        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        invert.addActionListener(e -> invertSelection());
        clear.addActionListener(e -> clearSelection());

        dialogPanel.add(bottomPanel, BorderLayout.SOUTH);

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

    private void clearSelection() {
        SwingUtilities.invokeLater(() -> {
            Enumeration<JCheckBox> enumeration = listModel.elements();
            int index = 0;
            while (enumeration.hasMoreElements()) {
                JCheckBox checkBox = enumeration.nextElement();
                checkBox.setSelected(false);
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
        storeConcurrencyCount(getConcurrencyCount());
        storeMarkForRerun(markFailedForReRun());
        return runConfigurations.stream()
                .filter(config -> checkedNames.contains(config.getName()))
                .collect(Collectors.toList());
    }

    boolean markFailedForReRun() {
        return reRunCheckBox.isSelected();
    }

    int getConcurrencyCount() {
        return (int) concurrencyCount.getValue();
    }

}
