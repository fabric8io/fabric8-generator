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

import io.fabric8.forge.generator.cache.CacheFacade;
import io.fabric8.forge.generator.cache.CacheNames;
import io.fabric8.forge.generator.kubernetes.KubernetesClientHelper;
import io.fabric8.forge.generator.pipeline.AbstractDevToolsCommand;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.utils.Strings;
import org.infinispan.Cache;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.input.UIInput;
import org.jboss.forge.addon.ui.result.Result;

import javax.inject.Inject;

/**
 */
public abstract class AbstractGitSetupCredentialsStep extends AbstractDevToolsCommand {
    protected Cache<String, GitAccount> accountCache;
    @Inject
    private CacheFacade cacheManager;
    private KubernetesClient kubernetesClient;
    private String namespace;

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        super.initializeUI(builder);

        this.accountCache = cacheManager.getCache(CacheNames.GITHUB_ACCOUNT_FROM_SECRET);
    }

    protected GitAccount loadGitAccountFromSecret(String githubSecretName) {
        kubernetesClient = KubernetesClientHelper.createKubernetesClientForCurrentCluster();
        namespace = KubernetesClientHelper.getUserSecretNamespace(kubernetesClient);
        return GitAccount.loadFromSecret(kubernetesClient, namespace, githubSecretName);
    }

    protected void setIfNotBlank(UIInput<String> input, String value) {
        if (Strings.isNotBlank(value)) {
            input.setValue(value);
        }
    }

    protected Result storeGitAccountInSecret(GitAccount details, String githubSecretName) {
        final String key = KubernetesClientHelper.getUserCacheKey();
        accountCache.evict(key);
        return GitAccount.storeGitDetailsInSecret(kubernetesClient, namespace, githubSecretName, details);
    }
}
