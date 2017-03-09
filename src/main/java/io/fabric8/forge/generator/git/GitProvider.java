/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.forge.generator.git;

import io.fabric8.forge.generator.Configuration;
import io.fabric8.forge.generator.github.GitHubProvider;
import io.fabric8.forge.generator.gogs.GogsProvider;
import io.fabric8.forge.generator.kubernetes.KubernetesClientHelper;
import io.fabric8.kubernetes.api.ServiceNames;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jboss.forge.addon.ui.result.navigation.NavigationResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 */
public abstract class GitProvider {
    private static final transient Logger LOG = LoggerFactory.getLogger(GitProvider.class);

    private final String name;

    public GitProvider(String name) {
        this.name = name;
    }

    public static List<GitProvider> loadGitProviders() {
        List<GitProvider> answer = new ArrayList();
        answer.add(new GitHubProvider());

        if (Configuration.isOnPremise()) {
            // check for gogs / gitlab providers based on available services in kubernetes/openshift
            KubernetesClient kubernetesClient = KubernetesClientHelper.createKubernetesClientForUser();
            String namespace = KubernetesClientHelper.getDiscoveryNamespace(kubernetesClient);

            if (hasService(kubernetesClient, namespace, ServiceNames.GOGS)) {
                answer.add(new GogsProvider());
            }
/*
            // TODO support gitlab!
            if (hasService(kubernetesClient, namespace, ServiceNames.GITLAB)) {
                answer.add(new GitLabProvider());
            }
*/
        }
        LOG.info("Loaded git providers: " + answer);
        return answer;
    }

    protected static boolean hasService(KubernetesClient kubernetesClient, String namespace, String name) {
        try {
            Service service = kubernetesClient.services().inNamespace(namespace).withName(name).get();
            return service != null;
        } catch (Exception e) {
            LOG.warn("Failed to find service " + namespace + "/" + name + ". " + e, e);
            return false;
        }
    }

    @Override
    public String toString() {
        return "GitProvider{" +
                "name='" + name + '\'' +
                '}';
    }

    public String getName() {
        return name;
    }

    public abstract void addCreateRepositoryStep(NavigationResultBuilder builder);

    public abstract void addImportRepositoriesSteps(NavigationResultBuilder builder);

    public abstract boolean isConfiguredCorrectly();

    public abstract void addConfigureStep(NavigationResultBuilder builder);

    public abstract void registerWebHook(GitAccount details, WebHookDetails webhook) throws IOException;

}
