package org.amdatu.ide;

import com.intellij.util.messages.Topic;

public interface WorkspaceRefreshedNotifier {

    Topic<WorkspaceRefreshedNotifier> WORKSPACE_REFRESHED =
                    Topic.create("Workspace refreshed", WorkspaceRefreshedNotifier.class);

    void workpaceRefreshed();
}
