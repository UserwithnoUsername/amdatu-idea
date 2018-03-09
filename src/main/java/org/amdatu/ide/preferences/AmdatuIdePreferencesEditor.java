package org.amdatu.ide.preferences;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ToolbarDecorator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class AmdatuIdePreferencesEditor implements SearchableConfigurable {
    private JList templateRepoList;
    private JButton addTemplateRepoButton;
    private JTextField newTemplateRepo;
    private JPanel root;
    private JPanel templateRepoListPanel;
    private CollectionListModel<String> myTemplateRepoListModel;

    private boolean modified = false;

    public AmdatuIdePreferencesEditor() {
        myTemplateRepoListModel = new CollectionListModel<>();
        addTemplateRepoButton.addActionListener(e -> {
            if (newTemplateRepo.getText().trim().length() > 0) {
                myTemplateRepoListModel.add(newTemplateRepo.getText().trim());
                newTemplateRepo.setText("");
                modified = true;
            }
        });
    }

    @NotNull
    @Override
    public String getId() {
        return "amdatu.ide.preferences";
    }

    @Nls
    @Override
    public String getDisplayName() {
        return "Amdatu IDE";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        reset();
        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(templateRepoList);
        templateRepoListPanel.add(decorator.createPanel());

        //noinspection unchecked
        templateRepoList.setModel(myTemplateRepoListModel);
        return root;
    }

    @Override
    public boolean isModified() {
        return modified;
    }

    @Override
    public void reset() {
        AmdatuIdePreferences preferences = AmdatuIdePreferences.getInstance();
        myTemplateRepoListModel.removeAll();
        myTemplateRepoListModel.addAll(0, preferences.getTemplateRepositoryUrls());
        this.modified = false;
    }

    @Override
    public void apply() {
        AmdatuIdePreferences preferences = AmdatuIdePreferences.getInstance();
        preferences.setTemplateRepositoryUrls(myTemplateRepoListModel.getItems());
        this.modified = false;
    }

}
