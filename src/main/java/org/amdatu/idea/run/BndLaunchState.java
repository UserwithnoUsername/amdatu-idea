// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package org.amdatu.idea.run;

import aQute.bnd.build.ProjectLauncher;
import aQute.bnd.build.Run;
import aQute.bnd.build.Workspace;
import com.intellij.debugger.ui.HotSwapUI;
import com.intellij.debugger.ui.HotSwapVetoableListener;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaCommandLineState;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerTopics;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;

import org.amdatu.idea.AmdatuIdeaNotificationService;
import org.amdatu.idea.AmdatuIdeaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Map;

import static com.intellij.openapi.util.Pair.pair;
import static org.amdatu.idea.i18n.OsmorcBundle.message;

public class BndLaunchState extends JavaCommandLineState implements CompilationStatusListener, HotSwapVetoableListener {
    private static final Logger LOG = Logger.getInstance(BndLaunchState.class);
    private static final Pair<Long, Long> MISSING_BUNDLE = pair(0L, 0L);

    private static final Map<String, NotificationGroup> ourNotificationGroups = ContainerUtil.newHashMap();

    private final BndRunConfigurationBase.Launch myConfiguration;
    private final Project myProject;
    private final NotificationGroup myNotifications;
    private ProjectLauncher myLauncher;
    private final Map<String, Pair<Long, Long>> myBundleStamps = ContainerUtil.newHashMap();

    BndLaunchState(@NotNull ExecutionEnvironment environment,
                   @NotNull BndRunConfigurationBase.Launch configuration) {
        super(environment);

        myConfiguration = configuration;
        myProject = myConfiguration.getProject();

        String toolWindowId = environment.getExecutor().getToolWindowId();
        NotificationGroup notificationGroup = ourNotificationGroups.get(toolWindowId);
        if (notificationGroup == null) {
            String name = BndRunConfigurationType.getInstance().getDisplayName() + " (" + toolWindowId + ")";
            notificationGroup = NotificationGroup.toolWindowGroup(name, toolWindowId);
            ourNotificationGroups.put(toolWindowId, notificationGroup);
        }
        myNotifications = notificationGroup;
    }

    @Override
    protected JavaParameters createJavaParameters() throws ExecutionException {
        String bndRunFile = myConfiguration.getOptions().getBndRunFile();
        File runFile = bndRunFile == null ? null : new File(bndRunFile);
        if (runFile == null || !runFile.isFile()) {
            throw new CantRunException(message("bnd.run.configuration.invalid", bndRunFile));
        }

        try {
            AmdatuIdeaPlugin amdatuIdeaPlugin = myProject.getComponent(AmdatuIdeaPlugin.class);
            Workspace workspace = amdatuIdeaPlugin.getWorkspace();

            Run run = Run.createRun(workspace, runFile);
            if (DefaultDebugExecutor.EXECUTOR_ID.equals(getEnvironment().getExecutor().getId())) {
                BndLaunchUtil.addBootDelegation(run, "com.intellij.rt.debugger.agent");
            }

            ProjectLauncher launcher = run.getProjectLauncher();
            launcher.prepare();

            if (myProject.getComponent(AmdatuIdeaNotificationService.class).report(launcher.getProject(), false)) {
                throw new CantRunException(
                                message("bnd.test.cannot.run", "project has errors"));
            }

            myLauncher = launcher;
        }
        catch (CantRunException e) {
            throw e;
        }
        catch (Exception e) {
            LOG.info(e);
            throw new CantRunException(message("bnd.run.configuration.cannot.run", runFile, BndLaunchUtil.message(e)));
        }

        bundlesChanged();

        return BndLaunchUtil.createJavaParameters(myConfiguration, myLauncher);
    }

    @NotNull
    @Override
    protected OSProcessHandler startProcess() throws ExecutionException {
        OSProcessHandler handler = super.startProcess();

        MessageBusConnection connection = myProject.getMessageBus().connect();
        connection.subscribe(CompilerTopics.COMPILATION_STATUS, this);

        HotSwapUI hotSwapManager = HotSwapUI.getInstance(myProject);
        hotSwapManager.addListener(this);

        handler.addProcessListener(new ProcessAdapter() {
            @Override
            public void processTerminated(@NotNull ProcessEvent event) {
                connection.disconnect();
                hotSwapManager.removeListener(BndLaunchState.this);
                myLauncher.cleanup();
            }
        });

        return handler;
    }

    @Override
    public void compilationFinished(boolean aborted, int errors, int warnings, CompileContext context) {
        if (!aborted && errors == 0 && bundlesChanged()) {
            try {
                myLauncher.update();
                myNotifications.createNotification(message("bnd.run.reloaded.text"), NotificationType.INFORMATION)
                                .notify(myProject);
            }
            catch (Exception e) {
                LOG.error(e);
            }
        }
    }

    private boolean bundlesChanged() {
        boolean changed = false;

        for (String bundle : myLauncher.getRunBundles()) {
            FileAttributes attributes = FileSystemUtil.getAttributes(bundle);
            Pair<Long, Long> current =
                            attributes != null ? pair(attributes.lastModified, attributes.length) : MISSING_BUNDLE;
            if (!current.equals(myBundleStamps.get(bundle))) {
                myBundleStamps.put(bundle, current);
                changed = true;
            }
        }

        return changed;
    }

    @Override
    public void fileGenerated(String outputRoot, String relativePath) {
    }

    @Override
    public boolean shouldHotSwap(CompileContext context) {
        return false;
    }
}