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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiPackage;
import org.amdatu.idea.*;
import org.amdatu.idea.inspections.quickfix.UpdateBundleVersion;
import org.amdatu.idea.inspections.quickfix.UpdatePackageInfoJavaPackageVersion;
import org.amdatu.idea.inspections.quickfix.UpdatePackageInfoPackageVersion;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class FixBaseliningErrorsAction extends AmdatuIdeaAction {

    public static final String FIX_BASELINING_ERRORS = "Fix baselining errors";

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        if (e.getProject() != null) {
            List<BaseliningSuggestion> suggestions = getSuggestions(e.getProject());
            e.getPresentation().setEnabled(!suggestions.isEmpty());
            e.getPresentation().setText("Fix " + suggestions.size() + " baselining errors");
        }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project currentProject = e.getProject();
        if (currentProject == null) {
            return;
        }

        AmdatuIdeaPlugin plugin = currentProject.getService(AmdatuIdeaPlugin.class);
        boolean enableModuleUpdater = plugin.pauseWorkspaceModelSync(FIX_BASELINING_ERRORS);
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
            if (enableModuleUpdater) {
                plugin.resumeWorkspaceModelSync(FIX_BASELINING_ERRORS);
            }
            Messages.showMessageDialog(currentProject, "Baselining  " + suggestions.size() + " errors found and " + count + " fixed", FIX_BASELINING_ERRORS, Messages.getInformationIcon());
        }
    }

    @NotNull
    private List<BaseliningSuggestion> getSuggestions(Project project) {
        BaseliningErrorService baseliningErrorService = project.getService(BaseliningErrorService.class);
        return baseliningErrorService.getAllSuggestions();
    }

}
