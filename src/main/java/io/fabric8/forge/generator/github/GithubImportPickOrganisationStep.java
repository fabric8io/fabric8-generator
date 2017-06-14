/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package io.fabric8.forge.generator.github;

import io.fabric8.forge.generator.git.GitOrganisationDTO;
import io.fabric8.utils.Strings;
import org.jboss.forge.addon.convert.Converter;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.context.UIValidationContext;
import org.jboss.forge.addon.ui.input.UISelectOne;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.NavigationResult;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.wizard.UIWizardStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;

import static io.fabric8.forge.generator.AttributeMapKeys.GIT_ACCOUNT;
import static io.fabric8.forge.generator.AttributeMapKeys.GIT_ORGANISATION;
import static io.fabric8.forge.generator.AttributeMapKeys.GIT_OWNER_NAME;

/**
 * Lets the user select an organisation
 */
public class GithubImportPickOrganisationStep extends AbstractGithubStep implements UIWizardStep {
    final transient Logger LOG = LoggerFactory.getLogger(this.getClass());
    @Inject
    @WithAttributes(label = "Organisation", required = true, description = "The github organisation to import repositories from")
    private UISelectOne<GitOrganisationDTO> gitOrganisation;

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
        if (organisations.size() > 1) {
            builder.add(gitOrganisation);
        }
    }


    @Override
    public void validate(UIValidationContext context) {
        if (github == null || !github.isDetailsValid()) {
            // invoked too early before the github account is setup - lets return silently
            return;
        }
        String orgName = getOrganisationName(gitOrganisation.getValue());

        if (Strings.isNullOrBlank(orgName)) {
            context.addValidationError(gitOrganisation, "Please select a github organiastion");
        }
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        storeAttributes(context.getUIContext());
        return null;
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        return storeAttributes(context.getUIContext());
    }

    protected Result storeAttributes(UIContext uiContext) {
        if (github == null) {
            return Results.fail("No github account setup");
        }
        String org = getOrganisationName(gitOrganisation.getValue());

        String gitOwnerName = org;
        uiContext.getAttributeMap().put(GIT_OWNER_NAME, gitOwnerName);
        uiContext.getAttributeMap().put(GIT_ORGANISATION, org);
        uiContext.getAttributeMap().put(GIT_ACCOUNT, github.getDetails());

        return Results.success();
    }


}
