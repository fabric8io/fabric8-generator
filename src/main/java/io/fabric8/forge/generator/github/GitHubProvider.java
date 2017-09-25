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
package io.fabric8.forge.generator.github;

import io.fabric8.forge.generator.Configuration;
import io.fabric8.forge.generator.git.GitAccount;
import io.fabric8.forge.generator.git.GitProvider;
import io.fabric8.forge.generator.git.GitSecretNames;
import io.fabric8.forge.generator.git.WebHookDetails;
import io.fabric8.forge.generator.kubernetes.KubernetesClientHelper;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jboss.forge.addon.ui.result.navigation.NavigationResultBuilder;

import java.io.IOException;

/**
 */
public class GitHubProvider extends GitProvider {
    private Boolean configuredCorrectly;
    private GitAccount details;
    private boolean registerWebHooks = true;

    public GitHubProvider() {
        super("github");
    }

    @Override
    public void addCreateRepositoryStep(NavigationResultBuilder builder) {
        builder.add(GitHubRepoStep.class);
    }

    @Override
    public void addConfigureStep(NavigationResultBuilder builder) {
        builder.add(GitHubSetupCredentialsStep.class);
    }

    @Override
    public void addImportRepositoriesSteps(NavigationResultBuilder builder) {
        builder.add(GitHubImportPickOrganisationStep.class);
        builder.add(GitHubImportPickRepositoriesStep.class);
    }

    @Override
    public void addGitCloneStep(NavigationResultBuilder builder) {
        builder.add(GitHubCloneStep.class);
    }

    @Override
    public boolean isConfiguredCorrectly() {
        if (configuredCorrectly == null) {
            if (Configuration.isOnPremise()) {
                KubernetesClient kubernetesClient = KubernetesClientHelper
                        .createKubernetesClientForCurrentCluster();
                String namespace = KubernetesClientHelper.getUserSecretNamespace(kubernetesClient);
                String githubSecretName = GitSecretNames.GITHUB_SECRET_NAME;
                details = GitAccount.loadFromSecret(kubernetesClient, namespace, githubSecretName);

                configuredCorrectly = details != null && details.hasValidData();
            } else {
                configuredCorrectly = true;
            }
        }
        return configuredCorrectly;
    }

    @Override
    public void registerWebHook(GitAccount details, WebHookDetails webhook) throws IOException {
        if (registerWebHooks) {
            GitHubFacade facade = new GitHubFacade(details);
            facade.createWebHook(webhook);
        }
    }
}
