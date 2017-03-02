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
package io.fabric8.forge.generator.gogs;

import io.fabric8.forge.generator.EnvironmentVariables;
import io.fabric8.forge.generator.git.EnvironmentVariablePrefixes;
import io.fabric8.forge.generator.git.GitAccount;
import io.fabric8.forge.generator.git.GitOrganisationDTO;
import io.fabric8.project.support.UserDetails;
import io.fabric8.repo.git.CreateRepositoryDTO;
import io.fabric8.repo.git.GitRepoClient;
import io.fabric8.repo.git.RepositoryDTO;
import io.fabric8.utils.Strings;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIValidationContext;
import org.jboss.forge.addon.ui.input.UIInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

import static io.fabric8.forge.generator.pipeline.JenkinsPipelineLibrary.getSystemPropertyOrDefault;


/**
 */
public class GogsFacade {
    private static final transient Logger LOG = LoggerFactory.getLogger(GogsFacade.class);
    private final GitAccount details;
    private GitRepoClient gogs;


    public GogsFacade() {
        this(GitAccount.createViaEnvironmentVariables(EnvironmentVariablePrefixes.GOGS));
    }

    public GogsFacade(GitAccount details) {
        this.details = details;

        String username = details.getUsername();
        String password = details.getPassword();

        String address = getSystemPropertyOrDefault(EnvironmentVariables.GOGS_URL, "http://gogs");
        LOG.info("Connecting to gogs via url: " + address);

        try {
            this.gogs = new GitRepoClient(address, username, password);
        } catch (Exception e) {
            LOG.warn("Failed to create  client for user " + details.getUsername() + ". " + e, e);
        }
    }

    public Collection<GitOrganisationDTO> loadOrganisations(UIBuilder builder) {
        SortedSet<GitOrganisationDTO> organisations = new TreeSet();
        String username = details.getUsername();
        if (Strings.isNotBlank(username)) {
            organisations.add(new GitOrganisationDTO(username));
        }
        GitRepoClient gogs = this.gogs;
        if (gogs != null) {
/*
            try {
                Map<String, GHOrganization> map = gogs.getMyOrganizations();
                if (map != null) {
                    Collection<GHOrganization> organizations = map.values();
                    for (GHOrganization organization : organizations) {

                        GitOrganisationDTO dto = new GitOrganisationDTO(organization);
                        if (dto.getName() != null) {
                            organisations.add(dto);
                        }
                    }
                }
            } catch (IOException e) {
                LOG.warn("Failed to load  organisations for user: " + details.getUsername() + " due to : " + e, e);
            }
*/
        }
        return organisations;
    }

    public void validateRepositoryName(UIInput<String> input, UIValidationContext context, String orgName,
                                       String repoName) {
        GitRepoClient gogs = this.gogs;
        if (gogs != null) {
            try {
                RepositoryDTO repository = gogs.getRepository(orgName, repoName);
                if (repository != null) {
                    context.addValidationError(input, "The repository " + repoName + " already exists!");
                }
            } catch (Exception e) {
                LOG.warn("Caught exception looking up  repository " + orgName + "/" + repoName + ". " + e, e);
            }
        }
    }

    public UserDetails createUserDetails(String gitUrl) {
        return new UserDetails(gitUrl, gitUrl, details.getUsername(), details.getPassword(), details.getEmail());
    }

    public GitAccount getDetails() {
        return details;
    }

    public RepositoryDTO createRepository(String orgName, String repoName, String description) throws IOException {
        CreateRepositoryDTO arguments = new CreateRepositoryDTO();
        arguments.setName(repoName);
        // TODO
        //arguments.setTeamId(teamNumber);
        if (Strings.isNotBlank(description)) {
            arguments.setDescription(description);
        }
        return gogs.createRepository(arguments);
    }

    public boolean isDetailsValid() {
        return details != null && GitAccount.isValid(details);
    }
}
