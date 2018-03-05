package org.amdatu.ide.templating;

import aQute.bnd.build.Workspace;
import aQute.bnd.osgi.resource.CapReqBuilder;
import aQute.bnd.repository.osgi.OSGiRepository;
import aQute.bnd.service.RepositoryPlugin;
import com.intellij.openapi.project.Project;
import org.amdatu.ide.AmdatuIdePlugin;
import org.bndtools.templating.Template;
import org.bndtools.templating.engine.mustache.MustacheTemplateEngine;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.service.repository.Repository;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RepoTemplateLoader {

    private static final String NS_TEMPLATE = "org.bndtools.template";

    // TODO This should not be initialized here
    private MustacheTemplateEngine engine = new MustacheTemplateEngine();

    public List<Template> findTemplates(Project project, String templateType) {

        Workspace workspace = null;
        if (project != null) {
            AmdatuIdePlugin amdatuIdePlugin = project.getComponent(AmdatuIdePlugin.class);
            workspace = amdatuIdePlugin.getWorkspace();
        }

        try {
            if (workspace == null) {
                workspace = Workspace.createDefaultWorkspace();
            }

            List<Repository> repositories = workspace.getPlugins(Repository.class);

            OSGiRepository osGiRepository = new OSGiRepository();
            osGiRepository.setRegistry(workspace);
            osGiRepository.setReporter(workspace);
            Map<String, String> map = new HashMap<>();
            map.put("name", "temp");
            map.put("locations", "http://amdatu-repo.s3.amazonaws.com/amdatu-blueprint/snapshot/repo/index.xml.gz");
            osGiRepository.setProperties(map);
            repositories.add(osGiRepository);

            return repositories.stream()
                    .flatMap(repo -> findTemplates(repo, templateType))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException();
        }
    }

    private Stream<Template> findTemplates(Repository repository, String templateType) {
        String filterStr = String.format("(%s=%s)", NS_TEMPLATE, templateType);
        final Requirement requirement = new CapReqBuilder(NS_TEMPLATE)
                .addDirective(Namespace.REQUIREMENT_FILTER_DIRECTIVE, filterStr)
                .buildSyntheticRequirement();

        return repository.findProviders(Collections.singleton(requirement))
                .getOrDefault(requirement, Collections.emptyList())
                .stream()
                .map(capability -> new CapabilityBasedTemplate(capability, engine, (RepositoryPlugin) repository));

    }
}
