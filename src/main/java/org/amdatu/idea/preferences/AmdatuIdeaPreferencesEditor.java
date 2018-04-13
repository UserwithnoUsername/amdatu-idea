package org.amdatu.idea.preferences;

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
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

public class AmdatuIdeaPreferencesEditor implements SearchableConfigurable {
    private JList templateRepoList;
    private JButton addTemplateRepoButton;
    private JTextField newTemplateRepo;
    private JPanel root;
    private JPanel templateRepoListPanel;
    private CollectionListModel<String> myTemplateRepoListModel;

    private boolean modified = false;

    public AmdatuIdeaPreferencesEditor() {
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
        return "amdatu.idea.preferences";
    }

    @Nls
    @Override
    public String getDisplayName() {
        return "Amdatu";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        reset();
        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(templateRepoList);
        templateRepoListPanel.add(decorator.createPanel());

        myTemplateRepoListModel.addListDataListener(new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent e) {
                modified = true;
            }

            @Override
            public void intervalRemoved(ListDataEvent e) {
                modified = true;
            }

            @Override
            public void contentsChanged(ListDataEvent e) {
                modified = true;
            }
        });

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
        AmdatuIdeaPreferences preferences = AmdatuIdeaPreferences.getInstance();
        myTemplateRepoListModel.removeAll();
        myTemplateRepoListModel.addAll(0, preferences.getTemplateRepositoryUrls());
        this.modified = false;
    }

    @Override
    public void apply() {
        AmdatuIdeaPreferences preferences = AmdatuIdeaPreferences.getInstance();
        preferences.setTemplateRepositoryUrls(myTemplateRepoListModel.getItems());
        this.modified = false;
    }

}