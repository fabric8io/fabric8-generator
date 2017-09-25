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
import io.fabric8.forge.generator.git.GitRepositoryDTO;
import io.fabric8.forge.generator.git.WebHookDetails;
import io.fabric8.project.support.UserDetails;
import io.fabric8.utils.Strings;
import io.fabric8.utils.URLUtils;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIValidationContext;
import org.jboss.forge.addon.ui.input.UIInput;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.TreeMap;

import static java.util.Collections.unmodifiableMap;

/**
 */
public class GitHubFacade {
    private static final transient Logger LOG = LoggerFactory.getLogger(GitHubFacade.class);
    public static final String MY_PERSONAL_GITHUB_ACCOUNT = "My personal github account";
    private final GitAccount details;
    private GHMyself myself;

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
            this.myself = this.github.getMyself();
            String login = myself.getLogin();
            if (Strings.isNotBlank(login) && !Objects.equals(login, username)) {
                LOG.debug("Switching the github user name from " + username + " to " + login);
                details.setUsername(login);
            }
            // lets always use the github email address
            String email = myself.getEmail();
            if (Strings.isNotBlank(email)) {
                details.setEmail(email);
            }
        } catch (IOException e) {
            LOG.warn("Failed to create github client for user " + details.getUsername());
        }
    }


    public Collection<GitOrganisationDTO> loadGithubOrganisations(UIBuilder builder) {
        SortedSet<GitOrganisationDTO> organisations = new TreeSet<>();
        String username = details.getUsername();
        if (Strings.isNotBlank(username)) {
            organisations.add(new GitOrganisationDTO(username, MY_PERSONAL_GITHUB_ACCOUNT));
        }
        GitHub github = this.github;
        if (github != null) {
            try {
                LOG.debug("Loading github organisations for " + username);
                Map<String, GHOrganization> map = github.getMyOrganizations();
                if (map != null) {
                    Collection<GHOrganization> organizations = map.values();
                    for (GHOrganization organization : organizations) {
                        GitOrganisationDTO dto = new GitOrganisationDTO(organization, username);
                        if (dto.isValid()) {
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

    public Collection<GitRepositoryDTO> getRepositoriesForOrganisation(String orgName) {
        Set<GitRepositoryDTO> answer = new TreeSet<>();
        GitHub github = this.github;
        if (github != null) {
            try {

                Map<String, GHRepository> repositories;
                String username = details.getUsername();
                if (Strings.isNullOrBlank(orgName) || orgName.equals(username)) {
                    Map<String,GHRepository> repositoriesTree = new TreeMap<>();
                    // With OWNER, retrieve public and private repositories owned by current user (only).
                    for (GHRepository r : github.getMyself().listRepositories(100, GHMyself.RepositoryListFilter.OWNER)) {
                        repositoriesTree.put(r.getName(),r);
                    }
                    repositories = unmodifiableMap(repositoriesTree);
                 } else {
                    repositories = github.getOrganization(orgName).getRepositories();
                }
                if (repositories != null) {
                    for (Map.Entry<String, GHRepository> entry : repositories.entrySet()) {
                        String key = entry.getKey();
                        GHRepository repository = entry.getValue();
                        answer.add(new GitRepositoryDTO(key, repository));
                    }
                }
            } catch (IOException e) {
                LOG.warn("Caught exception looking up github repositories for " + orgName + ". " + e, e);
            }
        }
        return answer;
    }

    public UserDetails createUserDetails(String gitUrl) {
        return new UserDetails(gitUrl, gitUrl, details.getUsername(), details.tokenOrPassword(), getEmail());
    }

    public boolean hasFile(String org, String repoName, String fileName) {
        boolean hasFile = false;
        GHContent content = null;
        try {
            content = github.getRepository(org + "/" + repoName).getFileContent(fileName);
            if (content != null) {
                hasFile = true;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return hasFile;
        }
        return hasFile;
    }

    public GHMyself getMyself() {
        if (myself == null) {
            try {
                myself = this.github.getMyself();
                if (myself == null) {
                    LOG.warn("Could not find valid github.getMyself()");
                }
            } catch (IOException e) {
                LOG.warn("Could not load github.getMyself(): " + e, e);
            }
            
        }
        return myself;
    }

    public String getEmail() {
        String email = details.getEmail();
        if (Strings.isNullOrBlank(email)) {
            GHMyself gitMyself = getMyself();
            if (gitMyself != null) {
                try {
                    email = gitMyself.getEmail();
                    if (Strings.isNullOrBlank(email)) {
                        LOG.warn("No email found on the user settings or GitHub myself, default to invalid address");
                        // github user might have chosen to keep email address private
                        // populate it with invalid address as null is invalid for org.eclipse.jgit.lib.PersonIdent
                        email = "private@github.com";

                    }
                } catch (IOException e) {
                    LOG.warn("Could not get github.getMyself().getEmail(): " + e, e);
                }
            }
        }
        return email;
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
        String repoName = webhook.getRepositoryName();


        String orgName = webhook.getGitOwnerName();
        GHRepository repository = github.getRepository(orgName + "/" + repoName);
        String webhookUrl = webhook.getWebhookUrl();

        removeOldWebHooks(repository, webhookUrl);

        Map<String, String> config = new HashMap<>();
        config.put("url", webhookUrl);
        config.put("insecure_ssl", "1");
        config.put("content_type", "json");
        config.put("secret", webhook.getSecret());
        List<GHEvent> events = new ArrayList<>();
        events.add(GHEvent.ALL);
        GHHook hook = repository.createHook("web", config, events, true);
        if (hook != null) {
            LOG.info("Created WebHook " + hook.getName() + " with ID " + hook.getId() + " for " + repository.getFullName() + " on URL " + webhookUrl);
        }
        //registerGitWebHook(details, webhook.getWebhookUrl(), webhook.getGitOwnerName(), repoName, webhook.getSecret());
    }

    private void removeOldWebHooks(GHRepository repository, String webhookUrl) {
        List<GHHook> hooks;
        try {
            hooks = repository.getHooks();
        } catch (IOException e) {
            LOG.warn("Failed to find WebHooks for repository " + repository.getFullName() + " due to : " + e, e);
            return;
        }
        if (hooks != null) {
            for (GHHook hook : hooks) {
                Map<String, String> config = hook.getConfig();
                if (config != null) {
                    String url = config.get("url");
                    if (url != null && webhookUrl.equals(url)) {
                        LOG.info("Removing WebHook " + hook.getName() + " with ID " + hook.getId() + " for " + repository.getFullName() + " on URL " + webhookUrl);
                        try {
                            hook.delete();
                        } catch (IOException e) {
                            LOG.warn("Failed to remove WebHook " + hook.getName() + " with ID " + hook.getId() + " for " + repository.getFullName() + " on URL " + webhookUrl + " due to: " + e, e);
                        }
                    }
                }
            }
        }
    }

    private void registerGitWebHook(GitAccount details, String webhookUrl, String gitOwnerName, String gitRepoName, String botSecret) throws IOException {

        LOG.info("Creating webhook at " + webhookUrl);
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


}
