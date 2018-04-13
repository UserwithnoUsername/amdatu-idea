package org.amdatu.idea;

import com.intellij.util.messages.Topic;

public interface WorkspaceRefreshedNotifier {

    Topic<WorkspaceRefreshedNotifier> WORKSPACE_REFRESHED =
                    Topic.create("Workspace refreshed", WorkspaceRefreshedNotifier.class);

    void workpaceRefreshed();
}
