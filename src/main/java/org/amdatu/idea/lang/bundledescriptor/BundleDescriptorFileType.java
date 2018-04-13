package org.amdatu.idea.lang.bundledescriptor;

import com.intellij.openapi.fileTypes.LanguageFileType;
import icons.OsmorcIdeaIcons;
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
        return "Bundle descriptor";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Bnd bundle descriptor";
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
        return OsmorcIdeaIcons.Bnd;
    }
}
