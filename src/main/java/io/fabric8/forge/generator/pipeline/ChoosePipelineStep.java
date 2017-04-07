/**
 * Copyright 2005-2015 Red Hat, Inc.
 * <p>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.forge.generator.pipeline;

import io.fabric8.devops.ProjectConfig;
import io.fabric8.devops.ProjectConfigs;
import io.fabric8.forge.addon.utils.CommandHelpers;
import io.fabric8.forge.addon.utils.StopWatch;
import io.fabric8.forge.generator.versions.VersionHelper;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.utils.DomHelper;
import io.fabric8.utils.Files;
import io.fabric8.utils.Filter;
import io.fabric8.utils.IOHelpers;
import io.fabric8.utils.Objects;
import io.fabric8.utils.Strings;
import org.jboss.forge.addon.convert.Converter;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.input.UISelectOne;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.NavigationResult;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;
import org.jboss.forge.addon.ui.wizard.UIWizardStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.inject.Inject;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.fabric8.forge.generator.che.CheStackDetector.parseXmlFile;
import static io.fabric8.kubernetes.api.KubernetesHelper.loadYaml;

public class ChoosePipelineStep extends AbstractProjectOverviewCommand implements UIWizardStep {
    public static final String JENKINSFILE = "Jenkinsfile";
    private static final transient Logger LOG = LoggerFactory.getLogger(ChoosePipelineStep.class);
    private static final String DEFAULT_MAVEN_FLOW = "workflows/maven/CanaryReleaseStageAndApprovePromote.groovy";
    @Inject
    @WithAttributes(label = "Pipeline", description = "The Jenkinsfile used to define the Continous Delivery pipeline")
    private UISelectOne<PipelineDTO> pipeline;

    @Inject
    private JenkinsPipelineLibrary jenkinsPipelineLibrary;

    private String namespace = KubernetesHelper.defaultNamespace();

    private boolean hasJenkinsFile;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(getClass())
                .category(Categories.create(AbstractDevToolsCommand.CATEGORY))
                .name(AbstractDevToolsCommand.CATEGORY + ": Configure Pipeline")
                .description("Configure the Pipeline for the new project");
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        StopWatch watch = new StopWatch();

        final UIContext context = builder.getUIContext();
        List<PipelineDTO> pipelineOptions = getPipelines(context, true);
        pipeline.setValueChoices(pipelineOptions);
        if (!pipelineOptions.isEmpty()) {
            // for now lets pick the first one but we should have a marker for the default?
            pipeline.setDefaultValue(pipelineOptions.get(pipelineOptions.size() - 1));

        }
        pipeline.setItemLabelConverter(new Converter<PipelineDTO, String>() {
            @Override
            public String convert(PipelineDTO pipeline) {
                return pipeline.getLabel();
            }
        });

        /*
        pipeline.setCompleter((context1, input, value) -> getPipelines(context1, true));
        */
        pipeline.setValueConverter(text -> getPipelineForValue(context, text));
        if (getCurrentSelectedProject(context) != null) {
            PipelineDTO defaultValue = getPipelineForValue(context, DEFAULT_MAVEN_FLOW);
            if (defaultValue != null) {
                pipeline.setDefaultValue(defaultValue);
            }
        }

        // lets initialise the data from the current config if it exists
        ProjectConfig config = null;
        Project project = getCurrentSelectedProject(context);
