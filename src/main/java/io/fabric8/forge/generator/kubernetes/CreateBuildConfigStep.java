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

import io.fabric8.forge.generator.AttributeMapKeys;
import io.fabric8.forge.generator.cache.CacheFacade;
import io.fabric8.forge.generator.cache.CacheNames;
import io.fabric8.forge.generator.git.GitAccount;
import io.fabric8.forge.generator.git.GitOrganisationDTO;
import io.fabric8.forge.generator.pipeline.AbstractDevToolsCommand;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.Project;
import io.fabric8.openshift.api.model.ProjectList;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.utils.Strings;
import org.infinispan.Cache;
import org.jboss.forge.addon.ui.command.UICommand;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.input.UISelectOne;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import static io.fabric8.project.support.BuildConfigHelper.createAndApplyBuildConfig;

/**
 * Creates the BuildConfig in OpenShift/Kubernetes so that the Jenkins build will be created
 */
public class CreateBuildConfigStep extends AbstractDevToolsCommand implements UICommand {
    private static final transient Logger LOG = LoggerFactory.getLogger(CreateBuildConfigStep.class);

    @Inject
    @WithAttributes(label = "Space", required = true, description = "The space to create the app")
    private UISelectOne<String> kubernetesSpace;

    @Inject
    private CacheFacade cacheManager;

    protected Cache<String, List<String>> namespacesCache;

    private KubernetesClient kubernetesClient;

    public void initializeUI(final UIBuilder builder) throws Exception {
        this.namespacesCache = cacheManager.getCache(CacheNames.USER_NAMESPACES);
        final String key = KubernetesClientHelper.getUserCacheKey();
        List<String> namespaces = namespacesCache.computeIfAbsent(key, k -> loadNamespaces(key));
        LOG.info("Has namespaces: " + namespaces);

        kubernetesSpace.setValueChoices(namespaces);
        if (!namespaces.isEmpty()) {
            kubernetesSpace.setDefaultValue(namespaces.get(0));
        }
        builder.add(kubernetesSpace);
    }

    private List<String> loadNamespaces(String key) {
        LOG.info("Loading user namespaces for key " + key);
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
        // TODO add annotations...
        Map<String, String> annotations = new HashMap<>();
        String namespace = kubernetesSpace.getValue();
        String projectName = getProjectName(uiContext);
        String gitUrl = (String) attributeMap.get(AttributeMapKeys.GIT_URL);
        if (Strings.isNullOrBlank(gitUrl)) {
            return Results.fail("No attribute: " + AttributeMapKeys.GIT_URL);
        }
        createAndApplyBuildConfig(getKubernetesClient(), namespace, projectName, gitUrl, annotations);
        return Results.success("Created BuildConfig");
    }


    public KubernetesClient getKubernetesClient() {
        if (kubernetesClient == null) {
            kubernetesClient = KubernetesClientHelper.createKubernetesClientForUser();
        }
        return kubernetesClient;
    }
}
