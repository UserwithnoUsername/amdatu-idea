package org.amdatu.ide.lang;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.lang.manifest.parser.ManifestParserDefinition;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;

public class BundleDescriptorParserDefinition extends ManifestParserDefinition {

    public static final IFileElementType FILE = new IFileElementType("BundleDescriptorFile", BundleDescriptorLanguage.INSTANCE);

    @NotNull
    @Override
    public Lexer createLexer(Project project) {
        return new BundleDescriptorLexer();
    }

    @Override
    public IFileElementType getFileNodeType() {
        return FILE;
    }

    @Override
    public PsiFile createFile(FileViewProvider viewProvider) {
        return new BundleDescriptorFileImpl(viewProvider);
    }
}
