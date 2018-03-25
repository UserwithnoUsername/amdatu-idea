package org.amdatu.ide.actions.index;

import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import java.io.File;
import java.util.List;

public class GenerateIndexForm {
    private JTextField myBaseDir;
    private JTextField myResourcePattern;
    private JList myResourceList;
    private JTextField myPrefix;
    private JRadioButton myPrettyPrintedXmlRadioButton;
    private JRadioButton myCompressedGZipRadioButton;
    private JPanel rootPane;

    private CollectionListModel<File> myCollectionListModel;

    public GenerateIndexForm(File baseDir) {
        myBaseDir.setText(baseDir.getAbsolutePath());
        myResourcePattern.setText("**.jar");

        // TODO: Don't support editing for now, just generate an index for the selected folder will do for most cases
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


