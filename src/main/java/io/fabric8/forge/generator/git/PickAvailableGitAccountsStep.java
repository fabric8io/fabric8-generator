/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package io.fabric8.forge.generator.git;

import io.fabric8.forge.generator.github.GithubRepoStep;
import io.fabric8.forge.generator.kubernetes.CreateBuildConfigStep;
import org.jboss.forge.addon.convert.Converter;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.input.UISelectOne;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.NavigationResult;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.result.navigation.NavigationResultBuilder;
import org.jboss.forge.addon.ui.wizard.UIWizardStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;

/**
 * When running on premise lets let the user setup their github credentials and store them in a Secret
 */
public class PickAvailableGitAccountsStep extends AbstractGitCommand implements UIWizardStep {
    final transient Logger LOG = LoggerFactory.getLogger(this.getClass());

    @Inject
    @WithAttributes(label = "git service user name", required = true, description = "Select which git provider you wish to use")
    private UISelectOne<GitProvider> gitService;

    public void initializeUI(final UIBuilder builder) throws Exception {
        List<GitProvider> gitServices = GitProvider.loadGitProviders();
        if (gitServices.size() > 1) {
            gitService.setValueChoices(gitServices);
            gitService.setItemLabelConverter(new Converter<GitProvider, String>() {
                @Override
                public String convert(GitProvider gitProvider) {
                    return gitProvider.getName();
                }
            });
            builder.add(gitService);
        }
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        NavigationResultBuilder builder = NavigationResultBuilder.create();
        // TODO use git provider based on selection
        addRepoStep(builder);
        builder.add(CreateBuildConfigStep.class);
        return builder.build();
    }

    protected void addRepoStep(NavigationResultBuilder builder) {
        builder.add(GithubRepoStep.class);
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        return Results.success();
    }
}
