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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.amdatu.idea.AmdatuIdeaPlugin;
import org.amdatu.idea.BaseliningBundleSuggestion;
import org.amdatu.idea.BaseliningErrorService;
import org.amdatu.idea.BaseliningPackageSuggestion;
import org.amdatu.idea.BaseliningSuggestion;
import org.amdatu.idea.inspections.quickfix.UpdateBundleVersion;
import org.amdatu.idea.inspections.quickfix.UpdatePackageInfoJavaPackageVersion;
import org.amdatu.idea.inspections.quickfix.UpdatePackageInfoPackageVersion;

import com.intellij.compiler.impl.CompilerErrorTreeView;
import com.intellij.ide.errorTreeView.ErrorTreeElement;
import com.intellij.ide.errorTreeView.ErrorViewStructure;
import com.intellij.ide.errorTreeView.GroupingElement;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiPackage;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.MessageView;

public class FixBaseliningErrorsAction extends AnAction {

    @Override
    public void update(AnActionEvent e) {
        List<BaseliningSuggestion> suggestions = getSuggestions(e.getProject());
        e.getPresentation().setEnabled(suggestions.size() > 0);
        e.getPresentation().setText("Fix " + suggestions.size() + " baselining errors");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project currentProject = e.getProject();
        AmdatuIdeaPlugin plugin = currentProject.getComponent(AmdatuIdeaPlugin.class);
        AmdatuIdeaPlugin.WorkspaceOperationToken token = plugin.startWorkspaceOperation();
        AtomicInteger count = new AtomicInteger();
        List<BaseliningSuggestion> suggestions = getSuggestions(e.getProject());
        try {
            ApplicationManager.getApplication().runWriteAction(() -> {
                for (BaseliningSuggestion suggestion : suggestions) {
                    if (suggestion instanceof BaseliningBundleSuggestion) {
                        new UpdateBundleVersion((BaseliningBundleSuggestion) suggestion).apply();
                        count.incrementAndGet();
                    } else if (suggestion instanceof BaseliningPackageSuggestion) {
                        BaseliningPackageSuggestion packageSuggestion = (BaseliningPackageSuggestion) suggestion;
                        String name = packageSuggestion.getSource().getName();
                        if (name.equals(PsiPackage.PACKAGE_INFO_FILE)) {
                            new UpdatePackageInfoJavaPackageVersion(packageSuggestion).apply();
                            count.incrementAndGet();
                        } else if (name.equals("packageinfo")) {
                            new UpdatePackageInfoPackageVersion(packageSuggestion).apply();
                            count.incrementAndGet();
                        }
                    }

                }
            });
        } finally {
            plugin.completeWorkspaceOperation(token);
            Messages.showMessageDialog(currentProject, "Baselining  " + suggestions.size() + " errors found and " + count + " fixed", "Fix baselining errors", Messages.getInformationIcon());
        }
    }

    private List<BaseliningSuggestion> getSuggestions(Project project) {

        BaseliningErrorService baseliningErrorService = project.getComponent(BaseliningErrorService.class);

        List<BaseliningSuggestion> suggestions = new ArrayList<>();
        MessageView messageView = MessageView.SERVICE.getInstance(project);
        ContentManager contentManager = messageView.getContentManager();
        Content[] contents = contentManager.getContents();
        if (contents.length > 0 && contents[0].getComponent() instanceof CompilerErrorTreeView) {
            CompilerErrorTreeView view = (CompilerErrorTreeView) contents[0].getComponent();
            ErrorViewStructure errorViewStructure = view.getErrorViewStructure();
            Object root = errorViewStructure.getRootElement();
            ErrorTreeElement[] childElements = errorViewStructure.getChildElements(root);

            for (ErrorTreeElement childElement : childElements) {
                if (childElement instanceof GroupingElement) {
                    GroupingElement groupingElement = (GroupingElement) childElement;
                    for (ErrorTreeElement messageChild : errorViewStructure.getChildElements(groupingElement)) {
                        for (String text : messageChild.getText()) {
                            VirtualFile file = groupingElement.getFile();
                            if (isBundleVersionMessage(text)) {
                                BaseliningSuggestion suggestion = baseliningErrorService.parseBundleVersionSuggestion(file, text);
                                suggestions.add(suggestion);
                            } else if (isPackageVersionMessage(text)) {
                                org.amdatu.idea.BaseliningPackageSuggestion suggestion = baseliningErrorService.parsePackageVersionSuggestion(file, text);
                                suggestions.add(suggestion);
                            }
                        }
                    }
                }
            }
        }
        return suggestions;
    }

    private boolean isPackageVersionMessage(String message) {
        return message.contains("Baseline mismatch for package");
    }

    private boolean isBundleVersionMessage(String message) {
        return message.contains("The bundle version") && message.contains("is too low");
    }

}
