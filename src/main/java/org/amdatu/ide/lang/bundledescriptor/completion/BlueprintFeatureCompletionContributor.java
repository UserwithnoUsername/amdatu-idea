package org.amdatu.ide.lang.bundledescriptor.completion;

import aQute.bnd.build.Workspace;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.amdatu.ide.AmdatuIdePlugin;
import org.amdatu.ide.lang.bundledescriptor.header.HeaderParserRepository;
import org.amdatu.ide.lang.bundledescriptor.psi.BundleDescriptorTokenType;
import org.amdatu.ide.lang.bundledescriptor.psi.Header;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public class BlueprintFeatureCompletionContributor extends CompletionContributor {

    public static final String BUILDFEATURES = "-buildfeatures";
    public static final String RUNFEATURES = "-runfeatures";

    public BlueprintFeatureCompletionContributor() {
        extend(CompletionType.BASIC,  getPlace(BUILDFEATURES), new BlueprintFeatureCompletionProvider(BUILDFEATURES));
        extend(CompletionType.BASIC,  getPlace(RUNFEATURES), new BlueprintFeatureCompletionProvider(RUNFEATURES));
    }

    private PsiElementPattern.Capture<PsiElement> getPlace(String name) {
        return psiElement(BundleDescriptorTokenType.HEADER_VALUE_PART)
                        .withSuperParent(2, psiElement(Header.class).withName(name));
    }

    // For debugging, this method is called just before creating the list of completions and is a nice place to put a break point
    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        super.fillCompletionVariants(parameters, result);
    }

    public static String findPrefix(@NotNull CompletionParameters parameters) {
        final PsiElement position = parameters.getPosition();
        final int offset = parameters.getOffset();
        TextRange range = position.getTextRange();
        assert range.containsOffset(offset) : position + "; " + offset + " not in " + range;
        //noinspection deprecation

        String substr = position.getText().substring(0, offset - position.getTextRange().getStartOffset());
        if (substr.length() == 0 || Character.isWhitespace(substr.charAt(substr.length() - 1)))
            return "";

        substr = substr.trim();

        return substr.trim();
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
            AmdatuIdePlugin amdatuIdePlugin = project.getComponent(AmdatuIdePlugin.class);

            ProjectFileIndex projectFileIndex = ProjectFileIndex.getInstance(project);
            Module module = projectFileIndex.getModuleForFile(
                            parameters.getOriginalFile().getVirtualFile());


            // TODO: Get list of installed features from the bnd workspace
            List<String> installed = Arrays.asList("base", "blobstores", "config", "email", "mongodb", "scheduling", "security", "shell", "template", "testing", "validator", "web");
            List<String> availableToAdd = new ArrayList<>(installed);

            try {
                // Remove features that are already added from completion options
                Workspace workspace = amdatuIdePlugin.getWorkspace();
                aQute.bnd.build.Project bndProject = workspace.getProject(module.getName());

                List<String> added = Arrays.stream(bndProject.get(instruction + ".*", "")
                                .split(","))
                                .map(String::trim)
                                .collect(Collectors.toList());
                availableToAdd.removeAll(added);
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            PsiElement prevSibling = parameters.getPosition().getPrevSibling();


            // Remove features that are added to the header we're completing, these are potentially
            // not yet saved so not yet known in the bnd project.
            while (prevSibling != null) {
                if (BundleDescriptorTokenType.HEADER_VALUE_PART.equals(prevSibling.getNode().getElementType())) {
                    availableToAdd.remove(prevSibling.getText().trim());
                }
                prevSibling = prevSibling.getPrevSibling();
            }

            for (String header : availableToAdd) {
                resultSet.addElement(LookupElementBuilder.create(header));
            }
        }
    }
}
