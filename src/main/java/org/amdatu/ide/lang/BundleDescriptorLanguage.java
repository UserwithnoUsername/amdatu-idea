package org.amdatu.ide.lang;

import org.jetbrains.lang.manifest.ManifestLanguage;

import com.intellij.lang.Language;

public class BundleDescriptorLanguage extends Language {
  public static final BundleDescriptorLanguage INSTANCE = new BundleDescriptorLanguage();

  public BundleDescriptorLanguage() {
    super(ManifestLanguage.INSTANCE, "BundleDescriptor");
  }
}