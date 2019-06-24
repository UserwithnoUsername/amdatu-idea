package org.amdatu.idea.actions;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;

public class RunConfigurationSelectDialogWrapper extends DialogWrapper {

    private final List<RunConfiguration> runConfigurations;
    private DefaultListModel<JCheckBox> listModel;
    private JBList list;

    public RunConfigurationSelectDialogWrapper(List<RunConfiguration> runConfigurations) {
        super(true);
        this.runConfigurations = runConfigurations;
        listModel = new DefaultListModel<>();
        for (RunConfiguration runConfiguration : runConfigurations) {
            JCheckBox checkBox = new JBCheckBox(runConfiguration.getName(), true);
            listModel.addElement(checkBox);
        }
        init();
        setTitle("Select run configurations");
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel dialogPanel = new JPanel(new BorderLayout());

        JLabel label = new JLabel("Select which tests to run");
        label.setPreferredSize(new Dimension(100, 25));
        dialogPanel.add(label, BorderLayout.NORTH);

        list = new CheckBoxList(listModel);
        JBScrollPane scrollPane = new JBScrollPane(list);
        scrollPane.setPreferredSize(new Dimension(600, 500));
        dialogPanel.add(scrollPane, BorderLayout.CENTER);
        
        JButton invert = new JButton("Invert selection");
        dialogPanel.add(invert, BorderLayout.SOUTH);
        invert.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                invertSelection();
            }
        });

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

    public List<RunConfiguration> getSelectedConfigurations() {
        List<String> checkedNames = new ArrayList<>();
        Enumeration<JCheckBox> enumeration = listModel.elements();
        while (enumeration.hasMoreElements()) {
            JCheckBox checkBox = enumeration.nextElement();
            if (checkBox.isSelected()) {
                checkedNames.add(checkBox.getText());
            }
        }
        return runConfigurations.stream()
                .filter(config -> checkedNames.contains(config.getName()))
                .collect(Collectors.toList());
    }

}
