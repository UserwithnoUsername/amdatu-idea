package org.amdatu.ide.lang;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

public class BundleDescriptorFileTypeFactory extends FileTypeFactory {
    public final static LanguageFileType BUNDLE_DESCRIPTOR = new BundleDescriptorFileType();

    @Override
    public void createFileTypes(@NotNull FileTypeConsumer consumer) {
        consumer.consume(BUNDLE_DESCRIPTOR, "bnd");
    }
}