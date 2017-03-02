/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package io.fabric8.forge.generator.gogs;

import io.fabric8.forge.generator.AttributeMapKeys;
import io.fabric8.forge.generator.Configuration;
import io.fabric8.forge.generator.git.AbstractGitRepoStep;
import io.fabric8.forge.generator.git.GitAccount;
import io.fabric8.forge.generator.git.GitOrganisationDTO;
import io.fabric8.forge.generator.git.GitSecretNames;
import io.fabric8.project.support.UserDetails;
import io.fabric8.repo.git.RepositoryDTO;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import static io.fabric8.forge.generator.AttributeMapKeys.GIT_URL;

/**
 * Lets the user configure the Gogs organisation and repo name that they want to pick for a new project
 */
public class GogsRepoStep extends AbstractGitRepoStep implements UIWizardStep {
    final transient Logger LOG = LoggerFactory.getLogger(this.getClass());
    @Inject
    @WithAttributes(label = "Organisation", required = true, description = "The gogs organisation to create this project inside")
    private UISelectOne<GitOrganisationDTO> gitOrganisation;
    @Inject
    @WithAttributes(label = "Repository", required = true, description = "The name of the new gogs repository")
    private UIInput<String> gitRepository;

    @Inject
    @WithAttributes(label = "Description", description = "The description of the new gogs repository")
    private UIInput<String> gitRepoDescription;

    private GogsFacade gogs;


    public void initializeUI(final UIBuilder builder) throws Exception {
        super.initializeUI(builder);

        this.gogs = createGitFacade(builder.getUIContext());

        // TODO cache this per user every say 30 seconds!
        Collection<GitOrganisationDTO> organisations = new ArrayList<>();
        if (gogs != null && gogs.isDetailsValid()) {
            String orgKey = gogs.getDetails().getUserCacheKey();
            organisations = organisationsCache.computeIfAbsent(orgKey, k -> gogs.loadOrganisations(builder));
        }
        gitOrganisation.setValueChoices(organisations);
        gitOrganisation.setItemLabelConverter(new Converter<GitOrganisationDTO, String>() {
            @Override
            public String convert(GitOrganisationDTO organisation) {
                return organisation.getName();
            }
        });
        String userName = gogs.getDetails().getUsername();
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
        builder.add(gitOrganisation);
        builder.add(gitRepository);
        builder.add(gitRepoDescription);
    }

    private GogsFacade createGitFacade(UIContext context) {
        GitAccount details = (GitAccount) context.getAttributeMap().get(AttributeMapKeys.GIT_ACCOUNT);
        if (details != null) {
            return new GogsFacade(details);
        } else if (Configuration.isOnPremise()) {
            details = GitAccount.loadGitDetailsFromSecret(accountCache, GitSecretNames.GOGS_SECRET_NAME);
            return new GogsFacade(details);
        }

        // lets try find it from KeyCloak etc...
        return new GogsFacade();
    }

    @Override
    public void validate(UIValidationContext context) {
        if (gogs == null || !gogs.isDetailsValid()) {
            // invoked too early before the git account is setup - lets return silently
            return;
        }
        String orgName = getOrganisationName(gitOrganisation.getValue());
        String repoName = gitRepository.getValue();

        if (Strings.isNotBlank(orgName) && Strings.isNotBlank(repoName)) {
            gogs.validateRepositoryName(gitRepository, context, orgName, repoName);
        }
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        if (gogs == null) {
            return Results.fail("No gogs account setup");
        }
        String org = getOrganisationName(gitOrganisation.getValue());
        String repo = gitRepository.getValue();

        LOG.info("Creating gogs repository " + org + "/" + repo);

        UIContext uiContext = context.getUIContext();
        File basedir = getSelectionFolder(uiContext);
        if (basedir == null || !basedir.exists() || !basedir.isDirectory()) {
            return Results.fail("No project directory exists! " + basedir);
        }

        String gitUrl = "https://gogs/" + org + "/" + repo + ".git";
        try {
            RepositoryDTO repository = gogs.createRepository(org, repo, gitRepoDescription.getValue());
            String cloneUrl = repository.getCloneUrl();
            if (Strings.isNotBlank(cloneUrl)) {
                gitUrl = cloneUrl;
            } else {
                String htmlUrl = repository.getHtmlUrl();
                if (Strings.isNotBlank(htmlUrl)) {
                    gitUrl = htmlUrl + ".git";
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to create repository  " + org + "/" + repo + " " + e, e);
            return Results.fail("Failed to create repository  " + org + "/" + repo + " " + e, e);
        }

        LOG.info("Created gogs repository: " + gitUrl);
        uiContext.getAttributeMap().put(GIT_URL, gitUrl);

        Result result = updateGitURLInJenkinsfile(basedir, gitUrl);
        if (result != null) {
            return result;
        }

        try {
            UserDetails userDetails = gogs.createUserDetails(gitUrl);
            importNewGitProject(userDetails, basedir, "Initial import", gitUrl);
        } catch (Exception e) {
            LOG.error("Failed to import project to " + gitUrl + " " + e, e);
            return Results.fail("Failed to import project to " + gitUrl + ". " + e, e);
        }
        return Results.success();
    }
}
