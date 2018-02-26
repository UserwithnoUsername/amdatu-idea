package org.amdatu.ide;

import java.io.File;

import org.jetbrains.annotations.NotNull;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;

import aQute.bnd.build.Workspace;

public class AmdatuIdePluginImpl implements AmdatuIdePlugin {

    private final Object workspaceLock = new Object();
    private Workspace workspace;

    @Override
    public boolean isBndWorkspace(@NotNull Project project) {
        File projectBase = new File(project.getBasePath());
        File cnfDir = new File(projectBase, Workspace.CNFDIR);
        return cnfDir.isDirectory() && getWorkspace(project) != null;
    }

    @Override
    public Workspace getWorkspace(@NotNull Project project) {
        synchronized (workspaceLock) {
            if (workspace == null) {
                try {
                    workspace = new Workspace(new File(project.getBasePath()));

                    if (workspace.getErrors() != null && !workspace.getErrors().isEmpty()) {
                        for (String warning : workspace.getWarnings()) {
                            Notifications.Bus.notify(new Notification("amdatu-ide", "Warning", "message: '" + warning + "'", NotificationType.WARNING));
                        }
                        for (String error : workspace.getErrors()) {
                            Notifications.Bus.notify(new Notification("amdatu-ide", "Error", "message: '" + error + "'", NotificationType.ERROR));
                        }
                    } else {
                        Notifications.Bus.notify(new Notification("amdatu-ide", "Success", "Successfully created bnd workspace'", NotificationType.INFORMATION));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return workspace;
        }
    }
}
