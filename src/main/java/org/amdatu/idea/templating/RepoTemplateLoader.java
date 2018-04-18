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

package org.amdatu.idea.templating;

import aQute.bnd.build.Workspace;
import aQute.bnd.osgi.resource.CapReqBuilder;
import aQute.bnd.repository.osgi.OSGiRepository;
import aQute.bnd.service.RepositoryPlugin;
import com.intellij.openapi.project.Project;
import org.amdatu.idea.AmdatuIdeaPlugin;
import org.amdatu.idea.preferences.AmdatuIdeaPreferences;
import org.bndtools.templating.Template;
import org.bndtools.templating.TemplateEngine;
import org.bndtools.templating.engine.mustache.MustacheTemplateEngine;
import org.bndtools.templating.engine.st.StringTemplateEngine;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.service.repository.Repository;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RepoTemplateLoader {

    private static final String NS_TEMPLATE = "org.bndtools.template";

    // TODO This should not be initialized here
    private MustacheTemplateEngine engine = new MustacheTemplateEngine();
    private StringTemplateEngine myStringTemplateEngine = new StringTemplateEngine();

    public List<Template> findTemplates(Project project, String templateType) {

        Workspace workspace = null;
        if (project != null) {
            AmdatuIdeaPlugin amdatuIdeaPlugin = project.getComponent(AmdatuIdeaPlugin.class);
            workspace = amdatuIdeaPlugin.getWorkspace();
        }

        try {
            if (workspace == null) {
                workspace = Workspace.createDefaultWorkspace();
            }

            List<Repository> repositories = workspace.getPlugins(Repository.class);

            createPreferencesLocationsRepo(workspace).map(repositories::add);

            return repositories.stream()
                    .flatMap(repo -> findTemplates(repo, templateType))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<OSGiRepository> createPreferencesLocationsRepo(Workspace workspace) throws Exception {
        AmdatuIdeaPreferences preferences = AmdatuIdeaPreferences.getInstance();
        List<String> templateRepositoryUrls = preferences.getTemplateRepositoryUrls();

        if (templateRepositoryUrls.isEmpty()) {
            return Optional.empty();
        } else {
            OSGiRepository osGiRepository = new OSGiRepository();
            osGiRepository.setRegistry(workspace);
            osGiRepository.setReporter(workspace);
            Map<String, String> map = new HashMap<>();
            map.put("name", "Amdatu preferences template repos");


            String locations = templateRepositoryUrls.stream().collect(Collectors.joining(","));
            map.put("locations", locations);
            osGiRepository.setProperties(map);
            return Optional.of(osGiRepository);
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
                .map(capability -> {
                    String engineId = (String) capability.getAttributes().get("engine");
                    TemplateEngine engine;
                    if ("mustache".equals(engineId)) {
                        engine = this.engine;
                    } else {
                        engine = myStringTemplateEngine;
                    }
                    return new CapabilityBasedTemplate(capability, engine, (RepositoryPlugin) repository);
                });

    }
}
