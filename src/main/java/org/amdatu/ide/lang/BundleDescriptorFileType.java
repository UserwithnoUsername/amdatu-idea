package org.amdatu.ide.lang;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

public class BundleDescriptorFileType extends LanguageFileType {

    public BundleDescriptorFileType() {
        super(BundleDescriptorLanguage.INSTANCE);
    }

    @NotNull
    @NonNls
    @Override
    public String getName() {
        return "BundleDescriptor";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Bundle descriptor";
    }

    @NotNull
    @NonNls
    @Override
    public String getDefaultExtension() {
        return "bnd";
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return AllIcons.FileTypes.Custom;
    }
}
