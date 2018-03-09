package org.amdatu.ide.lang.bundledescriptor;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.stream.Collectors;

public class BundleDescriptorTypeFactory extends FileTypeFactory {

    public final static LanguageFileType BUNDLE_DESCRIPTOR_FILE_TYPE = new BundleDescriptorFileType();
    public static final String EXTENSIONS = Arrays.asList("bnd", "bndrun")
                    .stream()
                    .collect(Collectors.joining(FileTypeConsumer.EXTENSION_DELIMITER));

    @Override
    public void createFileTypes(@NotNull FileTypeConsumer consumer) {
        consumer.consume(BUNDLE_DESCRIPTOR_FILE_TYPE, EXTENSIONS);
    }
}
