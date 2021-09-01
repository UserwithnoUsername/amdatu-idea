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

package org.amdatu.idea.preferences;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@State(name = "AmdatuIdeaPreferences", storages = @Storage("amdatu-idea.xml"))
public class AmdatuIdeaPreferences implements PersistentStateComponent<AmdatuIdeaPreferences> {

    private List<String> myTemplateRepositoryUrls = new ArrayList<>();

    public AmdatuIdeaPreferences() {
        myTemplateRepositoryUrls.add("https://raw.githubusercontent.com/bndtools/bundle-hub/master/index.xml.gz");
    }

    public static AmdatuIdeaPreferences getInstance() {
        return ApplicationManager.getApplication().getService(AmdatuIdeaPreferences.class);
    }

    @Nullable
    @Override
    public AmdatuIdeaPreferences getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull AmdatuIdeaPreferences state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public List<String> getTemplateRepositoryUrls() {
        return myTemplateRepositoryUrls;
    }

    public void setTemplateRepositoryUrls(List<String> templateRepositoryUrls) {
        myTemplateRepositoryUrls = templateRepositoryUrls;
    }
}
