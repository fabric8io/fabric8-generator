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
package io.fabric8.forge.generator.quickstart;

import io.fabric8.forge.generator.cache.CacheFacade;
import io.fabric8.forge.generator.cache.CacheNames;
import io.fabric8.forge.generator.git.GitAccount;
import io.fabric8.forge.generator.git.GitOrganisationDTO;
import io.fabric8.forge.generator.github.CreateGitRepoStatusDTO;
import io.fabric8.forge.generator.github.GitHubFacade;
import io.fabric8.forge.generator.github.GitHubImportParameters;
import io.fabric8.forge.generator.github.GitHubProvider;
import io.fabric8.project.support.UserDetails;
import io.fabric8.utils.Strings;
import io.openshift.launchpad.ui.booster.DeploymentType;
import io.openshift.launchpad.ui.booster.ProjectInfoStep;
import io.openshift.launchpad.ui.input.ProjectName;
import org.infinispan.Cache;
import org.jboss.forge.addon.convert.Converter;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.context.UIValidationContext;
import org.jboss.forge.addon.ui.input.UIInput;
import org.jboss.forge.addon.ui.input.UISelectOne;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.NavigationResult;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;

import static io.fabric8.forge.generator.AttributeMapKeys.GIT_ACCOUNT;
import static io.fabric8.forge.generator.AttributeMapKeys.GIT_ORGANISATION;
import static io.fabric8.forge.generator.AttributeMapKeys.GIT_OWNER_NAME;
import static io.fabric8.forge.generator.AttributeMapKeys.GIT_PROVIDER;
import static io.fabric8.forge.generator.AttributeMapKeys.GIT_REPO_NAME;
import static io.fabric8.forge.generator.AttributeMapKeys.GIT_URL;
import static io.fabric8.forge.generator.git.AbstractGitRepoStep.getOrganisationName;
import static io.fabric8.forge.generator.git.AbstractGitRepoStep.importNewGitProject;
import static io.fabric8.forge.generator.git.AbstractGitRepoStep.updateGitURLInJenkinsfile;
import static io.fabric8.forge.generator.github.AbstractGithubStep.createGitHubFacade;
import static io.fabric8.forge.generator.pipeline.AbstractDevToolsCommand.getSelectionFolder;

/**
 */
public class Fabric8ProjectInfoStep extends ProjectInfoStep {
    private static final transient Logger LOG = LoggerFactory.getLogger(NewProjectWizard.class);

    @Inject
    @WithAttributes(label = "Organisation", required = true, description = "The github organisation to create this project inside")
    private UISelectOne<GitOrganisationDTO> gitOrganisation;

    @Inject
    protected CacheFacade cacheManager;

    protected Cache<String, GitAccount> githubAccountCache;
    protected Cache<String, Collection<GitOrganisationDTO>> organisationsCache;
    private Collection<GitOrganisationDTO> organisations = new ArrayList<>();

    /**
     * The name of the upstream repo
     */
    private String origin = "origin";
    /**
     * The default branch we make on creating repos
     */
    private String branch = "master";

    // TODO abstract so can work with gogs
    private GitHubFacade github;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(getClass()).name("Fabric8: Project Info")
                .description("Project Information")
                .category(Categories.create("Fabric8"));
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        UIContext uiContext = builder.getUIContext();
        String organisationsCacheKey = CacheNames.GITHUB_ORGANISATIONS;

        // TODO use different caches for organisations based on the git provider
        if (false) {
            // gogs
            organisationsCacheKey = CacheNames.GOGS_ORGANISATIONS;
        }
        this.githubAccountCache = cacheManager.getCache(CacheNames.GITHUB_ACCOUNT_FROM_SECRET);
        this.organisationsCache = cacheManager.getCache(organisationsCacheKey);

        this.github = createGitHubFacade(uiContext, githubAccountCache);

        if (github != null && github.isDetailsValid()) {
            String orgKey = github.getDetails().getUserCacheKey();
            organisations = organisationsCache.computeIfAbsent(orgKey, k -> github.loadGithubOrganisations(builder));
        }
        gitOrganisation.setValueChoices(organisations);
        gitOrganisation.setItemLabelConverter(new Converter<GitOrganisationDTO, String>() {
            @Override
            public String convert(GitOrganisationDTO organisation) {
                return organisation.getName();
            }
        });
        String userName = github.getDetails().getUsername();
        if (Strings.isNotBlank(userName)) {
            for (GitOrganisationDTO organisation : organisations) {
                if (userName.equals(organisation.getName())) {
                    gitOrganisation.setDefaultValue(organisation);
                    break;
                }
            }
        }

        super.initializeUI(builder);
    }

    @Override
    public void validate(UIValidationContext context) {
        // lets ignore the mission validation as its not suitable for fabric8
        // super.validate(context);

        if (github == null || !github.isDetailsValid()) {
            // invoked too early before the github account is setup - lets return silently
            return;
        }
        String orgName = getOrganisationName(gitOrganisation.getValue());
        String repoName = getGithubRepositoryNameValue();
        if (Strings.isNotBlank(orgName)) {
            if (Strings.isNotBlank(repoName)) {
                github.validateRepositoryName(getNamed(), context, orgName, repoName);
            }
        }
    }


    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        return null;
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        if (github == null) {
            return Results.fail("No github account setup");
        }

        String org = getOrganisationName(gitOrganisation.getValue());
        String repo = getGitHubRepositoryName().getValue();
        if (Strings.isNullOrBlank(repo)) {
            repo = getNamed().getValue();
        }

        String orgOrNoUser = org;
        if (Strings.isNotBlank(org)) {
            orgOrNoUser = "user";
        }
        String orgAndRepo = orgOrNoUser + "/" + repo;
        LOG.info("Creating github repository " + orgAndRepo);

        UIContext uiContext = context.getUIContext();
        File basedir = getSelectionFolder(uiContext);
        if (basedir == null || !basedir.exists() || !basedir.isDirectory()) {
            return Results.fail("No project directory exists! " + basedir);
        }

        String gitOwnerName = org;

        GitHubImportParameters importParameters = new GitHubImportParameters(org, repo, orgAndRepo, github);
        uiContext.getAttributeMap().put(GitHubImportParameters.class, importParameters);

        return super.execute(context);
    }

    @Override
    protected boolean isShowArtifactId() {
        return false;
    }

    @Override
    protected void addDeploymentProperties(UIBuilder builder, DeploymentType deploymentType) {
        if (organisations.size() > 1) {
            builder.add(gitOrganisation);
        }
        // there's no need for github repo and name really - its just confusing and users may make mistakes?
        //builder.add(getGitHubRepositoryName());
        builder.add(getNamed());

    }
}
