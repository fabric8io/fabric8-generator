/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package io.fabric8.forge.generator.github;

import io.fabric8.forge.generator.AttributeMapKeys;
import io.fabric8.forge.generator.Configuration;
import io.fabric8.forge.generator.git.AbstractGitRepoStep;
import io.fabric8.forge.generator.git.GitAccount;
import io.fabric8.forge.generator.git.GitOrganisationDTO;
import io.fabric8.forge.generator.git.GitSecretNames;
import io.fabric8.project.support.UserDetails;
import io.fabric8.utils.Strings;
import org.jboss.forge.addon.convert.Converter;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UIValidationContext;
import org.jboss.forge.addon.ui.input.UIInput;
import org.jboss.forge.addon.ui.input.UISelectOne;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.wizard.UIWizardStep;
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
import static io.fabric8.forge.generator.AttributeMapKeys.GIT_OWNER_NAME;
import static io.fabric8.forge.generator.AttributeMapKeys.GIT_REPO_NAME;
import static io.fabric8.forge.generator.AttributeMapKeys.GIT_URL;

/**
 * Lets the user configure the GitHub organisation and repo name that they want to pick for a new project
 */
public class GithubRepoStep extends AbstractGitRepoStep implements UIWizardStep {
    final transient Logger LOG = LoggerFactory.getLogger(this.getClass());
    @Inject
    @WithAttributes(label = "Organisation", required = true, description = "The github organisation to create this project inside")
    private UISelectOne<GitOrganisationDTO> gitOrganisation;
    @Inject
    @WithAttributes(label = "Repository", required = true, description = "The name of the new github repository")
    private UIInput<String> gitRepository;

    @Inject
    @WithAttributes(label = "Description", description = "The description of the new github repository")
    private UIInput<String> gitRepoDescription;

    private GitHubFacade github;

    public void initializeUI(final UIBuilder builder) throws Exception {
        super.initializeUI(builder);

        this.github = createGithubFacade(builder.getUIContext());

        // TODO cache this per user every say 30 seconds!
        Collection<GitOrganisationDTO> organisations = new ArrayList<>();
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
        String projectName = getProjectName(builder.getUIContext());
        gitRepository.setDefaultValue(projectName);
        if (organisations.size() > 1) {
            builder.add(gitOrganisation);
        }
        builder.add(gitRepository);
        builder.add(gitRepoDescription);
    }

    private GitHubFacade createGithubFacade(UIContext context) {
        GitAccount details = (GitAccount) context.getAttributeMap().get(AttributeMapKeys.GIT_ACCOUNT);
        if (details == null) {
            if (Configuration.isOnPremise()) {
                details = GitAccount.loadGitDetailsFromSecret(accountCache, GitSecretNames.GITHUB_SECRET_NAME);
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

    @Override
    public void validate(UIValidationContext context) {
        if (github == null || !github.isDetailsValid()) {
            // invoked too early before the github account is setup - lets return silently
            return;
        }
        String orgName = getOrganisationName(gitOrganisation.getValue());
        String repoName = gitRepository.getValue();

        if (Strings.isNotBlank(orgName) && Strings.isNotBlank(repoName)) {
            github.validateRepositoryName(gitRepository, context, orgName, repoName);
        }
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        if (github == null) {
            return Results.fail("No github account setup");
        }
        String org = getOrganisationName(gitOrganisation.getValue());
        String repo = gitRepository.getValue();

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
        String gitUrl = "https://github.com/" + orgAndRepo + ".git";
        try {
            GHRepository repository = github.createRepository(org, repo, gitRepoDescription.getValue());
            URL htmlUrl = repository.getHtmlUrl();
            if (htmlUrl != null) {
                gitUrl = htmlUrl.toString() + ".git";
            }
            gitOwnerName = repository.getOwnerName();
        } catch (IOException e) {
            LOG.error("Failed to create repository  " + orgAndRepo + " " + e, e);
            return Results.fail("Failed to create repository  " + orgAndRepo + " " + e, e);
        }

        LOG.info("Created repository: " + gitUrl);
        uiContext.getAttributeMap().put(GIT_URL, gitUrl);
        uiContext.getAttributeMap().put(GIT_OWNER_NAME, gitOwnerName);
        uiContext.getAttributeMap().put(GIT_REPO_NAME, repo);
        uiContext.getAttributeMap().put(GIT_ACCOUNT, github.getDetails());

        Result result = updateGitURLInJenkinsfile(basedir, gitUrl);
        if (result != null) {
            return result;
        }

        try {
            UserDetails userDetails = github.createUserDetails(gitUrl);
            importNewGitProject(userDetails, basedir, "Initial import", gitUrl);
        } catch (Exception e) {
            LOG.error("Failed to import project to " + gitUrl + " " + e, e);
            return Results.fail("Failed to import project to " + gitUrl + ". " + e, e);
        }
        return Results.success();
    }


}
