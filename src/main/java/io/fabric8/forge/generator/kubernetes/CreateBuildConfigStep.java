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
package io.fabric8.forge.generator.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Objects;
import io.fabric8.forge.generator.Annotations;
import io.fabric8.forge.generator.AttributeMapKeys;
import io.fabric8.forge.generator.cache.CacheFacade;
import io.fabric8.forge.generator.cache.CacheNames;
import io.fabric8.forge.generator.che.CheStack;
import io.fabric8.forge.generator.che.CheStackDetector;
import io.fabric8.forge.generator.git.GitAccount;
import io.fabric8.forge.generator.git.GitProvider;
import io.fabric8.forge.generator.git.WebHookDetails;
import io.fabric8.forge.generator.pipeline.AbstractDevToolsCommand;
import io.fabric8.forge.generator.utils.WebClientHelpers;
import io.fabric8.kubernetes.api.Controller;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.ServiceNames;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceList;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.api.spaces.Space;
import io.fabric8.kubernetes.api.spaces.Spaces;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildRequest;
import io.fabric8.openshift.api.model.BuildRequestBuilder;
import io.fabric8.openshift.api.model.Project;
import io.fabric8.openshift.api.model.ProjectList;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.utils.DomHelper;
import io.fabric8.utils.Strings;
import io.fabric8.utils.URLUtils;
import org.infinispan.Cache;
import org.jboss.forge.addon.ui.command.UICommand;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.input.UIInput;
import org.jboss.forge.addon.ui.input.UISelectOne;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.inject.Inject;
import javax.ws.rs.RedirectionException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

import static io.fabric8.forge.generator.kubernetes.Base64Helper.base64decode;
import static io.fabric8.project.support.BuildConfigHelper.createBuildConfig;

/**
 * Creates the BuildConfig in OpenShift/Kubernetes so that the Jenkins build will be created
 */
public class CreateBuildConfigStep extends AbstractDevToolsCommand implements UICommand {
    protected static final String GITHUB_SCM_NAVIGATOR_ELEMENT = "org.jenkinsci.plugins.github__branch__source.GitHubSCMNavigator";

    private static final transient Logger LOG = LoggerFactory.getLogger(CreateBuildConfigStep.class);
    public static final String JENKINS_NAMESPACE_SUFFIX = "-jenkins";
    protected Cache<String, List<String>> namespacesCache;
    protected Cache<String, CachedSpaces> spacesCache;
    @Inject
    @WithAttributes(label = "Organisation", required = true, description = "The organisation")
    private UISelectOne<String> kubernetesSpace;
    @Inject
    @WithAttributes(label = "Space", description = "The space running Jenkins")
    private UISelectOne<SpaceDTO> labelSpace;
    @Inject
    @WithAttributes(label = "Jenkins Space", required = true, description = "The space running Jenkins")
    private UISelectOne<String> jenkinsSpace;
    @Inject
    @WithAttributes(label = "Trigger build", description = "Should a build be triggered immediately after import?")
    private UIInput<Boolean> triggerBuild;
    @Inject
    @WithAttributes(label = "Add CI?", description = "Should we add a Continuous Integration webhooks for Pull Requests?")
    private UIInput<Boolean> addCIWebHooks;
    @Inject
    private CacheFacade cacheManager;
    private KubernetesClient kubernetesClient;
    private int retryTriggerBuildCount = 5;

    /**
     * Combines the job patterns.
     */
    public static String combineJobPattern(String oldPattern, String repoName) {
        if (oldPattern == null) {
            oldPattern = "";
        }
        oldPattern = oldPattern.trim();
        if (oldPattern.isEmpty()) {
            return repoName;
        }
        return oldPattern + "|" + repoName;
    }

