package org.amdatu.idea.preferences;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@State(name = "AmdatuIdeaPreferences", storages = @Storage("amdatu-idea.xml"))
public class AmdatuIdeaPreferences implements PersistentStateComponent<AmdatuIdeaPreferences> {

    private List<String> myTemplateRepositoryUrls = ContainerUtil.newArrayList();

    public AmdatuIdeaPreferences() {
        myTemplateRepositoryUrls.add("http://amdatu-repo.s3.amazonaws.com/amdatu-blueprint/snapshot/repo/index.xml.gz");
        myTemplateRepositoryUrls.add("https://raw.githubusercontent.com/bndtools/bundle-hub/master/index.xml.gz");
    }

    public static AmdatuIdeaPreferences getInstance() {
        return ServiceManager.getService(AmdatuIdeaPreferences.class);
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
