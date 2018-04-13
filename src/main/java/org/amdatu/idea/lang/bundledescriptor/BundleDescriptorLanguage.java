package org.amdatu.idea.lang.bundledescriptor;

import com.intellij.lang.Language;

public class BundleDescriptorLanguage extends Language {
    public static final BundleDescriptorLanguage
                    INSTANCE = new BundleDescriptorLanguage();

    public BundleDescriptorLanguage() {
        super("BundleDescriptor");
    }
}