/*
        File configFile = getProjectConfigFile(context, getSelectedProject(context));
        if (configFile != null && configFile.exists()) {
            config = ProjectConfigs.parseProjectConfig(configFile);
        }
        if (config != null) {
            PipelineDTO flow = getPipelineForValue(context, config.getPipeline());
            if (flow != null) {
                CommandHelpers.setInitialComponentValue(this.pipeline, flow);
            }
            context.getAttributeMap().put("projectConfig", config);
        }
*/

        hasJenkinsFile = hasLocalJenkinsFile(context, project);
        if (!hasJenkinsFile) {
            builder.add(pipeline);
        }

        LOG.debug("initializeUI took " + watch.taken());
    }

    private boolean hasLocalJenkinsFile(UIContext context, Project project) {
        File jenkinsFile = CommandHelpers.getProjectContextFile(context, project, "Jenkinsfile");
        boolean hasJenkinsFile = Files.isFile(jenkinsFile);
        LOG.debug("Has Jenkinsfile " + hasJenkinsFile + " with file: " + jenkinsFile);
        return hasJenkinsFile;
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        return null;
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        UIContext uiContext = context.getUIContext();
        context.getUIContext().getAttributeMap().put("hasJenkinsFile", hasJenkinsFile);
        PipelineDTO value = pipeline.getValue();
        context.getUIContext().getAttributeMap().put("selectedPipeline", value);
        File basedir = getSelectionFolder(uiContext);
        StatusDTO status = new StatusDTO();
        if (basedir == null || !basedir.isDirectory()) {
            status.warning(LOG, "Cannot copy the pipeline to the project as no basedir!");
        } else {
            if (value != null) {
            String pipelinePath = value.getValue();
                if (Strings.isNullOrBlank(pipelinePath)) {
                    status.warning(LOG, "Cannot copy the pipeline to the project as the pipeline has no Jenkinsfile configured!");
                } else {
                    String pipelineText = getPipelineContent(pipelinePath, context.getUIContext());
                    if (Strings.isNullOrBlank(pipelineText)) {
                        status.warning(LOG, "Cannot copy the pipeline to the project as no pipeline text could be loaded!");
                    } else {
                        File newFile = new File(basedir, ProjectConfigs.LOCAL_FLOW_FILE_NAME);
                        Files.writeToFile(newFile, pipelineText.getBytes());
                        LOG.debug("Written Jenkinsfile to " + newFile);
                    }
                }
            }
            updatePomVersions(uiContext, status, basedir);
        }
        return Results.success("Added Jenkinsfile to project", status);
    }


    private void updatePomVersions(UIContext uiContext, StatusDTO status, File basedir) {
        File pom = new File(basedir, "pom.xml");
        if (pom.exists() && pom.isFile()) {
            Document doc;
            try {
                doc = parseXmlFile(pom);
            } catch (Exception e) {
                status.warning(LOG, "Cannot parse pom.xml: " + e, e);
                return;
            }
            Element properties = DomHelper.firstChild(doc.getDocumentElement(), "properties");
            if (properties != null) {
                boolean update = false;
                if (updateFirstChild(properties, "fabric8.version", VersionHelper.fabric8Version())) {
                    update = true;
                }
                if (updateFirstChild(properties, "fabric8.maven.plugin.version", VersionHelper.fabric8MavenPluginVersion())) {
                    update = true;
                }
                if (updateFirstChild(properties, "fabric8-maven-plugin.version", VersionHelper.fabric8MavenPluginVersion())) {
                    update = true;
                }
                if (update) {
                    LOG.debug("Updating properties of pom.xml");
                    try {
                        DomHelper.save(doc, pom);
                    } catch (Exception e) {
                        status.warning(LOG, "failed to save pom.xml: " + e, e);
                    }
                }
            }
        }
    }

    private boolean updateFirstChild(Element parentElement, String elementName, String value) {
        if (parentElement != null) {
            Element element = DomHelper.firstChild(parentElement, elementName);
            if (element != null) {
                String textContent = element.getTextContent();
                if (textContent == null || !value.equals(textContent)) {
                    element.setTextContent(value);
                    return true;
                }
            }
        }
        return false;
    }

    private String getPipelineContent(String flow, UIContext context) {
        File dir = getJenkinsWorkflowFolder(context);
        if (dir != null) {
            File file = new File(dir, flow);
            if (file.isFile() && file.exists()) {
                try {
                    return IOHelpers.readFully(file);
                } catch (IOException e) {
                    LOG.warn("Failed to load local pipeline " + file + ". " + e, e);
                }
            }
        }
        return null;
    }

    protected PipelineDTO getPipelineForValue(UIContext context, String value) {
        if (Strings.isNotBlank(value)) {
            Iterable<PipelineDTO> pipelines = getPipelines(context, false);
            for (PipelineDTO pipelineDTO : pipelines) {
                if (pipelineDTO.getValue().equals(value) || pipelineDTO.toString().equals(value)) {
                    return pipelineDTO;
                }
            }
        }
        return null;
    }

    protected List<PipelineDTO> getPipelines(UIContext context, boolean filterPipelines) {
        StopWatch watch = new StopWatch();

        Set<String> builders = null;
        ProjectOverviewDTO projectOverview;
        if (filterPipelines) {
            projectOverview = getProjectOverview(context);
            builders = projectOverview.getBuilders();
        }
        File dir = getJenkinsWorkflowFolder(context);
        Set<String> buildersFound = new HashSet<>();
        try {
            if (dir != null) {
                Filter<File> filter = new Filter<File>() {
                    @Override
                    public boolean matches(File file) {
                        return file.isFile() && Objects.equal(JENKINSFILE, file.getName());
                    }
                };
                Set<File> files = Files.findRecursive(dir, filter);
                List<PipelineDTO> pipelines = new ArrayList<>();
                for (File file : files) {
                    try {
                        String relativePath = Files.getRelativePath(dir, file);
                        String value = Strings.stripPrefix(relativePath, "/");
                        String label = value;
                        String postfix = "/" + JENKINSFILE;
                        if (label.endsWith(postfix)) {
                            label = label.substring(0, label.length() - postfix.length());
                        }
                        // Lets ignore the fabric8 specific pipelines
                        if (label.startsWith("fabric8-release/")) {
                            continue;
                        }
                        String builder = null;
                        int idx = label.indexOf("/");
                        if (idx > 0) {
                            builder = label.substring(0, idx);
                            if (filterPipelines && !builders.contains(builder)) {
                                // ignore this builder
                                continue;
                            } else {
                                buildersFound.add(builder);
                            }
                        }
                        String descriptionMarkdown = null;
                        File markdownFile = new File(file.getParentFile(), "ReadMe.md");
                        if (Files.isFile(markdownFile)) {
                            descriptionMarkdown = IOHelpers.readFully(markdownFile);
                        }
                        PipelineDTO pipeline = new PipelineDTO(value, label, builder, descriptionMarkdown);

                        File yamlFile = new File(file.getParentFile(), "metadata.yml");
                        if (Files.isFile(yamlFile)) {
                            PipelineMetadata metadata = null;
                            try {
                                metadata = loadYaml(yamlFile, PipelineMetadata.class);
                            } catch (IOException e) {
                                LOG.warn("Failed to parse yaml file " + yamlFile + ". " + e, e);
                            }
                            if (metadata != null) {
                                metadata.configurePipeline(pipeline);
                            }
                        }
                        pipelines.add(pipeline);
                    } catch (IOException e) {
                        LOG.warn("Failed to find relative path for folder " + dir + " and file " + file + ". " + e, e);
                    }
                }
                if (buildersFound.size() == 1) {
                    // lets trim the builder prefix from the labels
                    for (String first : buildersFound) {
                        String prefix = first + "/";
                        for (PipelineDTO pipeline : pipelines) {
                            String label = pipeline.getLabel();
                            if (label.startsWith(prefix)) {
                                label = label.substring(prefix.length());
                                pipeline.setLabel(label);
                            }
                        }
                        break;
                    }
                }
                Collections.sort(pipelines);
                return pipelines;
            } else {
                LOG.warn("No jenkinsfilesFolder!");
                return new ArrayList<>();
            }
        } finally {
            LOG.debug("getPipelines took " + watch.taken());
        }
    }

    protected File getJenkinsWorkflowFolder(UIContext context) {
        return jenkinsPipelineLibrary.getWorkflowFolder();
/*
        File dir = null;
        Object workflowFolder = context.getAttributeMap().get("jenkinsfilesFolder");
        if (workflowFolder instanceof File) {
            dir = (File) workflowFolder;
        }
        return dir;
*/
    }

}
