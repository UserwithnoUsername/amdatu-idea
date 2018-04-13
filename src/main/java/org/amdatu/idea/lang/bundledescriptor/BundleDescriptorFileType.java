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
