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

import io.fabric8.forge.generator.git.EnvironmentVariablePrefixes;
import io.fabric8.forge.generator.git.GitAccount;
import io.fabric8.forge.generator.git.GitOrganisationDTO;
import io.fabric8.forge.generator.git.WebHookDetails;
import io.fabric8.project.support.UserDetails;
import io.fabric8.utils.Strings;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIValidationContext;
import org.jboss.forge.addon.ui.input.UIInput;
import org.kohsuke.github.GHCreateRepositoryBuilder;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 */
public class GitHubFacade {
    private static final transient Logger LOG = LoggerFactory.getLogger(GitHubFacade.class);
    private final GitAccount details;

    private GitHub github;

    public GitHubFacade() {
        this(GitAccount.createViaEnvironmentVariables(EnvironmentVariablePrefixes.GITHUB));
    }

    public GitHubFacade(GitAccount details) {
        this.details = details;

        String username = details.getUsername();
        String token = details.getToken();
        String password = details.getPassword();

/*
        // TODO
        // Use a cache for responses so we don't count HTTP 304 against our API quota
        final File githubCacheFolder = GitHubLocalCache.INSTANCE.getCacheFolder();
        final Cache cache = new Cache(githubCacheFolder, TENMB);
        final GitHubBuilder ghb = new GitHubBuilder()
                .withConnector(new OkHttpConnector(new OkUrlFactory(new OkHttpClient().setCache(cache))));
*/
        try {
            final GitHubBuilder ghb = new GitHubBuilder();
            if (Strings.isNotBlank(username) && Strings.isNotBlank(password)) {
                ghb.withPassword(username, password);
            } else if (Strings.isNotBlank(token)) {
                if (Strings.isNotBlank(username)) {
                    ghb.withOAuthToken(token, username);
                } else {
                    ghb.withOAuthToken(token);
                }
            }
            this.github = ghb.build();
        } catch (IOException e) {
            LOG.warn("Failed to create github client for user " + details.getUsername());
        }
    }


    public Collection<GitOrganisationDTO> loadGithubOrganisations(UIBuilder builder) {
        SortedSet<GitOrganisationDTO> organisations = new TreeSet();
        String username = details.getUsername();
        if (Strings.isNotBlank(username)) {
            organisations.add(new GitOrganisationDTO(username));
        }
        GitHub github = this.github;
        if (github != null) {
            try {
                LOG.info("Loading github organisations for " + username);
                Map<String, GHOrganization> map = github.getMyOrganizations();
                if (map != null) {
                    Collection<GHOrganization> organizations = map.values();
                    for (GHOrganization organization : organizations) {

                        GitOrganisationDTO dto = new GitOrganisationDTO(organization);
                        if (dto.getName() != null) {
                            organisations.add(dto);
                        }
                    }
                }
            } catch (HttpException e) {
                if (e.getResponseCode() == 403) {
                    // don't have the karma for listing organisations
                    LOG.warn("User doesn't have karma to list organisations: " + e);
                    return organisations;
                } else {
                    LOG.warn("Failed to load github organisations for user: " + details.getUsername() + " due to : " + e, e);
                }
            } catch (IOException e) {
                LOG.warn("Failed to load github organisations for user: " + details.getUsername() + " due to : " + e, e);
            }
        }
        return organisations;
    }

    public void validateRepositoryName(UIInput<String> input, UIValidationContext context, String orgName,
                                       String repoName) {
        GitHub github = this.github;
        if (github != null) {
            String name = orgName + "/" + repoName;
            try {
                GHRepository repository = github.getRepository(name);
                if (repository != null) {
                    context.addValidationError(input, "The repository " + repoName + " already exists!");
                }
            } catch (FileNotFoundException e) {
                // repo doesn't exist
            } catch (IOException e) {
                LOG.warn("Caught exception looking up github repository " + name + ". " + e, e);
            }
        }
    }

    public Collection<String> getRespositoriesForOrganisation(String orgName) {
        Set<String> answer = new TreeSet<>();
        GitHub github = this.github;
        if (github != null) {
            try {

                Map<String, GHRepository> repositories;
                String username = details.getUsername();
                if (Strings.isNullOrBlank(orgName) || orgName.equals(username)) {
                    repositories = github.getUser(username).getRepositories();
                } else {
                    repositories = github.getOrganization(orgName).getRepositories();
                }
                answer.addAll(repositories.keySet());
            } catch (IOException e) {
                LOG.warn("Caught exception looking up github repositories for " + orgName + ". " + e, e);
            }
        }
        return answer;
    }

    public UserDetails createUserDetails(String gitUrl) {
        return new UserDetails(gitUrl, gitUrl, details.getUsername(), details.tokenOrPassword(), details.getEmail());
    }

    public GitAccount getDetails() {
        return details;
    }

    public GHRepository createRepository(String orgName, String repoName, String description) throws IOException {
        GHCreateRepositoryBuilder builder;
        if (Strings.isNullOrBlank(orgName) || orgName.equals(details.getUsername())) {
            builder = github.createRepository(repoName);
        } else {
            builder = github.getOrganization(orgName).createRepository(repoName);
        }
        // TODO link to the space URL?
        builder.private_(false)
                .homepage("")
                .issues(false)
                .downloads(false)
                .wiki(false);

        if (Strings.isNotBlank(description)) {
            builder.description(description);
        }
        return builder.create();
    }

    public boolean isDetailsValid() {
        return details != null && GitAccount.isValid(details);
    }

    public void createWebHook(WebHookDetails webhook) throws IOException {
        String orgName = webhook.getOrganisation();
        String repoName = webhook.getRepositoryName();
        GHRepository repository = github.getRepository(orgName + "/" + repoName);
        repository.createWebHook(webhook.getWebhookURL());

    }

}
