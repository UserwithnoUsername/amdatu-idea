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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.amdatu.idea.AmdatuIdeaPlugin;
import org.amdatu.idea.preferences.AmdatuIdeaPreferences;
import org.bndtools.templating.Template;
import org.bndtools.templating.TemplateEngine;
import org.bndtools.templating.engine.mustache.MustacheTemplateEngine;
import org.bndtools.templating.engine.st.StringTemplateEngine;
import org.jetbrains.annotations.NotNull;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.service.repository.Repository;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RepoTemplateLoader {

    private static final Logger LOG = Logger.getInstance(RepoTemplateLoader.class);
    private static final String NS_TEMPLATE = "org.bndtools.template";

    // TODO This should not be initialized here
    private MustacheTemplateEngine engine = new MustacheTemplateEngine();
    private StringTemplateEngine myStringTemplateEngine = new StringTemplateEngine();

    public List<Template> findTemplates(Project project, String templateType) {
        if (project != null) {
            AmdatuIdeaPlugin amdatuIdeaPlugin = project.getService(AmdatuIdeaPlugin.class);
            if (amdatuIdeaPlugin.isBndWorkspace()) {
                return amdatuIdeaPlugin.withWorkspace(workspace -> doLoadTemplates(templateType, workspace));
            }
        }
        try {
            return doLoadTemplates(templateType, Workspace.createDefaultWorkspace());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    private List<Template> doLoadTemplates(String templateType, Workspace workspace) {
        try {

            List<Repository> repositories = workspace.getPlugins(Repository.class);

            String blueprintVersion;
            if ("workspace".equals(templateType)) {
                blueprintVersion = "latest";
            } else {
                blueprintVersion = workspace.get("blueprintVersion");
            }

            if (blueprintVersion == null && workspace.getFile("cnf/ext/blueprint.bnd").exists()) {
                // The blueprint version is only available for blueprint r4 and higher, use r3 version when version unknown
                blueprintVersion = "r3";
            }

            createPreferencesLocationsRepo(workspace, blueprintVersion).map(repositories::add);

            return repositories.stream()
                    .flatMap(repo -> findTemplates(repo, templateType))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<OSGiRepository> createPreferencesLocationsRepo(Workspace workspace, String blueprintVersion) throws Exception {
        AmdatuIdeaPreferences preferences = AmdatuIdeaPreferences.getInstance();
        List<String> templateRepositoryUrls = new ArrayList<>(preferences.getTemplateRepositoryUrls());


        if (blueprintVersion != null ) {
            if (blueprintVersion.equals("latest")) {
                templateRepositoryUrls.add("https://repository.amdatu.org/amdatu-blueprint/latest.xml");
            } else {
                templateRepositoryUrls.add(String.format("https://repository.amdatu.org/amdatu-blueprint/%s/repo/index.xml.gz", blueprintVersion));
            }
        }

        if (templateRepositoryUrls.isEmpty()) {
            return Optional.empty();
        } else {
            OSGiRepository osGiRepository = new OSGiRepository();
            osGiRepository.setRegistry(workspace);
            osGiRepository.setReporter(workspace);
            Map<String, String> map = new HashMap<>();
            map.put("name", "Amdatu preferences template repos");
            map.put("poll.time", "-1");
            map.put("max.stale", "0");

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



        try {
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
        } catch (Exception e) {
            LOG.error("Failed to load templates from repository " + repository, e);
            return Stream.empty();

        }
    }
}
