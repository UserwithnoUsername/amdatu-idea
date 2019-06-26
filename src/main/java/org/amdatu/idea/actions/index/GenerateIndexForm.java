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

package org.amdatu.idea.actions.index;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.util.List;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.NotNull;

import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;

public class GenerateIndexForm {
    private JTextField myBaseDir;
    private JTextField myResourcePattern;
    private JList<File> myResourceList;
    private JTextField myPrefix;
    private JRadioButton myPrettyPrintedXmlRadioButton;
    private JRadioButton myCompressedGZipRadioButton;
    private JPanel rootPane;

    private CollectionListModel<File> myCollectionListModel;

    public GenerateIndexForm(File baseDir) {
        myBaseDir.setText(baseDir.getAbsolutePath());
        myResourcePattern.setText("**.jar");

        myBaseDir.setEditable(false);
        myResourcePattern.setEditable(false);

        myPrefix.setText("index");

        myCollectionListModel = new CollectionListModel<>();
        myResourceList.setModel(myCollectionListModel);
        myResourceList.setCellRenderer(new ColoredListCellRenderer<File>() {
            @Override
            protected void customizeCellRenderer(@NotNull JList list, File value, int index, boolean selected, boolean hasFocus) {
                append(baseDir.toPath().relativize(value.toPath()).toString());
            }
        });
    }

    public void updateFileList(List<File> fileList) {
        myCollectionListModel.replaceAll(fileList);
    }

    public JPanel getRootPane() {
        return rootPane;
    }

    public String getBaseDir() {
        return myBaseDir.getText();
    }

    public String getResourcePatten() {
        return myResourcePattern.getText();
    }

    public List<File> getResources() {
        return myCollectionListModel.getItems();
    }

    public String getIndexFileName() {
        return myPrefix.getText() + (isCompressed() ? ".xml.gz" : ".xml");
    }

    public boolean isCompressed() {
        return myCompressedGZipRadioButton.isSelected();
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
        rootPane = new JPanel();
        rootPane.setLayout(new GridLayoutManager(6, 2, new Insets(0, 0, 0, 0), -1, -1));
        rootPane.setPreferredSize(new Dimension(450, 300));
        final JLabel label1 = new JLabel();
        label1.setText("Base directory");
        rootPane.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myBaseDir = new JTextField();
        rootPane.add(myBaseDir, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Resource pattern");
        rootPane.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myResourcePattern = new JTextField();
        rootPane.add(myResourcePattern, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("Resources");
        rootPane.add(label3, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        scrollPane1.setOpaque(true);
        rootPane.add(scrollPane1, new GridConstraints(2, 1, 2, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        scrollPane1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, new Color(-4473925)));
        myResourceList = new JList();
        final DefaultListModel defaultListModel1 = new DefaultListModel();
        myResourceList.setModel(defaultListModel1);
        scrollPane1.setViewportView(myResourceList);
        final JLabel label4 = new JLabel();
        label4.setText("Output");
        label4.setVerticalAlignment(1);
        rootPane.add(label4, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
        rootPane.add(panel1, new GridConstraints(4, 1, 2, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("Prefix");
        label5.setVerticalAlignment(0);
        panel1.add(label5, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myPrefix = new JTextField();
        panel1.add(myPrefix, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        myPrettyPrintedXmlRadioButton = new JRadioButton();
        myPrettyPrintedXmlRadioButton.setActionCommand("Pretty printed ");
        myPrettyPrintedXmlRadioButton.setSelected(true);
        myPrettyPrintedXmlRadioButton.setText("Pretty printed (.xml)");
        panel1.add(myPrettyPrintedXmlRadioButton, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myCompressedGZipRadioButton = new JRadioButton();
        myCompressedGZipRadioButton.setText("Compressed (.xml.gz)");
        panel1.add(myCompressedGZipRadioButton, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        rootPane.add(spacer1, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        rootPane.add(spacer2, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        ButtonGroup buttonGroup;
        buttonGroup = new ButtonGroup();
        buttonGroup.add(myPrettyPrintedXmlRadioButton);
        buttonGroup.add(myCompressedGZipRadioButton);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return rootPane;
    }
}


