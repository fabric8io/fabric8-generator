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

import io.fabric8.forge.generator.AttributeMapKeys;
import io.fabric8.forge.generator.Configuration;
import io.fabric8.forge.generator.cache.CacheFacade;
import io.fabric8.forge.generator.cache.CacheNames;
import io.fabric8.forge.generator.git.AbstractGitRepoStep;
import io.fabric8.forge.generator.git.GitAccount;
import io.fabric8.forge.generator.git.GitSecretNames;
import org.jboss.forge.addon.ui.context.UIContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public abstract class AbstractGithubStep extends AbstractGitRepoStep {
    private static final transient Logger LOG = LoggerFactory.getLogger(AbstractGithubStep.class);

    public AbstractGithubStep() {
        super(CacheNames.GITHUB_ACCOUNT_FROM_SECRET, CacheNames.GITHUB_ORGANISATIONS);
    }

    public AbstractGithubStep(CacheFacade cacheManager) {
        super(CacheNames.GITHUB_ACCOUNT_FROM_SECRET, CacheNames.GITHUB_ORGANISATIONS, cacheManager);
    }

    protected GitHubFacade createGithubFacade(UIContext context) {
        GitAccount details = (GitAccount) context.getAttributeMap().get(AttributeMapKeys.GIT_ACCOUNT);
        if (details == null) {
            if (Configuration.isOnPremise()) {
                details = GitAccount.loadGitDetailsFromSecret(accountCache, GitSecretNames.GITHUB_SECRET_NAME, context);
            } else {
                details = GitAccount.loadFromSaaS(context);
            }
        }
        if (details == null) {
            LOG.warn("No git details found - assuming local testing mode!");
            return new GitHubFacade();
        }
        return new GitHubFacade(details);
    }
}