    public static void closeQuietly(Client client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                LOG.debug("Ignoring exception closing client: " + e, e);
            }
        }
    }

    public void initializeUI(final UIBuilder builder) throws Exception {
        this.kubernetesClient = KubernetesClientHelper.createKubernetesClient(builder.getUIContext());
        this.namespacesCache = cacheManager.getCache(CacheNames.USER_NAMESPACES);
        this.spacesCache = cacheManager.getCache(CacheNames.USER_SPACES);
        final String key = KubernetesClientHelper.getUserCacheKey(kubernetesClient);
        List<String> namespaces = namespacesCache.computeIfAbsent(key, k -> loadNamespaces(key));

        kubernetesSpace.setValueChoices(namespaces);
        if (!namespaces.isEmpty()) {
            kubernetesSpace.setDefaultValue(findDefaultNamespace(namespaces));
        }
        jenkinsSpace.setValueChoices(namespaces);
        if (!namespaces.isEmpty()) {
            jenkinsSpace.setDefaultValue(findDefaultJenkinsSpace(namespaces));
        }

        labelSpace.setValueChoices(() -> loadCachedSpaces(key));
        labelSpace.setItemLabelConverter(value -> value.getLabel());

        triggerBuild.setDefaultValue(true);
        addCIWebHooks.setDefaultValue(true);

        if (namespaces.size() > 1) {
            builder.add(kubernetesSpace);
        }
        builder.add(labelSpace);
        if (namespaces.size() > 1) {
            builder.add(jenkinsSpace);
        }
        builder.add(triggerBuild);
        builder.add(addCIWebHooks);
    }

    protected String findDefaultNamespace(List<String> namespaces) {
        String jenkinsNamespace = findDefaultJenkinsSpace(namespaces);
        if (jenkinsNamespace != null && jenkinsNamespace.endsWith(JENKINS_NAMESPACE_SUFFIX)) {
            String namespace = jenkinsNamespace.substring(0, jenkinsNamespace.length() - JENKINS_NAMESPACE_SUFFIX.length());
            if (namespaces.contains(namespace)) {
                return namespace;
            }
        }
        return namespaces.get(0);
    }

    private String findDefaultJenkinsSpace(List<String> namespaces) {
        for (String namespace : namespaces) {
            if (namespace.endsWith(JENKINS_NAMESPACE_SUFFIX)) {
                return namespace;
            }
        }
        if (namespaces.isEmpty()) {
            return null;
        } else {
            return namespaces.get(0);
        }
    }

    private List<SpaceDTO> loadCachedSpaces(String key) {
        String namespace = kubernetesSpace.getValue();
        CachedSpaces cachedSpaces = spacesCache.computeIfAbsent(key, k -> new CachedSpaces(namespace, loadSpaces(namespace)));
        if (!cachedSpaces.getNamespace().equals(namespace)) {
            cachedSpaces.setNamespace(namespace);
            cachedSpaces.setSpaces(loadSpaces(namespace));
        }
        return cachedSpaces.getSpaces();
    }

    private List<SpaceDTO> loadSpaces(String namespace) {
        List<SpaceDTO> answer = new ArrayList<>();
        if (namespace != null) {
            try {
                Spaces spacesValue = Spaces.load(kubernetesClient, namespace);
                if (spacesValue != null) {
                    SortedSet<Space> spaces = spacesValue.getSpaceSet();
                    for (Space space : spaces) {
                        answer.add(new SpaceDTO(space.getName(), space.getName()));
                    }
                }
            } catch (Exception e) {
                LOG.warn("Failed to load spaces: " + e, e);
            }
        }
        return answer;
    }

    private List<String> loadNamespaces(String key) {
        LOG.debug("Loading user namespaces for key " + key);
        SortedSet<String> namespaces = new TreeSet<>();

        KubernetesClient kubernetes = getKubernetesClient();
        OpenShiftClient openshiftClient = KubernetesClientHelper.getOpenShiftClientOrNull(kubernetes);
        if (openshiftClient != null) {
            // It is preferable to iterate on the list of projects as regular user with the 'basic-role' bound
            // are not granted permission get operation on non-existing project resource that returns 403
            // instead of 404. Only more privileged roles like 'view' or 'cluster-reader' are granted this permission.
            ProjectList list = openshiftClient.projects().list();
            if (list != null) {
                List<Project> items = list.getItems();
                if (items != null) {
                    for (Project item : items) {
                        String name = KubernetesHelper.getName(item);
                        if (Strings.isNotBlank(name)) {
                            namespaces.add(name);
                        }
                    }
                }
            }
        } else {
            NamespaceList list = kubernetes.namespaces().list();
            List<Namespace> items = list.getItems();
            if (items != null) {
                for (Namespace item : items) {
                    String name = KubernetesHelper.getName(item);
                    if (Strings.isNotBlank(name)) {
                        namespaces.add(name);
                    }
                }
            }
        }
        return new ArrayList<>(namespaces);
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        UIContext uiContext = context.getUIContext();
        Map<Object, Object> attributeMap = uiContext.getAttributeMap();

        String namespace = kubernetesSpace.getValue();
        String jenkinsNamespace = jenkinsSpace.getValue();
        if (Strings.isNullOrBlank(jenkinsNamespace)) {
            jenkinsNamespace = namespace;
        }
        String projectName = getProjectName(uiContext);
        GitAccount details = (GitAccount) attributeMap.get(AttributeMapKeys.GIT_ACCOUNT);
        if (details == null) {
            return Results.fail("No attribute: " + AttributeMapKeys.GIT_ACCOUNT);

        }
        String gitRepoPattern = (String) attributeMap.get(AttributeMapKeys.GIT_REPOSITORY_PATTERN);
        String gitRepoNameValue = (String) attributeMap.get(AttributeMapKeys.GIT_REPO_NAME);
        Iterable<String> gitRepoNames = (Iterable<String>) attributeMap.get(AttributeMapKeys.GIT_REPO_NAMES);
        if (Strings.isNullOrBlank(gitRepoNameValue) && gitRepoNames == null) {
            return Results.fail("No attribute: " + AttributeMapKeys.GIT_REPO_NAME + " or " + AttributeMapKeys.GIT_REPO_NAMES);
        }
        List<String> gitRepoNameList = new ArrayList<>();
        if (Strings.isNotBlank(gitRepoNameValue)) {
            gitRepoNameList.add(gitRepoNameValue);
        } else {
            for (String gitRepoName : gitRepoNames) {
                gitRepoNameList.add(gitRepoName);
            }
        }
        String gitOwnerName = (String) attributeMap.get(AttributeMapKeys.GIT_OWNER_NAME);
        if (Strings.isNullOrBlank(gitOwnerName)) {
            gitOwnerName = details.getUsername();
        }
        GitProvider gitProvider = (GitProvider) attributeMap.get(AttributeMapKeys.GIT_PROVIDER);
        if (gitProvider == null) {
            return Results.fail("No attribute: " + AttributeMapKeys.GIT_PROVIDER);
        }
        KubernetesClient kubernetes = getKubernetesClient();


        String jenkinsJobUrl = null;
        String cheStackId = null;
        String message = "";
        Boolean addCI = addCIWebHooks.getValue();
        boolean isGitHubOrganisationFolder = gitProvider.isGitHub();

        Controller controller = new Controller(kubernetesClient);
        controller.setNamespace(namespace);

        String gitUrl = (String) attributeMap.get(AttributeMapKeys.GIT_URL);
        if (Strings.isNotBlank(gitUrl)) {
            Map<String, String> annotations = new HashMap<>();
            // lets add the annotations so that it looks like its generated by jenkins-sync plugin to minimise duplication
            if (addCI && isGitHubOrganisationFolder) {
                annotations.put(Annotations.JENKINGS_GENERATED_BY, "jenkins");
                annotations.put(Annotations.JENKINS_JOB_PATH, "" + gitOwnerName + "/" + gitRepoNameValue + "/master");
            }
            CheStack stack = CheStackDetector.detectCheStack(uiContext, getCurrentSelectedProject(uiContext));
            if (stack != null) {
                cheStackId = stack.getId();
                annotations.put(Annotations.CHE_STACK, cheStackId);
            }
            if (addCI && isGitHubOrganisationFolder) {
                // lets disable jenkins-syn plugin creating the BC as well to avoid possible duplicate
                annotations.put("jenkins.openshift.org/disable-sync-create-on", "jenkins");
            }

            BuildConfig buildConfig = createBuildConfig(kubernetesClient, namespace, projectName, gitUrl, annotations);
            SpaceDTO spaceDTO = labelSpace.getValue();
            if (spaceDTO != null) {
                String spaceId = spaceDTO.getId();
                KubernetesHelper.getOrCreateLabels(buildConfig).put("space", spaceId);
            }
            controller.applyBuildConfig(buildConfig, "from project " + projectName);

            message += "Created OpenShift BuildConfig " + namespace + "/" + projectName;
        }
        List<String> warnings = new ArrayList<>();

        if (addCI) {
            String discoveryNamespace = KubernetesClientHelper.getDiscoveryNamespace(kubernetes, jenkinsNamespace);
            String jenkinsUrl;
            try {
                jenkinsUrl = KubernetesHelper.getServiceURL(kubernetes, ServiceNames.JENKINS, discoveryNamespace, "https", true);
            } catch (Exception e) {
                return Results.fail("Failed to find Jenkins URL: " + e, e);
            }

            String botServiceAccount = "cd-bot";
            String botSecret = findBotSecret(discoveryNamespace, botServiceAccount);
            if (Strings.isNullOrBlank(botSecret)) {
                botSecret = "secret101";
            }
            String oauthToken = kubernetes.getConfiguration().getOauthToken();
            String authHeader = "Bearer " + oauthToken;

            String webhookUrl = URLUtils.pathJoin(jenkinsUrl, "/github-webhook/");


            String triggeredBuildName = null;
            if (isGitHubOrganisationFolder) {
                try {
                    ensureJenkinsCDCredentialCreated(gitOwnerName, details.tokenOrPassword(), jenkinsUrl, authHeader);
                } catch (Exception e) {
                    LOG.error("Failed to create Jenkins CD Bot credentials: " + e, e);
                    return Results.fail("Failed to create Jenkins CD Bot credentials: " + e, e);
                }

                String gitRepoPatternOrName = gitRepoPattern;
                if (Strings.isNullOrBlank(gitRepoPatternOrName)) {
                    gitRepoPatternOrName = Strings.join(gitRepoNameList, "|");
                }
                try {
                    String jobUrl = URLUtils.pathJoin(jenkinsUrl, "/job/" + gitOwnerName);
                    if (Strings.isNotBlank(message)) {
                        message += ". ";
                    }
                    message += "Created Jenkins job: " + jobUrl;
                    jenkinsJobUrl = jobUrl;
                    ensureJenkinsCDOrganisationJobCreated(jenkinsUrl, jobUrl, oauthToken, authHeader, gitOwnerName, gitRepoPatternOrName);
                } catch (Exception e) {
                    LOG.error("Failed to create Jenkins Organisation job: " + e, e);
                    return Results.fail("Failed to create Jenkins Organisation job:: " + e, e);
                }
            } else {
                // lets trigger the build
                OpenShiftClient openShiftClient = controller.getOpenShiftClientOrNull();
                if (openShiftClient != null) {
                    triggerBuild(openShiftClient, namespace, projectName);
                }
            }

            for (String gitRepoName : gitRepoNameList) {
                try {
                    gitProvider.registerWebHook(details, new WebHookDetails(gitOwnerName, gitRepoName, webhookUrl, botSecret));
                    //registerGitWebHook(details, webhookUrl, gitOwnerName, gitRepoName, botSecret);
                } catch (Exception e) {
                    addWarning(warnings, "Failed to create CI webhooks for: " + gitRepoName + ": " + e, e);
                }
            }
            if (!gitRepoNameList.isEmpty()) {
                message += " and added git webhooks to repositories " + Strings.join(gitRepoNameList, ", ");
            }
            message += ". ";
        }
        CreateBuildConfigStatusDTO status = new CreateBuildConfigStatusDTO(namespace ,projectName, gitUrl, cheStackId, jenkinsJobUrl, gitRepoNameList, gitOwnerName, warnings);
        return Results.success(message, status);
    }

    private void addWarning(List<String> warnings, String message, Exception e) {
        LOG.warn(message, e);
        // TODO add stack trace too?
        warnings.add(message);
    }

    protected void triggerBuild(OpenShiftClient openShiftClient, String namespace, String projectName) {
        for (int i = 0; i < retryTriggerBuildCount; i++) {
            if (i > 0) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            String triggeredBuildName;
            BuildRequest request = new BuildRequestBuilder().
                    withNewMetadata().withName(projectName).endMetadata().
                    addNewTriggeredBy().withMessage("Manually triggered").endTriggeredBy().
                    build();
            try {
                Build build = openShiftClient.buildConfigs().inNamespace(namespace).withName(projectName).instantiate(request);
                if (build != null) {
                    triggeredBuildName = KubernetesHelper.getName(build);
                    LOG.info("Triggered build " + triggeredBuildName);
                    return;
                } else {
                    LOG.error("Failed to trigger build for " + namespace + "/" + projectName + " du to: no Build returned");
                }
            } catch (Exception e) {
                LOG.error("Failed to trigger build for " + namespace + "/" + projectName + " due to: " + e, e);
            }
        }
    }

    private void registerGitWebHook(GitAccount details, String webhookUrl, String gitOwnerName, String gitRepoName, String botSecret) throws IOException {

        // TODO move this logic into the GitProvider!!!
        String body = "{\"name\": \"web\",\"active\": true,\"events\": [\"*\"],\"config\": {\"url\": \"" + webhookUrl + "\",\"insecure_ssl\":\"1\"," +
                "\"content_type\": \"json\",\"secret\":\"" + botSecret + "\"}}";

        String authHeader = details.mandatoryAuthHeader();
        String createWebHookUrl = URLUtils.pathJoin("https://api.github.com/repos/", gitOwnerName, gitRepoName, "/hooks");

        // JAX-RS doesn't work so lets use trusty java.net.URL instead ;)
        HttpURLConnection connection = null;
        try {
            URL url = new URL(createWebHookUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept", MediaType.APPLICATION_JSON);
            connection.setRequestProperty("Content-Type", MediaType.APPLICATION_JSON);
            connection.setRequestProperty("Authorization", authHeader);
            connection.setDoOutput(true);

            OutputStreamWriter out = new OutputStreamWriter(
                    connection.getOutputStream());
            out.write(body);

            out.close();
            int status = connection.getResponseCode();
            String message = connection.getResponseMessage();
            LOG.info("Got response code from github " + createWebHookUrl + " status: " + status + " message: " + message);
            if (status < 200 || status >= 300) {
                LOG.error("Failed to create the github web hook at: " + createWebHookUrl + ". Status: " + status + " message: " + message);
                throw new IllegalStateException("Failed to create the github web hook at: " + createWebHookUrl + ". Status: " + status + " message: " + message);
            }
        } catch (Exception e) {
            LOG.error("Failed to create the github web hook at: " + createWebHookUrl + ". " + e, e);
            throw e;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Finds the secret token we should use for the web hooks
     */
    private String findBotSecret(String discoveryNamespace, String botServiceAccount) {
        KubernetesClient kubernetes = getKubernetesClient();
        SecretList list = kubernetes.secrets().inNamespace(discoveryNamespace).list();
        if (list != null) {
            List<Secret> items = list.getItems();
            if (items != null) {
                for (Secret item : items) {
                    String name = KubernetesHelper.getName(item);
                    if (name.startsWith(botServiceAccount + "-token-")) {
                        Map<String, String> data = item.getData();
                        if (data != null) {
                            String token = data.get("token");
                            if (token != null) {
                                return base64decode(token);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Triggers the given jenkins job via its URL.
     *
     * @param authHeader
     * @param jobUrl     the URL to the jenkins job
     * @param triggerUrl can be null or empty and the default triggerUrl will be used
     */
    protected void triggerJenkinsWebHook(String token, String authHeader, String jobUrl, String triggerUrl, boolean post) {
        if (Strings.isNullOrBlank(triggerUrl)) {
            //triggerUrl = URLUtils.pathJoin(jobUrl, "/build?token=" + token);
            triggerUrl = URLUtils.pathJoin(jobUrl, "/build?delay=0");
        }
        // lets check if this build is already running in which case do nothing
        String lastBuild = URLUtils.pathJoin(jobUrl, "/lastBuild/api/json");
        JsonNode lastBuildJson = parseLastBuildJson(authHeader, lastBuild);
        JsonNode building = null;
        if (lastBuildJson != null && lastBuildJson.isObject()) {
            building = lastBuildJson.get("building");
            if (building != null && building.isBoolean()) {
                if (building.booleanValue()) {
                    LOG.info("Build is already running so lets not trigger another one!");
                    return;
                }
            }
        }
        LOG.info("Got last build JSON: " + lastBuildJson + " building: " + building);

        LOG.info("Triggering Jenkins build: " + triggerUrl);

        Client client = WebClientHelpers.createClientWihtoutHostVerification();
        try {
            Response response = client.target(triggerUrl).
                    request().
                    header("Authorization", authHeader).
                    post(Entity.text(null), Response.class);

            int status = response.getStatus();
            String message = null;
            Response.StatusType statusInfo = response.getStatusInfo();
            if (statusInfo != null) {
                message = statusInfo.getReasonPhrase();
            }
            String extra = "";
            if (status == 302) {
                extra = " Location: " + response.getLocation();
            }
            LOG.info("Got response code from Jenkins: " + status + " message: " + message + " from URL: " + triggerUrl + extra);
            if (status <= 200 || status > 302) {
                LOG.error("Failed to trigger job " + triggerUrl + ". Status: " + status + " message: " + message);
            }
        } finally {
            closeQuietly(client);
        }
    }

    protected JsonNode parseLastBuildJson(String authHeader, String urlText) {
        Client client = WebClientHelpers.createClientWihtoutHostVerification();
        try {
            Response response = client.target(urlText).
                    request().
                    header("Authorization", authHeader).
                    post(Entity.text(null), Response.class);

            int status = response.getStatus();
            String message = null;
            Response.StatusType statusInfo = response.getStatusInfo();
            if (statusInfo != null) {
                message = statusInfo.getReasonPhrase();
            }
            LOG.info("Got response code from Jenkins: " + status + " message: " + message + " from URL: " + urlText);
            if (status <= 200 || status >= 300) {
                LOG.error("Failed to trigger job " + urlText + ". Status: " + status + " message: " + message);
            } else {
                String json = response.readEntity(String.class);
                if (Strings.isNotBlank(json)) {
                    ObjectMapper objectMapper = new ObjectMapper();
                    try {
                        return objectMapper.reader().readTree(json);
                    } catch (IOException e) {
                        LOG.warn("Failed to parse JSON: " + e, e);
                    }
                }
            }
        } finally {
            closeQuietly(client);
        }
        return null;
    }

    private Response ensureJenkinsCDCredentialCreated(String gitUserName, String gitToken, String jenkinsUrl, String authHeader) {
        String answer = null;

        LOG.info("Creating Jenkins fabric8 credentials for github user name: " + gitUserName);

        String createUrl = URLUtils.pathJoin(jenkinsUrl, "/credentials/store/system/domain/_/createCredentials");
        /*
        String getUrl = URLUtils.pathJoin(jenkinsUrl, "/credentials/store/system/domain/_/credentials/fabric8");

        Not sure we need to check it it already exists...

        try {
            answer = client.target(getUrl)
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", authHeader)
                    .get(String.class);
        } catch (Exception e) {
            LOG.warn("Caught probably expected error querying URL: " + getUrl + ". " + e, e);
        }
*/
        Response response = null;

        if (answer == null) {
            String json = "{\n" +
                    "  \"\": \"0\",\n" +
                    "  \"credentials\": {\n" +
                    "    \"scope\": \"GLOBAL\",\n" +
                    "    \"id\": \"fabric8\",\n" +
                    "    \"username\": \"" + gitUserName + "\",\n" +
                    "    \"password\": \"" + gitToken + "\",\n" +
                    "    \"description\": \"fabric8\",\n" +
                    "    \"$class\": \"com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl\"\n" +
                    "  }\n" +
                    "}";


            Form form = new Form();
            form.param("json", json);

            Client client = null;
            try {
                client = WebClientHelpers.createClientWihtoutHostVerification();
                response = client.target(createUrl).request().
                        header("Authorization", authHeader).
                        post(Entity.form(form), Response.class);

                int status = response.getStatus();
                String message = null;
                Response.StatusType statusInfo = response.getStatusInfo();
                if (statusInfo != null) {
                    message = statusInfo.getReasonPhrase();
                }
                String extra = "";
                if (status == 302) {
                    extra = " Location: " + response.getLocation();
                }
                LOG.info("Got response code from Jenkins: " + status + " message: " + message + " from URL: " + createUrl + extra);
                if (status <= 200 || status > 302) {
                    LOG.error("Failed to create credentials " + createUrl + ". Status: " + status + " message: " + message);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create the fabric8 credentials in Jenkins at the URL " + createUrl + ". " + e, e);
            } finally {
                closeQuietly(client);
            }
        }
        return response;
    }

    private Response ensureJenkinsCDOrganisationJobCreated(String jenkinsUrl, String jobUrl, String oauthToken, String authHeader, String gitOwnerName, String gitRepoName) {
        String triggerUrl = URLUtils.pathJoin(jobUrl, "/build?delay=0");
        String getUrl = URLUtils.pathJoin(jobUrl, "/config.xml");
        String createUrl = URLUtils.pathJoin(jenkinsUrl, "/createItem?name=" + gitOwnerName);

        Document document = null;
        try {
            Response response = invokeRequestWithRedirectResponse(getUrl,
                    target -> target.request(MediaType.TEXT_XML).
                            header("Authorization", authHeader).
                            get(Response.class));
            document = response.readEntity(Document.class);
            if (document == null) {
                document = parseEntityAsXml(response.readEntity(String.class));
            }
        } catch (Exception e) {
            LOG.warn("Failed to get gitub org job at " + getUrl + ". Probably does not exist? " + e, e);
        }

        boolean create = false;
        if (document == null || getGithubScmNavigatorElement(document) == null) {
            create = true;
            document = parseGitHubOrgJobConfig();
            if (document == null) {
                throw new IllegalStateException("Cannot parse the template github org job XML!");
            }
        }

        setGithubOrgJobOwnerAndRepo(document, gitOwnerName, gitRepoName);

        final Entity entity = Entity.entity(document, MediaType.TEXT_XML);
        Response answer;
        if (create) {
            try {
                answer = invokeRequestWithRedirectResponse(createUrl,
                        target -> target.request(MediaType.TEXT_XML).
                                header("Authorization", authHeader).
                                post(entity, Response.class));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create the GitHub Org Job at " + createUrl + ". " + e, e);
            }
        } else {
            try {
                answer = invokeRequestWithRedirectResponse(getUrl,
                        target -> target.request(MediaType.TEXT_XML).
                                header("Authorization", authHeader).
                                post(entity, Response.class));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to update the GitHub Org Job at " + getUrl + ". " + e, e);
            }
        }

        LOG.info("Triggering the job " + jobUrl);
        try {
            triggerJenkinsWebHook(oauthToken, authHeader, jobUrl, triggerUrl, true);
        } catch (Exception e) {
            LOG.error("Failed to trigger jenkins job at " + triggerUrl + ". " + e, e);
        }
        return answer;
    }

    private void setGithubOrgJobOwnerAndRepo(Document doc, String gitOwnerName, String gitRepoName) {
        Element githubNavigator = getGithubScmNavigatorElement(doc);
        if (githubNavigator == null) {
            new IllegalArgumentException("No element <" + GITHUB_SCM_NAVIGATOR_ELEMENT + "> found in the github organisation job!");
        }

        Element repoOwner = mandatoryFirstChild(githubNavigator, "repoOwner");
        Element pattern = mandatoryFirstChild(githubNavigator, "pattern");

        String newPattern = combineJobPattern(pattern.getTextContent(), gitRepoName);
        setElementText(repoOwner, gitOwnerName);
        setElementText(pattern, newPattern);
    }

    protected Element getGithubScmNavigatorElement(Document doc) {
        Element githubNavigator = null;
        Element rootElement = doc.getDocumentElement();
        if (rootElement != null) {
            NodeList githubNavigators = rootElement.getElementsByTagName(GITHUB_SCM_NAVIGATOR_ELEMENT);
            for (int i = 0, size = githubNavigators.getLength(); i < size; i++) {
                Node item = githubNavigators.item(i);
                if (item instanceof Element) {
                    Element element = (Element) item;
                    githubNavigator = element;
                    break;
                }
            }
        }
        return githubNavigator;
    }

    protected Response invokeRequestWithRedirectResponse(String url, Function<WebTarget, Response> callback) {
        boolean redirected = false;
        Response response = null;
        for (int i = 0, retries = 2; i < retries; i++) {
            Client client = null;
            try {
                client = WebClientHelpers.createClientWihtoutHostVerification();
                WebTarget target = client.target(url);
                response = callback.apply(target);
                int status = response.getStatus();
                String reasonPhrase = "";
                Response.StatusType statusInfo = response.getStatusInfo();
                if (statusInfo != null) {
                    reasonPhrase = statusInfo.getReasonPhrase();
                }
                LOG.info("Response from " + url + " is " + status + " " + reasonPhrase);
                if (status == 302) {
                    if (redirected) {
                        LOG.warn("Failed to process " + url + " and got status: " + status + " " + reasonPhrase, response);
                        throw new WebApplicationException("Failed to process " + url + " and got status: " + status + " " + reasonPhrase, response);
                    }
                    redirected = true;
                    URI uri = response.getLocation();
                    if (uri == null) {
                        LOG.warn("Failed to process " + url + " and got status: " + status + " " + reasonPhrase + " but no location header!", response);
                        throw new WebApplicationException("Failed to process " + url + " and got status: " + status + " " + reasonPhrase + " but no location header!", response);
                    }
                    url = uri.toString();
                } else if (status < 200 || status >= 300) {
                    LOG.warn("Failed to process " + url + " and got status: " + status + " " + reasonPhrase, response);
                    throw new WebApplicationException("Failed to process " + url + " and got status: " + status + " " + reasonPhrase, response);
                } else {
                    return response;
                }
            } catch (RedirectionException redirect) {
                if (redirected) {
                    throw redirect;
                }
                redirected = true;
                URI uri = redirect.getLocation();
                url = uri.toString();
            } finally {
                closeQuietly(client);
            }
        }
        return response;
    }

    private Client createSecureClient() {
        return ClientBuilder.newClient();
    }

    /**
     * Updates the element content if its different and returns true if it was changed
     */
    private boolean setElementText(Element element, String value) {
        String textContent = element.getTextContent();
        if (Objects.equal(value, textContent)) {
            return false;
        }
        element.setTextContent(value);
        return true;
    }

    /**
     * Returns the first child of the given element with the name or throws an exception
     */
    private Element mandatoryFirstChild(Element element, String name) {
        Element child = DomHelper.firstChild(element, name);
        if (child == null) {
            throw new IllegalArgumentException("The element <" + element.getTagName() + "> should have at least one child called <" + name + ">");
        }
        return child;
    }


    public KubernetesClient getKubernetesClient() {
        return kubernetesClient;
    }

    private Document parseEntityAsXml(String entity) throws ParserConfigurationException, IOException, SAXException {
        if (entity == null) {
            return null;
        }
        DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        return documentBuilder.parse(new ByteArrayInputStream(entity.getBytes()));
    }


    public Document parseGitHubOrgJobConfig() {
        String templateName = "github-org-job-config.xml";
        URL url = getClass().getResource(templateName);
        if (url == null) {
            LOG.error("Could not load " + templateName + " on the classpath!");
        } else {
            try {
                DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                return documentBuilder.parse(url.toString());
            } catch (Exception e) {
                LOG.error("Failed to load template " + templateName + " from " + url + ". " + e, e);
            }
        }
        return null;
    }
}
