package org.amdatu.ide.lang;

import org.jetbrains.annotations.NotNull;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;

public class BundleDescriptorFileImpl extends PsiFileBase  {
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
