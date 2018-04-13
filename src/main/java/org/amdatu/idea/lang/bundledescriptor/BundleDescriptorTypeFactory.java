/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.amdatu.idea.lang.bundledescriptor;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BundleDescriptorTypeFactory extends FileTypeFactory {

    public final static LanguageFileType BUNDLE_DESCRIPTOR_FILE_TYPE = new BundleDescriptorFileType();
    private static final String EXTENSIONS = Stream.of("bnd", "bndrun")
                    .collect(Collectors.joining(FileTypeConsumer.EXTENSION_DELIMITER));

    @Override
    public void createFileTypes(@NotNull FileTypeConsumer consumer) {
        consumer.consume(BUNDLE_DESCRIPTOR_FILE_TYPE, EXTENSIONS);
    }
}
