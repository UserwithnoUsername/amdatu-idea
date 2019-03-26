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

package org.amdatu.idea.lang.bundledescriptor.completion;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.amdatu.idea.AmdatuIdeaConstants;
import org.amdatu.idea.AmdatuIdeaPlugin;
import org.amdatu.idea.lang.bundledescriptor.psi.BundleDescriptorTokenType;
import org.amdatu.idea.lang.bundledescriptor.psi.Header;
import org.jetbrains.annotations.NotNull;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static org.amdatu.idea.AmdatuIdeaConstants.BLUEPRINT_FEATURE;

public class BlueprintFeatureCompletionContributor extends CompletionContributor {

    private static final Logger LOG = Logger.getInstance(BlueprintFeatureCompletionContributor.class);
    private static final Pattern COMMA_PATTERN = Pattern.compile(",");

    public BlueprintFeatureCompletionContributor() {
        extend(CompletionType.BASIC,  getPlace(AmdatuIdeaConstants.BUILDFEATURES), new BlueprintFeatureCompletionProvider(
                        AmdatuIdeaConstants.BUILDFEATURES));
        extend(CompletionType.BASIC,  getPlace(AmdatuIdeaConstants.RUNFEATURES), new BlueprintFeatureCompletionProvider(
                        AmdatuIdeaConstants.RUNFEATURES));
    }

    private PsiElementPattern.Capture<PsiElement> getPlace(String name) {
        return psiElement(BundleDescriptorTokenType.HEADER_VALUE_PART)
                        .withSuperParent(2, psiElement(Header.class).withName(name));
    }

    // For debugging, this method is called just before creating the list of completions and is a nice place to put a break point
    @SuppressWarnings("EmptyMethod")
    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        super.fillCompletionVariants(parameters, result);
    }

    private static class BlueprintFeatureCompletionProvider extends CompletionProvider<CompletionParameters> {

        private final String instruction;

        private BlueprintFeatureCompletionProvider(String instruction) {
            this.instruction = instruction;
        }

        @Override
        public void addCompletions(@NotNull CompletionParameters parameters,
                        ProcessingContext context,
                        @NotNull CompletionResultSet resultSet) {

            Project project = parameters.getPosition().getProject();
            AmdatuIdeaPlugin amdatuIdeaPlugin = project.getComponent(AmdatuIdeaPlugin.class);

            ProjectFileIndex projectFileIndex = ProjectFileIndex.getInstance(project);
            Module module = projectFileIndex.getModuleForFile(parameters.getOriginalFile().getVirtualFile());
            if (module == null) {
                return;
            }

            try {
                // Get list of features from the current header that's being completed
                // (the bnd model doesn't know about unsaved added features)
                List<String> addedCurrentHeader = new ArrayList<>();
                PsiElement prevSibling = parameters.getPosition().getPrevSibling();
                while (prevSibling != null) {
                    if (BundleDescriptorTokenType.HEADER_VALUE_PART.equals(prevSibling.getNode().getElementType())) {
                        addedCurrentHeader.add(prevSibling.getText().trim());
                    }
                    prevSibling = prevSibling.getPrevSibling();
                }

                amdatuIdeaPlugin.withWorkspace(workspace -> {
                    aQute.bnd.build.Project bndProject = workspace.getProject(module.getName());
                    List<String> addedBndModel = COMMA_PATTERN.splitAsStream(bndProject.get(instruction + ".*", ""))
                            .map(String::trim)
                            .collect(Collectors.toList());

                    //
                    COMMA_PATTERN.splitAsStream(bndProject.getProperty(BLUEPRINT_FEATURE + ".*", ""))
                            .map(String::trim)
                            .filter(feature -> !addedBndModel.contains(feature))
                            .filter(feature -> !addedCurrentHeader.contains(feature))
                            .forEach(feature -> resultSet.addElement(LookupElementBuilder.create(feature)));
                    return null;
                });
            } catch (Exception e) {
                LOG.warn("Blueprint feature completion failed", e);
            }
        }
    }
}
