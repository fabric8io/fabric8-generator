/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package io.fabric8.forge.generator.github;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.devops.ProjectConfigs;
import io.fabric8.forge.generator.AttributeMapKeys;
import io.fabric8.forge.generator.Configuration;
import io.fabric8.forge.generator.cache.CacheNames;
import io.fabric8.forge.generator.cache.Caches;
import io.fabric8.forge.generator.git.GitAccount;
import io.fabric8.forge.generator.git.GitSecretNames;
import io.fabric8.forge.generator.pipeline.AbstractDevToolsCommand;
import io.fabric8.project.support.GitUtils;
import io.fabric8.project.support.UserDetails;
import io.fabric8.utils.IOHelpers;
import io.fabric8.utils.Strings;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.forge.addon.convert.Converter;
import org.jboss.forge.addon.resource.ResourceFactory;
import org.jboss.forge.addon.templates.TemplateFactory;
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

import static io.fabric8.forge.generator.AttributeMapKeys.GIT_URL;

/**
 * Lets the user configure the GitHub organisation and repo name that they want to pick for a new project
 */
public class GithubRepoStep extends AbstractDevToolsCommand implements UIWizardStep {
    final transient Logger LOG = LoggerFactory.getLogger(this.getClass());
    @Inject
    TemplateFactory templateFactory;
    @Inject
    ResourceFactory resourceFactory;
    @Inject
    @WithAttributes(label = "Organisation", required = true, description = "The github organisation to create this project inside")
    private UISelectOne<GitHubOrganisationDTO> gitOrganisation;
    @Inject
    @WithAttributes(label = "Repository", required = true, description = "The name of the new github repository")
    private UIInput<String> gitRepository;

    @Inject
    @WithAttributes(label = "Description", description = "The description of the new github repository")
    private UIInput<String> gitRepoDescription;

    @Inject
    private EmbeddedCacheManager cacheManager;

    /**
     * The name of the upstream repo
     */
    private String origin = "origin";

    /**
     * The default branch we make on creating repos
     */
    private String branch = "master";

    private GitHubFacade github;
    private Cache<String, GitAccount> accountCache;
    private Cache<String, Iterable<GitHubOrganisationDTO>> organisationsCache;

    public void initializeUI(final UIBuilder builder) throws Exception {
        this.accountCache = Caches.getCache(cacheManager, CacheNames.GITHUB_ACCOUNT_FROM_SECRET);
        this.organisationsCache = Caches.getCache(cacheManager, CacheNames.GITHUB_ORGANISATIONS);

        this.github = createGithubFacade(builder.getUIContext());

        // TODO cache this per user every say 30 seconds!
        Iterable<GitHubOrganisationDTO> organisations = new ArrayList<>();
        if (github != null && github.isDetailsValid()) {
            String orgKey = github.getDetails().getUserCacheKey();
            organisations = organisationsCache.computeIfAbsent(orgKey, k -> github.loadGithubOrganisations(builder));
        }
        gitOrganisation.setValueChoices(organisations);
        gitOrganisation.setItemLabelConverter(new Converter<GitHubOrganisationDTO, String>() {
            @Override
            public String convert(GitHubOrganisationDTO organisation) {
                return organisation.getName();
            }
        });
        String userName = github.getDetails().getUsername();
        if (Strings.isNotBlank(userName)) {
            for (GitHubOrganisationDTO organisation : organisations) {
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

    private GitHubFacade createGithubFacade(UIContext context) {
        GitAccount details = (GitAccount) context.getAttributeMap().get(AttributeMapKeys.GITHUB_DETAILS);
        if (details != null) {
            return new GitHubFacade(details);
        } else if (Configuration.isOnPremise()) {
            details = GitAccount.loadGitDetailsFromSecret(accountCache, GitSecretNames.GITHUB_SECRET_NAME);
            return new GitHubFacade(details);
        }

        // lets try find it from KeyCloak etc...
        return new GitHubFacade();
    }

    @Override
    public void validate(UIValidationContext context) {
        if (github == null || !github.isDetailsValid()) {
            // invoked too early before the github account is setup - lets return silently
            return;
        }
        String orgName = getOrganisationName();
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
        String org = getOrganisationName();
        String repo = gitRepository.getValue();

        LOG.info("Creating github repository " + org + "/" + repo);

        UIContext uiContext = context.getUIContext();
        File basedir = getSelectionFolder(uiContext);
        if (basedir == null || !basedir.exists() || !basedir.isDirectory()) {
            return Results.fail("No project directory exists! " + basedir);
        }

        String gitUrl = "https://github.com/" + org + "/" + repo + ".git";
        try {
            GHRepository repository = github.createRepository(org, repo, gitRepoDescription.getValue());
            URL htmlUrl = repository.getHtmlUrl();
            if (htmlUrl != null) {
                gitUrl = htmlUrl.toString() + ".git";
            }
        } catch (IOException e) {
            LOG.error("Failed to create repository  " + org + "/" + repo + " " + e, e);
            return Results.fail("Failed to create repository  " + org + "/" + repo + " " + e, e);
        }

        LOG.info("Created githubrepository: " + gitUrl);
        uiContext.getAttributeMap().put(GIT_URL, gitUrl);

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

    protected String getOrganisationName() {
        String orgName = null;
        GitHubOrganisationDTO org = gitOrganisation.getValue();
        if (org != null) {
            orgName = org.getName();
        }
        return orgName;
    }

    public void importNewGitProject(UserDetails userDetails, File basedir, String message, String gitUrl)
            throws GitAPIException, JsonProcessingException {
        GitUtils.disableSslCertificateChecks();
        InitCommand initCommand = Git.init();
        initCommand.setDirectory(basedir);
        Git git = initCommand.call();
        LOG.info("Initialised an empty git configuration repo at {}", basedir.getAbsolutePath());
        PersonIdent personIdent = userDetails.createPersonIdent();

        GitUtils.configureBranch(git, branch, origin, gitUrl);
        GitUtils.addDummyFileToEmptyFolders(basedir);
        LOG.info("About to git commit and push to: " + gitUrl + " and remote name " + origin);
        GitUtils.doAddCommitAndPushFiles(git, userDetails, personIdent, branch, origin, message, true);
    }

    private Result updateGitURLInJenkinsfile(File basedir, String gitUrl) {
        File jenkinsFile = new File(basedir, ProjectConfigs.LOCAL_FLOW_FILE_NAME);
        if (jenkinsFile.isFile() && jenkinsFile.exists()) {
            String pipelineText;
            try {
                pipelineText = IOHelpers.readFully(jenkinsFile);
            } catch (IOException e) {
                return Results.fail("Failed to load file " + jenkinsFile + ". " + e, e);
            }
            pipelineText = Strings.replaceAllWithoutRegex(pipelineText, "GIT_URL", "'" + gitUrl + "'");
            try {
                IOHelpers.writeFully(jenkinsFile, pipelineText);
            } catch (IOException e) {
                return Results.fail("Failed to write file " + jenkinsFile + ". " + e, e);
            }
        }
        return null;
    }

}
