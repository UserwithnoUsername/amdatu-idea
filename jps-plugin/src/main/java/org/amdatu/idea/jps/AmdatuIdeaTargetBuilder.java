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

package org.amdatu.idea.jps;

import aQute.bnd.build.Project;
import aQute.bnd.osgi.Processor;
import aQute.service.reporter.Report;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.DoneSomethingNotification;
import org.jetbrains.jps.incremental.messages.ProgressMessage;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static java.lang.String.format;

public class AmdatuIdeaTargetBuilder extends TargetBuilder<BuildRootDescriptor, AmdatuIdeaModuleBasedBuildTarget> {

    private static final String ID = "AmdatuIdea";

    AmdatuIdeaTargetBuilder() {
        super(Collections.singletonList(AmdatuIdeaModuleBasedTargetType.INSTANCE));
    }

    @NotNull
    @Override
    public String getPresentableName() {
        return ID;
    }

    @Override
    public void build(@NotNull AmdatuIdeaModuleBasedBuildTarget target,
                      @NotNull DirtyFilesHolder<BuildRootDescriptor, AmdatuIdeaModuleBasedBuildTarget> holder,
                      @NotNull BuildOutputConsumer outputConsumer,
                      @NotNull CompileContext context) throws IOException {
        if (JavaBuilderUtil.isForcedRecompilationAllJavaModules(context) ||
                holder.hasDirtyFiles() || holder.hasRemovedFiles()) {
            doBuild(target, context);
        }
    }

    private void doBuild(@NotNull AmdatuIdeaModuleBasedBuildTarget target, @NotNull CompileContext context) {

        context.processMessage(new ProgressMessage("Running bnd build for: " + target.getModule().getName()));

        try {
            for (File outputRoot : target.getOutputRoots(context)) {
                if (!FileUtil.delete(outputRoot)) {
                    throw new RuntimeException("Failed to delete: '" + outputRoot + "'.");
                }
            }


            try (Project project = target.getBndWorkspace().getProject(target.getModule().getName())) {
                context.processMessage(new ProgressMessage(format("Building project: %s", project.getName())));
                project.build();

                project.getWarnings().stream()
                        .map(message -> toCompilerMessage(BuildMessage.Kind.WARNING, message, project))
                        .forEach(context::processMessage);

                project.getErrors().stream()
                        .map(message -> toCompilerMessage(BuildMessage.Kind.ERROR, message, project))
                        .forEach(context::processMessage);
            }
        } catch (Exception e) {
            context.processMessage(CompilerMessage.createInternalBuilderError(AmdatuIdeaTargetBuilder.ID, e));
            return;
        }

        context.processMessage(DoneSomethingNotification.INSTANCE);
    }

    private CompilerMessage toCompilerMessage(BuildMessage.Kind kind, String message, Processor processor) {
        Report.Location location = processor.getLocation(message);
        String path = null;
        int line = -1;

        if (location != null) {
            path = location.file;
            line = location.line + 1; // Line numbers in bnd are 0 based
        }

        if (path == null && processor.getPropertiesFile() != null){
            path = processor.getPropertiesFile().getAbsolutePath();
        }
        return new CompilerMessage(AmdatuIdeaTargetBuilder.ID, kind, message, path, -1, -1, -1, line, -1);
    }

}
