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
import java.io.File;
import java.util.List;

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
            protected void customizeCellRenderer(@NotNull JList list, File value, int index, boolean selected,  boolean hasFocus) {
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
}


