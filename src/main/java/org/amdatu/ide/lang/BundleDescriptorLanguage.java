package org.amdatu.ide.lang;

import com.intellij.lang.Language;
import org.jetbrains.lang.manifest.ManifestLanguage;

public class BundleDescriptorLanguage extends Language {
    public static final BundleDescriptorLanguage INSTANCE = new BundleDescriptorLanguage();

    public BundleDescriptorLanguage() {
        super(ManifestLanguage.INSTANCE, "BundleDescriptor");
    }
}