package org.amdatu.ide.lang;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public class BundleDescriptorFileImpl extends PsiFileBase {
    public BundleDescriptorFileImpl(FileViewProvider viewProvider) {
        super(viewProvider, BundleDescriptorLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public FileType getFileType() {
        return BundleDescriptorFileTypeFactory.BUNDLE_DESCRIPTOR;
    }

    @Override
    public String toString() {
        return "BundleDescriptorFileImpl:" + getName();
    }
}
