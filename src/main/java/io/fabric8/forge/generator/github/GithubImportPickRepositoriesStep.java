/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package io.fabric8.forge.generator.github;

import io.fabric8.forge.generator.AttributeMapKeys;
import io.fabric8.forge.generator.cache.CacheNames;
import io.fabric8.utils.Strings;
import org.infinispan.Cache;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UIValidationContext;
import org.jboss.forge.addon.ui.input.UIInput;
import org.jboss.forge.addon.ui.input.UISelectMany;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.wizard.UIWizardStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Map;

import static io.fabric8.forge.generator.AttributeMapKeys.GIT_REPO_NAMES;
import static io.fabric8.forge.generator.AttributeMapKeys.GIT_REPOSITORY_PATTERN;

/**
 * Lets the user configure the GitHub organisation and repo name that they want to pick for a new project
 */
public class GithubImportPickRepositoriesStep extends AbstractGithubStep implements UIWizardStep {
    final transient Logger LOG = LoggerFactory.getLogger(this.getClass());

    @Inject
    @WithAttributes(label = "Repository pattern", description = "The pattern to match repositories")
    private UIInput<String> gitRepositoryPattern;

    @Inject
    @WithAttributes(label = "Repositories", description = "The repositories to import")
    private UISelectMany<String> gitRepositories;

    protected Cache<String, Collection<String>> repositoriesCache;

    private GitHubFacade github;

    public void initializeUI(final UIBuilder builder) throws Exception {
        super.initializeUI(builder);

        this.repositoriesCache = cacheManager.getCache(CacheNames.GITHUB_REPOSITORIES_FOR_ORGANISATION);

        this.github = createGithubFacade(builder.getUIContext());

        Map<Object, Object> attributeMap = builder.getUIContext().getAttributeMap();
        final String gitOrganisation = (String) attributeMap.get(AttributeMapKeys.GIT_ORGANISATION);

        String userKey = github.getDetails().getUserCacheKey();
        String orgKey = userKey + "/" + gitOrganisation;

        Collection<String> repositoryNames = repositoriesCache.computeIfAbsent(orgKey, k -> github.getRespositoriesForOrganisation(gitOrganisation));
        gitRepositories.setValueChoices(repositoryNames);
        builder.add(gitRepositoryPattern);
        builder.add(gitRepositories);
    }

    @Override
    public void validate(UIValidationContext context) {
        if (github == null || !github.isDetailsValid()) {
            // invoked too early before the github account is setup - lets return silently
            return;
        }
        String pattern = gitRepositoryPattern.getValue();
        if (Strings.isNullOrBlank(pattern)) {
            Iterable<String> value = gitRepositories.getValue();
            if (!value.iterator().hasNext()) {
                context.addValidationWarning(gitRepositoryPattern, "You must enter a pattern or select one or more repositories to import");
            }
        }
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        if (github == null) {
            return Results.fail("No github account setup");
        }
        UIContext uiContext = context.getUIContext();
        String pattern = gitRepositoryPattern.getValue();
        Iterable<String> repositories = gitRepositories.getValue();
        if (Strings.isNullOrBlank(pattern)) {
            pattern = createPatternFromRepositories(repositories);
        }
        uiContext.getAttributeMap().put(GIT_REPOSITORY_PATTERN, pattern);
        uiContext.getAttributeMap().put(GIT_REPO_NAMES, repositories);
        return Results.success();
    }

    private String createPatternFromRepositories(Iterable<String> value) {
        StringBuilder builder = new StringBuilder();
        for (String name : value) {
            if (builder.length() > 0) {
                builder.append("|");
            }
            builder.append(name);
        }
        return builder.toString();
    }


}
