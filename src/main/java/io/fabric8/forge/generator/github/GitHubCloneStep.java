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

import io.fabric8.forge.generator.cache.CacheNames;
import io.fabric8.forge.generator.git.CloneRepoAttributes;
import io.fabric8.forge.generator.git.GitCloneStep;
import io.fabric8.project.support.UserDetails;
import org.jboss.forge.addon.ui.context.UIBuilder;

import java.io.File;

import static io.fabric8.forge.generator.github.AbstractGitHubStep.createGitHubFacade;

/**
 * Performs a git clone of a repo via github
 */
public class GitHubCloneStep extends GitCloneStep {
    private GitHubFacade github;

    public GitHubCloneStep() {
        super(CacheNames.GITHUB_ACCOUNT_FROM_SECRET, CacheNames.GITHUB_ORGANISATIONS);
    }

    public void initializeUI(final UIBuilder builder) throws Exception {
        super.initializeUI(builder);

        this.github = createGitHubFacade(builder.getUIContext(), accountCache);
    }

    @Override
    protected CloneRepoAttributes createCloneRepoAttributes(String gitOwnerName, String gitRepoName, File dir) {
        String uri = "https://github.com/" + gitOwnerName + "/" + gitRepoName + ".git";
        UserDetails userDetails = github.createUserDetails(uri);
        return new CloneRepoAttributes(userDetails, uri, dir);
    }
}


