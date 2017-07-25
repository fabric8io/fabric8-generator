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
import io.fabric8.forge.generator.AttributeMapKeys;
import io.fabric8.forge.generator.cache.CacheFacade;
import io.fabric8.forge.generator.cache.CacheNames;
import io.fabric8.forge.generator.kubernetes.CachedSpaces;
import io.fabric8.forge.generator.kubernetes.KubernetesClientHelper;
import io.fabric8.forge.generator.kubernetes.SpaceDTO;
import io.fabric8.forge.generator.versions.VersionHelper;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.utils.DomHelper;
import io.fabric8.utils.Files;
import io.fabric8.utils.Filter;
import io.fabric8.utils.IOHelpers;
import io.fabric8.utils.Objects;
import io.fabric8.utils.Strings;
import org.infinispan.Cache;
import org.jboss.forge.addon.convert.Converter;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.input.UIInput;
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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.fabric8.forge.generator.che.CheStackDetector.parseXmlFile;
import static io.fabric8.forge.generator.kubernetes.KubernetesClientHelper.findDefaultNamespace;
import static io.fabric8.forge.generator.utils.DomUtils.getOrCreateChild;
import static io.fabric8.kubernetes.api.KubernetesHelper.loadYaml;

public class ChoosePipelineStep extends AbstractProjectOverviewCommand implements UIWizardStep {
    public static final String JENKINSFILE = "Jenkinsfile";
    private static final transient Logger LOG = LoggerFactory.getLogger(ChoosePipelineStep.class);
    private static final String DEFAULT_MAVEN_FLOW = "workflows/maven/CanaryReleaseStageAndApprovePromote.groovy";
    protected Cache<String, List<String>> namespacesCache;
    protected Cache<String, CachedSpaces> spacesCache;
    @Inject
    @WithAttributes(label = "Pipeline", description = "The Jenkinsfile used to define the Continous Delivery pipeline")
    private UISelectOne<PipelineDTO> pipeline;
    @Inject
    @WithAttributes(label = "Organisation", required = true, description = "The organisation")
    private UISelectOne<String> kubernetesSpace;
    @Inject
    @WithAttributes(label = "Space", description = "The space for the new app")
    private UIInput<String> labelSpace;
    @Inject
    private JenkinsPipelineLibrary jenkinsPipelineLibrary;
    @Inject
    private CacheFacade cacheManager;
    private KubernetesClient kubernetesClient;
    private String namespace = KubernetesHelper.defaultNamespace();

    private boolean hasJenkinsFile;

    private static Element getGrandParentElement(Element node) {
        Node parentNode = node.getParentNode();
        if (parentNode != null) {
            Node grandParent = parentNode.getParentNode();
            if (grandParent instanceof Element) {
                Element element = (Element) grandParent;
                return element;
            }
        }
        return null;
    }

    private static String getGrandParentElementName(Element node) {
        Element element = getGrandParentElement(node);
        if (element != null) {
            return element.getTagName();
        }
        return null;
    }

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(getClass())
                .category(Categories.create(AbstractDevToolsCommand.CATEGORY))
                .name(AbstractDevToolsCommand.CATEGORY + ": Configure Pipeline")
                .description("Configure the Pipeline for the new project");
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        this.kubernetesClient = KubernetesClientHelper.createKubernetesClient(builder.getUIContext());
        this.namespacesCache = cacheManager.getCache(CacheNames.USER_NAMESPACES);
        this.spacesCache = cacheManager.getCache(CacheNames.USER_SPACES);
        final String key = KubernetesClientHelper.getUserCacheKey(kubernetesClient);
        List<String> namespaces = namespacesCache.computeIfAbsent(key, k -> KubernetesClientHelper.loadNamespaces(kubernetesClient, key));


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

        kubernetesSpace.setValueChoices(namespaces);
        if (!namespaces.isEmpty()) {
            kubernetesSpace.setDefaultValue(findDefaultNamespace(namespaces));
        }
        if (namespaces.size() > 1) {
            builder.add(kubernetesSpace);
        }

/*
        labelSpace.setValueChoices(() -> loadCachedSpaces(key));
        labelSpace.setItemLabelConverter(value -> value.getLabel());
*/
        builder.add(labelSpace);


