package org.amdatu.ide.jps;

import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.DoneSomethingNotification;
import org.jetbrains.jps.incremental.messages.ProgressMessage;

import com.intellij.openapi.util.io.FileUtil;

import aQute.bnd.build.Project;
import aQute.bnd.build.ProjectBuilder;
import aQute.bnd.osgi.Builder;
import aQute.bnd.osgi.Jar;
import aQute.service.reporter.Report;

public class AmdatuIdeTargetBuilder extends TargetBuilder<BuildRootDescriptor, AmdatuIdeModuleBasedBuildTarget> {

    public static final String ID = "AmdatuIde";

    public AmdatuIdeTargetBuilder() {
        super(Collections.singletonList(AmdatuIdeModuleBasedTargetType.INSTANCE));
    }

    @NotNull
    @Override
    public String getPresentableName() {
        return ID;
    }

    @Override
    public void build(@NotNull AmdatuIdeModuleBasedBuildTarget target,
                      @NotNull DirtyFilesHolder<BuildRootDescriptor, AmdatuIdeModuleBasedBuildTarget> holder,
                      @NotNull BuildOutputConsumer outputConsumer,
                      @NotNull CompileContext context) throws ProjectBuildException, IOException {
        if (JavaBuilderUtil.isForcedRecompilationAllJavaModules(context) ||
                holder.hasDirtyFiles() || holder.hasRemovedFiles()) {
            doBuild(target, context);
        }
    }

    public void doBuild(@NotNull AmdatuIdeModuleBasedBuildTarget target, @NotNull CompileContext context) throws IOException {

        context.processMessage(new ProgressMessage("Running bnd build for: " + target.getModule().getName()));

        try {
            for (File outputRoot : target.getOutputRoots(context)) {
                if (!FileUtil.delete(outputRoot)) {
                    throw new RuntimeException("Failed to delete: '" + outputRoot + "'.");
                }
            }

            Project project = target.getBndWorkspace().getProject(target.getModule().getName());
            try (ProjectBuilder projectBuilder = project.getBuilder(null)) {
                for (Builder builder : projectBuilder.getSubBuilders()) {
                    String bsn = builder.getBsn();
                    String version = builder.getVersion();
                    context.processMessage(new ProgressMessage(format("Building bundle: %s [%s]", bsn, version)));

                    Jar build = builder.build();
                    build.write(project.getOutputFile(bsn, version));

                    builder.getWarnings().stream()
                            .map(message -> toCompilerMessage(BuildMessage.Kind.WARNING, message, builder))
                            .forEach(context::processMessage);

                    builder.getErrors().stream()
                            .map(message -> toCompilerMessage(BuildMessage.Kind.ERROR, message, builder))
                            .forEach(context::processMessage);
                }
            }

        } catch (Exception e) {
            context.processMessage(new CompilerMessage(AmdatuIdeTargetBuilder.ID, e));
            return;
        }

        context.processMessage(DoneSomethingNotification.INSTANCE);
    }

    private CompilerMessage toCompilerMessage(BuildMessage.Kind kind, String message, Builder builder) {
        Report.Location location = builder.getLocation(message);
        String path = null;
        int line = -1;

        if (location != null) {
            path = location.file;
            line = location.line;
        }
        return new CompilerMessage(AmdatuIdeTargetBuilder.ID, kind, message, path, -1, -1, -1, line, -1);
    }


}
