package org.amdatu.idea.actions;

import com.intellij.execution.*;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.content.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractRunTestsAction extends AmdatuIdeaAction {

    private final String testType;
    private final String runConfigurationProducerClassName;

    public AbstractRunTestsAction(String testType, String runConfigurationProducerClassName) {
        this.testType = testType;
        this.runConfigurationProducerClassName = runConfigurationProducerClassName;
    }

    protected void runConfigurations(final Executor executor, final Queue<RunConfiguration> runConfigurations) {
        if (runConfigurations.isEmpty()) {
            return;
        }

        final RunConfiguration runConfiguration = runConfigurations.poll();
        final Project project = runConfiguration.getProject();
        final RunnerAndConfigurationSettings configuration = new RunnerAndConfigurationSettingsImpl(
                RunManagerImpl.getInstanceImpl(project), runConfiguration, false);

        boolean started = false;
        try {
            final ProgramRunner runner = RunnerRegistry.getInstance().getRunner(executor.getId(), runConfiguration);
            if (runner == null) return;
            if (!checkRunConfiguration(executor, project, configuration)) return;

            runTriggers(executor, configuration);
            ExecutionEnvironment executionEnvironment = new ExecutionEnvironment(executor, runner, configuration, project);

            runner.execute(executionEnvironment, new ProgramRunner.Callback() {
                @SuppressWarnings("ConstantConditions")
                @Override
                public void processStarted(final RunContentDescriptor descriptor) {
                    if (descriptor == null) {
                        // start next configuration..
                        return;
                    }

                    final ProcessHandler processHandler = descriptor.getProcessHandler();
                    if (processHandler != null) {
                        processHandler.addProcessListener(new ProcessAdapter() {
                            @SuppressWarnings("ConstantConditions")
                            @Override
                            public void startNotified(ProcessEvent processEvent) {
                                Content content = descriptor.getAttachedContent();
                                if (content != null) {
                                    content.setIcon(descriptor.getIcon());

                                    // mark all current console tab as pinned
                                    content.setPinned(true);

                                    // mark running process tab with *
                                    content.setDisplayName(descriptor.getDisplayName() + "*");
                                }
                            }

                            @Override public void processTerminated(final ProcessEvent processEvent) {
                                onTermination(processEvent, true);
                            }

                            @Override public void processWillTerminate(ProcessEvent processEvent, boolean willBeDestroyed) {
                                onTermination(processEvent, false);
                            }

                            private void onTermination(final ProcessEvent processEvent, final boolean terminated) {
                                if (descriptor.getAttachedContent() == null) {
                                    return;
                                }

                                ApplicationManager.getApplication().invokeLater(new Runnable() {
                                    @Override
                                    public void run() {
                                        final Content content = descriptor.getAttachedContent();
                                        if (content == null) return;

                                        // exit code is 0 if the process completed successfully
                                        final boolean completedSuccessfully = (terminated && processEvent.getExitCode() == 0);

                                        if (completedSuccessfully) {
                                            // close the tab for the success process and exit - nothing else could be done
                                            if (content.getManager() != null) {
                                                content.getManager().removeContent(content, false);
                                                return;
                                            }
                                        }

                                        if (completedSuccessfully) {
                                            // un-pin the console tab if re-use is allowed and process completed successfully,
                                            // so the tab could be re-used for other processes
                                            content.setPinned(false);
                                        }

                                        // remove the * used to identify running process
                                        content.setDisplayName(descriptor.getDisplayName());

                                        // add the alert icon in case if process existed with non-0 status
                                        if (processEvent.getExitCode() != 0) {
                                            ApplicationManager.getApplication().invokeLater(new Runnable() {
                                                @Override
                                                public void run() {
                                                    content.setIcon(LayeredIcon.create(content.getIcon(), AllIcons.Nodes.TabAlert));
                                                }
                                            });
                                        }
                                    }
                                });
                                if (terminated) {
                                    runConfigurations(executor, runConfigurations);
                                }

                            }
                        });
                    }
                }
            });
            started = true;
        } catch (ExecutionException e) {
            ExecutionUtil.handleExecutionError(project, executor.getToolWindowId(), configuration.getConfiguration(), e);
        } finally {
            if (!started) {
                // failed to start current, means the chain is broken
                runConfigurations(executor, runConfigurations);
            }
        }
    }

    protected void runTriggers(Executor executor, RunnerAndConfigurationSettings configuration) {
        final ConfigurationType configurationType = configuration.getType();
        if (configurationType != null) {
            UsageTrigger.trigger("execute." + ConvertUsagesUtil.ensureProperKey(configurationType.getId()) + "." + executor.getId());
        }
    }

    protected boolean checkRunConfiguration(Executor executor, Project project, RunnerAndConfigurationSettings configuration) {
        ExecutionTarget target = ExecutionTargetManager.getActiveTarget(project);

        if (!ExecutionTargetManager.canRun(configuration, target)) {
            ExecutionUtil.handleExecutionError(
                    project, executor.getToolWindowId(), configuration.getConfiguration(),
                    new ExecutionException(StringUtil.escapeXml("Cannot run '" + configuration.getName() + "' on '" + target.getDisplayName() + "'")));
            return false;
        }

        return true;
    }

    abstract Stream<VirtualFile> getRunConfigurationTargets(Project project);

    private RunConfiguration createRunConfiguration(VirtualFile sourceRoot, Project project, RunConfigurationProducer runConfigurationProducer, int index) {
        RunManager runManager = RunManager.getInstance(project);
        String runConfigurationName = testType + " for " + getModuleName(sourceRoot);
        RunnerAndConfigurationSettings configurationAndSettings = runManager.findConfigurationByName(runConfigurationName);
        if (configurationAndSettings != null) {
            runManager.removeConfiguration(configurationAndSettings);
        }

        configurationAndSettings = runManager.createConfiguration(runConfigurationName, runConfigurationProducer.getConfigurationFactory());

        Element element = new Element("configuration");
        RunConfiguration configuration = configurationAndSettings.getConfiguration();
        configuration.writeExternal(element);

        customizeConfiguration(element, sourceRoot);

        // disable make
        if (index > 0) {
            Element methodElement = new Element("method");
            methodElement.setAttribute("v", "2");
            element.addContent(methodElement);
        }

        configuration.readExternal(element);

        if (index > 0) {
            configuration.setBeforeRunTasks(Collections.emptyList());
        }

        runManager.addConfiguration(configurationAndSettings);
        return configuration;
    }

    abstract void customizeConfiguration(Element element, VirtualFile runConfigurationTarget);

    abstract String getModuleName(VirtualFile runConfigurationTarget);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        RunConfigurationProducer<?> runConfigurationProducer = RunConfigurationProducer.getProducers(project).stream()
                .filter(producer -> producer.getClass().getName().equals(runConfigurationProducerClassName))
                .findFirst().get();
        AtomicInteger configurationIndex = new AtomicInteger();
        List<RunConfiguration> runConfigurations = getRunConfigurationTargets(project)
                .map(root -> createRunConfiguration(root, project, runConfigurationProducer, configurationIndex.getAndIncrement()))
                .collect(Collectors.toList());

        RunConfigurationSelectDialogWrapper dialog = new RunConfigurationSelectDialogWrapper(runConfigurations);
        if (dialog.showAndGet()) {
            List<RunConfiguration> selectedConfigurations = dialog.getSelectedConfigurations();

            Executor executor = DefaultRunExecutor.getRunExecutorInstance();
            runConfigurations(executor, new ConcurrentLinkedQueue<>(selectedConfigurations));
        }
    }
}
