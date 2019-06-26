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

package org.amdatu.idea.actions;

import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.*;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.content.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractRunTestsAction extends AmdatuIdeaAction {

    static final String OPTION = "option";
    static final String MODULE = "module";
    static final String NAME = "name";
    static final String VALUE = "value";

    private final String testType;
    private final String runConfigurationProducerClassName;

    AbstractRunTestsAction(String testType, String runConfigurationProducerClassName) {
        this.testType = testType;
        this.runConfigurationProducerClassName = runConfigurationProducerClassName;
    }

    private void runConfigurations(final Executor executor, final Queue<RunnerAndConfigurationSettings> runConfigurations) {
        if (runConfigurations.isEmpty()) {
            return;
        }

        final RunnerAndConfigurationSettings configurationAndSettings = runConfigurations.poll();
        boolean lastTest = runConfigurations.isEmpty();
        final Project project = configurationAndSettings.getConfiguration().getProject();

        boolean started = false;
        try {
            final ProgramRunner runner = ProgramRunnerUtil.getRunner(executor.getId(), configurationAndSettings);
            if (runner == null) return;
            if (!checkRunConfiguration(executor, project, configurationAndSettings)) return;

            ExecutionEnvironment executionEnvironment = new ExecutionEnvironment(executor, runner, configurationAndSettings, project);

            runner.execute(executionEnvironment, descriptor -> {
                if (descriptor == null) {
                    // start next configuration..
                    return;
                }

                final ProcessHandler processHandler = descriptor.getProcessHandler();
                if (processHandler != null) {
                    processHandler.addProcessListener(new ProcessAdapter() {
                        @Override
                        public void startNotified(@NotNull ProcessEvent processEvent) {
                            Content content = descriptor.getAttachedContent();
                            if (content != null) {
                                content.setIcon(descriptor.getIcon());

                                // mark all current console tab as pinned
                                content.setPinned(true);

                                // mark running process tab with *
                                content.setDisplayName(descriptor.getDisplayName() + "*");
                            }
                        }

                        @Override public void processTerminated(@NotNull final ProcessEvent processEvent) {
                            onTermination(processEvent, true);
                        }

                        @Override public void processWillTerminate(@NotNull ProcessEvent processEvent, boolean willBeDestroyed) {
                            onTermination(processEvent, false);
                        }

                        private void onTermination(final ProcessEvent processEvent, final boolean terminated) {
                            if (descriptor.getAttachedContent() == null) {
                                return;
                            }

                            ApplicationManager.getApplication().invokeLater(() -> {
                                final Content content = descriptor.getAttachedContent();
                                if (content == null) return;

                                // exit code is 0 if the process completed successfully
                                final boolean completedSuccessfully = (terminated && processEvent.getExitCode() == 0);

                                if (completedSuccessfully && content.getManager() != null && !lastTest) {
                                    content.getManager().removeContent(content, false);
                                    return;
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
                                    ApplicationManager.getApplication().invokeLater(() -> content.setIcon(LayeredIcon.create(content.getIcon(), AllIcons.Nodes.TabAlert)));
                                }
                            });
                            if (terminated) {
                                runConfigurations(executor, runConfigurations);
                            }

                        }
                    });
                }
            });
            started = true;
        } catch (ExecutionException e) {
            ExecutionUtil.handleExecutionError(project, executor.getToolWindowId(), configurationAndSettings.getConfiguration(), e);
        } finally {
            if (!started) {
                // failed to start current, means the chain is broken
                runConfigurations(executor, runConfigurations);
            }
        }
    }

    private boolean checkRunConfiguration(Executor executor, Project project, RunnerAndConfigurationSettings configuration) {
        ExecutionTarget target = ExecutionTargetManager.getActiveTarget(project);

        if (!ExecutionTargetManager.canRun(configuration, target)) {
            ExecutionUtil.handleExecutionError(
                    project, executor.getToolWindowId(), configuration.getConfiguration(),
                    new ExecutionException(StringUtil.escapeXml("Cannot run '" + configuration.getName() + "' on '" + target.getDisplayName() + "'")));
            return false;
        }

        return true;
    }

    private RunnerAndConfigurationSettings createRunConfiguration(Module module, Project project, RunConfigurationProducer runConfigurationProducer) {
        RunManager runManager = RunManager.getInstance(project);
        String runConfigurationName = module.getName();
        RunnerAndConfigurationSettings existingConfigurationAndSettings = runManager.findConfigurationByName(runConfigurationName);
        if (existingConfigurationAndSettings != null) {
            runManager.removeConfiguration(existingConfigurationAndSettings);
        }

        RunnerAndConfigurationSettings configurationAndSettings = runManager.createConfiguration(runConfigurationName, runConfigurationProducer.getConfigurationFactory());

        Element element = new Element("configuration");
        RunConfiguration configuration = configurationAndSettings.getConfiguration();
        configuration.writeExternal(element);

        customizeConfiguration(element, module);
        configuration.readExternal(element);
        configuration.setBeforeRunTasks(Collections.emptyList());

        runManager.addConfiguration(configurationAndSettings);
        return configurationAndSettings;
    }

    public RunnerAndConfigurationSettings addBuildBeforeRunTask(RunnerAndConfigurationSettings runnerAndConfigurationSettings) {
        RunManager runManager = RunManager.getInstance(runnerAndConfigurationSettings.getConfiguration().getProject());
        RunnerAndConfigurationSettings existingConfigurationAndSettings = runManager.findConfigurationByName(runnerAndConfigurationSettings.getName());
        if (existingConfigurationAndSettings != null) {
            runManager.removeConfiguration(existingConfigurationAndSettings);
        }

        Element element = new Element("configuration");
        RunConfiguration configuration = runnerAndConfigurationSettings.getConfiguration();
        configuration.writeExternal(element);

        // disable make
        Element methodElement = new Element("method");
        methodElement.setAttribute("v", "2");
        element.addContent(methodElement);

        configuration.readExternal(element);

        configuration.setBeforeRunTasks(Collections.singletonList(new CompileStepBeforeRun.MakeBeforeRunTask()));

        runManager.addConfiguration(runnerAndConfigurationSettings);
        return runnerAndConfigurationSettings;
    }

    abstract void customizeConfiguration(Element element, Module module);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        RunConfigurationProducer<?> runConfigurationProducer = RunConfigurationProducer.getProducers(project).stream()
                .filter(producer -> producer.getClass().getName().equals(runConfigurationProducerClassName))
                .findFirst().orElse(null);

        if (runConfigurationProducer == null) {
            return;
        }

        List<RunnerAndConfigurationSettings> runConfigurations = getRunConfigurationTargets(project)
                .map(root -> createRunConfiguration(root, project, runConfigurationProducer))
                .sorted(new TestRunConfigurationComparator())
                .collect(Collectors.toList());

        RunConfigurationSelectDialogWrapper dialog = new RunConfigurationSelectDialogWrapper(testType, runConfigurations);
        if (dialog.showAndGet()) {
            List<RunnerAndConfigurationSettings> selectedConfigurations = dialog.getSelectedConfigurations();
            selectedConfigurations.sort(new TestRunConfigurationComparator());

            Executor executor = DefaultRunExecutor.getRunExecutorInstance();
            if (!selectedConfigurations.isEmpty()) {
                selectedConfigurations.set(0, addBuildBeforeRunTask(selectedConfigurations.get(0)));
            }
            runConfigurations(executor, new ConcurrentLinkedQueue<>(selectedConfigurations));
        }
    }

    private Stream<Module> getRunConfigurationTargets(Project project) {
        return Arrays.stream(ProjectRootManager.getInstance(project).getContentRootsFromAllModules())
                .map(root -> ModuleUtil.findModuleForFile(root, project))
                .filter(this::isTestModule);
    }

    abstract boolean isTestModule(Module module);

    static class TestRunConfigurationComparator implements Comparator<RunnerAndConfigurationSettings> {

        @Override
        public int compare(RunnerAndConfigurationSettings o1, RunnerAndConfigurationSettings o2) {
            return o1.getName().compareTo(o2.getName());
        }
    }
}
