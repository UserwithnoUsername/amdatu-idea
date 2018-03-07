package org.amdatu.ide.preferences;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@State(name = "AmdatuIde", storages = @Storage("amdatu-ide.xml"))
public class AmdatuIdePreferences implements PersistentStateComponent<AmdatuIdePreferences> {

    private List<String> myTemplateRepositoryUrls = ContainerUtil.newArrayList();

    public static AmdatuIdePreferences getInstance() {
        return ServiceManager.getService(AmdatuIdePreferences.class);
    }

    @Nullable
    @Override
    public AmdatuIdePreferences getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull AmdatuIdePreferences state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public List<String> getTemplateRepositoryUrls() {
        return myTemplateRepositoryUrls;
    }

    public void setTemplateRepositoryUrls(List<String> templateRepositoryUrls) {
        myTemplateRepositoryUrls = templateRepositoryUrls;
    }
}
