package org.amdatu.idea;

import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.messages.Topic;

import java.util.List;

public interface WorkspaceOperationListener {
    Topic<WorkspaceOperationListener> WORKSPACE_OPERATION_TOPIC = new Topic<>("WorkspaceOperation", WorkspaceOperationListener.class);
    void afterWorkspaceOperation(List<VFileEvent> events);
}
