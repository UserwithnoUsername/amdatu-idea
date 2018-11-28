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

import aQute.bnd.build.model.BndEditModel;
import aQute.bnd.properties.Document;
import com.intellij.compiler.impl.CompilerErrorTreeView;
import com.intellij.ide.errorTreeView.ErrorTreeElement;
import com.intellij.ide.errorTreeView.ErrorViewStructure;
import com.intellij.ide.errorTreeView.GroupingElement;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.amdatu.idea.AmdatuIdeaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FixBaseliningErrorsAction extends AnAction {

    @Override
    public void update(AnActionEvent e) {
        List<BaseliningBundleSuggestion> suggestions = getSuggestions();
        e.getPresentation().setEnabled(suggestions.size() > 0);
        e.getPresentation().setText("Fix " + suggestions.size() + " baselining errors");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project currentProject = e.getProject();
        AmdatuIdeaPlugin plugin = currentProject.getComponent(AmdatuIdeaPlugin.class);
        AmdatuIdeaPlugin.WorkspaceOperationToken token = plugin.startWorkspaceOperation();
        int count = 0;
        try {
            List<BaseliningBundleSuggestion> suggestions = getSuggestions();
            for (BaseliningBundleSuggestion suggestion : suggestions) {
                suggestion.apply();
                count++;
            }
        } finally {
            plugin.completeWorkspaceOperation(token);
            Messages.showMessageDialog(currentProject, "Baselining errors found and fixed: " + count, "Fix baselining errors", Messages.getInformationIcon());
        }
    }

    private List<BaseliningBundleSuggestion> getSuggestions() {
        List<BaseliningBundleSuggestion> suggestions = new ArrayList<>();
        ToolWindow activeToolWindow = ToolWindowManager.getActiveToolWindow();
        ContentManager contentManager = activeToolWindow.getContentManager();
        Content[] contents = contentManager.getContents();
        CompilerErrorTreeView view = (CompilerErrorTreeView) contents[0].getComponent();
        ErrorViewStructure errorViewStructure = view.getErrorViewStructure();
        Object root = errorViewStructure.getRootElement();
        ErrorTreeElement[] childElements = errorViewStructure.getChildElements(root);

        for (ErrorTreeElement childElement : childElements) {
            if (childElement instanceof GroupingElement) {
                GroupingElement groupingElement = (GroupingElement) childElement;
                for (ErrorTreeElement messageChild : errorViewStructure.getChildElements(groupingElement)) {
                    for (String text : messageChild.getText()) {
                        if (isBundleVersionMessage(text)) {
                            VirtualFile file = groupingElement.getFile();
                            BaseliningBundleSuggestion suggestion = BaseliningBundleSuggestion.parse(file, text);
                            suggestions.add(suggestion);
                        }
                    }
                }
            }
        }
        return suggestions;
    }

    public boolean isBundleVersionMessage(String message) {
        return message.contains("The bundle version") && message.contains("is too low");
    }

    static class BaseliningBundleSuggestion {
        VirtualFile source;
        String currentVersion;
        String suggestedVersion;

        static BaseliningBundleSuggestion parse(VirtualFile source, String message) {
            String suggestionMarker = "must be at least ";
            String suggestedVersion = message.substring(message.indexOf(suggestionMarker) + suggestionMarker.length());
            String currentMarker = "The bundle version (";
            int currentMarkerIndex = message.indexOf(currentMarker) + currentMarker.length();
            String currentVersion = message.substring(currentMarkerIndex, currentMarkerIndex + 5);
            return new BaseliningBundleSuggestion(source, currentVersion, suggestedVersion);
        }

        private BaseliningBundleSuggestion(VirtualFile source, String currentVersion, String suggestedVersion) {
            this.source = source;
            this.currentVersion = currentVersion;
            this.suggestedVersion = suggestedVersion;
        }

        @Override
        public String toString() {
            return "Bundle version " + source.getPath() + " " + currentVersion + " -> " + suggestedVersion;
        }

        public void apply() {
            try {
                byte[] contents = source.contentsToByteArray();
                Document bndDocument = new Document(new String(contents));
                BndEditModel bndEditModel = new BndEditModel();
                bndEditModel.loadFrom(bndDocument);
                bndEditModel.setBundleVersion(suggestedVersion);
                bndEditModel.saveChangesTo(bndDocument);
                source.setBinaryContent(bndDocument.get().getBytes());
            } catch (IOException e) {
                throw new RuntimeException("Could not edit bnd document " + source.getPath(), e);
            }
        }
    }
}
