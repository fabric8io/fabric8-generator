/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package io.fabric8.forge.generator.gogs;

import io.fabric8.forge.generator.AttributeMapKeys;
import io.fabric8.forge.generator.git.AbstractGitSetupCredentialsStep;
import io.fabric8.forge.generator.git.GitAccount;
import io.fabric8.forge.generator.git.GitSecretNames;
import io.fabric8.utils.Strings;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.hints.InputType;
import org.jboss.forge.addon.ui.input.UIInput;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.NavigationResult;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.wizard.UIWizardStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Map;

/**
 * When running on premise lets let the user setup their gogs credentials and store them in a Secret
 */
public class GogsSetupCredentialsStep extends AbstractGitSetupCredentialsStep implements UIWizardStep {
    final transient Logger LOG = LoggerFactory.getLogger(this.getClass());

    @Inject
    @WithAttributes(label = "gogs user name", required = true, description = "Your gogs user name")
    private UIInput<String> gitUserName;

    @Inject
    @WithAttributes(label = "gogs password", required = true, description = "Your gogs passcode or password", type = InputType.SECRET)
    private UIInput<String> gitPassword;

    @Inject
    @WithAttributes(label = "email for gogs", required = true, description = "Your gogs email address")
    private UIInput<String> gitEmail;

    public void initializeUI(final UIBuilder builder) throws Exception {
        GitAccount details = loadGitAccountFromSecret(GitSecretNames.GOGS_SECRET_NAME);
        if (details != null) {
            setIfNotBlank(gitUserName, details.getUsername());
            setIfNotBlank(gitPassword, details.getPassword());
            setIfNotBlank(gitEmail, details.getEmail());
        }
        builder.add(gitUserName);
        builder.add(gitPassword);
        builder.add(gitEmail);
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        String username = gitUserName.getValue();
        String password = gitPassword.getValue();
        String email = gitEmail.getValue();
        String token = null;

        GitAccount details = new GitAccount(username, token, password, email);

        Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();
        attributeMap.put(AttributeMapKeys.GIT_ACCOUNT, details);

/*
      if (details.hasValidData()) {
         NavigationResultBuilder builder = NavigationResultBuilder.create();
         builder.add(GogsSetupCredentialsStep.class);
         return builder.build();
      }
*/
        return null;
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        String username = gitUserName.getValue();
        String password = gitPassword.getValue();
        String email = gitEmail.getValue();
        String token = null;

        if (Strings.isNullOrBlank(username)) {
            return Results.fail("Missing gogs username");
        }
        if (Strings.isNullOrBlank(password)) {
            return Results.fail("Missing gogs password");
        }
        if (Strings.isNullOrBlank(email)) {
            return Results.fail("Missing gogs email");
        }
        GitAccount details = new GitAccount(username, token, password, email);
        Result result = storeGitAccountInSecret(details, GitSecretNames.GOGS_SECRET_NAME);
        if (result != null) {
            return result;
        }

        Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();
        attributeMap.put(AttributeMapKeys.GIT_ACCOUNT, details);

        return Results.success();
    }

}