        LOG.debug("initializeUI took " + watch.taken());
    }

    private List<SpaceDTO> loadCachedSpaces(String key) {
        String namespace = kubernetesSpace.getValue();
        CachedSpaces cachedSpaces = spacesCache.computeIfAbsent(key, k -> new CachedSpaces(namespace, KubernetesClientHelper.loadSpaces(this.kubernetesClient, namespace)));
        if (!cachedSpaces.getNamespace().equals(namespace)) {
            cachedSpaces.setNamespace(namespace);
            cachedSpaces.setSpaces(KubernetesClientHelper.loadSpaces(this.kubernetesClient, namespace));
        }
        return cachedSpaces.getSpaces();
    }

    private boolean hasLocalJenkinsFile(UIContext context, Project project) {
        File jenkinsFile = CommandHelpers.getProjectContextFile(context, project, "Jenkinsfile");
        boolean hasJenkinsFile = Files.isFile(jenkinsFile);
        LOG.debug("Has Jenkinsfile " + hasJenkinsFile + " with file: " + jenkinsFile);
        return hasJenkinsFile;
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        UIContext uiContext = context.getUIContext();
        storeAttributes(uiContext);
        return null;
    }

    protected void storeAttributes(UIContext uiContext) {
        Map<Object, Object> attributeMap = uiContext.getAttributeMap();
        String ns = kubernetesSpace.getValue();
        String space = labelSpace.getValue();
        if (Strings.isNotBlank(ns)) {
            attributeMap.put(AttributeMapKeys.NAMESPACE, ns);
        }
        if (Strings.isNotBlank(space)) {
            attributeMap.put(AttributeMapKeys.SPACE, space);
        }
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        UIContext uiContext = context.getUIContext();
        storeAttributes(uiContext);
        Map<Object, Object> attributeMap = uiContext.getAttributeMap();
        attributeMap.put("hasJenkinsFile", hasJenkinsFile);
        PipelineDTO value = pipeline.getValue();
        attributeMap.put("selectedPipeline", value);
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
        updatePomVersions(pom, status, getSpaceId());
    }

    public static void updatePomVersions(File pom, StatusDTO status, String spaceId) {
        if (pom.exists() && pom.isFile()) {
            Document doc;
            try {
                doc = parseXmlFile(pom);
            } catch (Exception e) {
                status.warning(LOG, "Cannot parse pom.xml: " + e, e);
                return;
            }
            Element rootElement = doc.getDocumentElement();
            NodeList plugins = rootElement.getElementsByTagName("plugin");
            Set<String> fmpVersionProperties = new HashSet<>();
            Set<String> fmpVersionPropertiesLazyCreate = new HashSet<>();
            List<Element> fmpPlugins = new ArrayList<>();
            List<Element> fmpPluginsWithVersion = new ArrayList<>();

            String fmpVersion = VersionHelper.fabric8MavenPluginVersion();
            boolean update = false;

            for (int i = 0, size = plugins.getLength(); i < size; i++) {
                Node item = plugins.item(i);
                if (item instanceof Element) {
                    Element element = (Element) item;
                    if ("fabric8-maven-plugin".equals(DomHelper.firstChildTextContent(element, "artifactId"))) {
                        String version = DomHelper.firstChildTextContent(element, "version");
                        if (version != null) {
                            fmpPluginsWithVersion.add(element);
                            if (version.startsWith("${") && version.endsWith("}")) {
                                String versionProperty = version.substring(2, version.length() - 1);
                                fmpVersionPropertiesLazyCreate.add(versionProperty);
                            } else {
                                if (updateFirstChild(element, "version", fmpVersion)) {
                                    update = true;
                                }
                            }
                        } else {
                            fmpPlugins.add(element);
                        }
                    }
                }
            }


            // Lets add a new version element to all fmp <plugin> which don't have a corresponding versioned
            // <pluginManagement> entry in the same <build>
            for (Element fmpPlugin : fmpPlugins) {
                Element grandParent = getGrandParentElement(fmpPlugin);
                if (grandParent == null) {
                    continue;
                }
                boolean addVersion = false;
                if ("pluginManagement".equals(grandParent.getTagName())) {
                    addVersion = true;
                } else if ("build".equals(grandParent.getTagName())) {
                    boolean found = false;
                    for (Element pluginWithVersion : fmpPluginsWithVersion) {
                        Element pluginVersionGrandParent = getGrandParentElement(pluginWithVersion);
                        if (pluginVersionGrandParent != null && "pluginManagement".equals(pluginVersionGrandParent.getTagName())) {
                            if (pluginVersionGrandParent.getParentNode() == grandParent) {
                                // there's a pluginManagement version specified for this
                                found = true;
                                break;
                            }

                        }
                    }
                    if (!found) {
                        addVersion = true;
                    }
                }
                if (addVersion) {
                    // lets add an explicit version as we can't find a plugin-management one
                    Element version = doc.createElement("version");
                    version.setTextContent(fmpVersion);
                    Element artifactId = DomHelper.firstChild(fmpPlugin, "artifactId");
                    Node nextSibling = artifactId.getNextSibling();
                    String text = getPreviousText(artifactId);
                    fmpPlugin.insertBefore(doc.createTextNode(text), nextSibling);
                    fmpPlugin.insertBefore(version, nextSibling);
                    update = true;
                }
            }

            List<String> defaultVersionProperties = Arrays.asList("fabric8.maven.plugin.version", "fabric8-maven-plugin.version");
            for (String property : defaultVersionProperties) {
                if (!fmpVersionPropertiesLazyCreate.contains(property)) {
                    fmpVersionProperties.add(property);
                }
            }

            Element properties = DomHelper.firstChild(rootElement, "properties");
            if (properties == null && !fmpVersionPropertiesLazyCreate.isEmpty()) {
                properties = DomHelper.addChildElement(rootElement, "properties");
            }
            if (properties != null) {
                if (updateFirstChild(properties, "fabric8.version", VersionHelper.fabric8Version())) {
                    update = true;
                }
                for (String property : fmpVersionPropertiesLazyCreate) {
                    if (updateFirstChild(properties, property, fmpVersion)) {
                        update = true;
                    } else {
                        if (DomHelper.firstChild(properties, property) == null) {
                            DomHelper.addChildElement(properties, property, fmpVersion);
                            update = true;
                        }
                    }
                }
                for (String property : fmpVersionProperties) {
                    if (updateFirstChild(properties, property, fmpVersion)) {
                        update = true;
                    }
                }
                if (ensureSpaceLabelAddedToPom(doc, spaceId)) {
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

    /**
     * Returns the combined text of previous nodes of the given element
     */
    private static String getPreviousText(Node node) {
        StringBuilder builder = new StringBuilder();
        while (node != null) {
            node = node.getPreviousSibling();
            if (node instanceof Text) {
                Text textNode = (Text) node;
                builder.append(textNode.getWholeText());
            } else {
                break;
            }
        }
        return builder.toString();
    }

    protected String getSpaceId() {
        return labelSpace.getValue();
/*
        SpaceDTO spaceDTO = labelSpace.getValue();
        if (spaceDTO != null) {
            return spaceDTO.getId();
        }
        return null;
*/
    }

    private static boolean ensureSpaceLabelAddedToPom(Document document, String spaceId) {
        if (document != null && Strings.isNotBlank(spaceId)) {
            NodeList plugins = document.getElementsByTagName("plugin");
            if (plugins != null) {
                for (int i = 0, size = plugins.getLength(); i < size; i++) {
                    Node item = plugins.item(i);
                    if (item instanceof Element) {
                        Element element = (Element) item;
                        if ("fabric8-maven-plugin".equals(DomHelper.firstChildTextContent(element, "artifactId"))) {
                            String indent = "\n        ";
                            Element configuration = getOrCreateChild(element, "configuration", indent);
                            Element resources = getOrCreateChild(configuration, "resources", indent + "  ");
                            Element labels = getOrCreateChild(resources, "labels", indent + "    ");
                            Element all = getOrCreateChild(labels, "all", indent + "      ");
                            Element space = getOrCreateChild(all, "space", indent + "        ");
                            if (!spaceId.equals(space.getTextContent())) {
                                space.setTextContent(spaceId);
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean updateFirstChild(Element parentElement, String elementName, String value) {
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
                        PipelineDTO pipeline = new PipelineDTO(value, humanize(label), builder, descriptionMarkdown);

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

    private String humanize(String label) {
        String text = Strings.splitCamelCase(label, ", ");
        return text.replace(", And, ", " and ");
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
