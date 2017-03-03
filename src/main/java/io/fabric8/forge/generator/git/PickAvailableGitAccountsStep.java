/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package io.fabric8.forge.generator.git;

import io.fabric8.forge.generator.AttributeMapKeys;
import io.fabric8.forge.generator.cache.CacheFacade;
import io.fabric8.forge.generator.cache.CacheNames;
import io.fabric8.forge.generator.kubernetes.CreateBuildConfigStep;
import io.fabric8.forge.generator.kubernetes.KubernetesClientHelper;
import org.infinispan.Cache;
import org.jboss.forge.addon.convert.Converter;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
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
    protected Cache<String, List<GitProvider>> gitProviderCache;
    @Inject
    @WithAttributes(label = "git provider", required = true, description = "Select which git provider you wish to use")
    private UISelectOne<GitProvider> gitProvider;
    @Inject
    private CacheFacade cacheManager;

    public void initializeUI(final UIBuilder builder) throws Exception {
        super.initializeUI(builder);

        this.gitProviderCache = cacheManager.getCache(CacheNames.GIT_PROVIDERS);

        String key = KubernetesClientHelper.getUserCacheKey();
        List<GitProvider> gitServices = gitProviderCache.computeIfAbsent(key, k -> GitProvider.loadGitProviders());
        int size = gitServices.size();
        if (size > 0) {
            gitProvider.setDefaultValue(gitServices.get(0));
        }
        if (size > 1) {
            gitProvider.setValueChoices(gitServices);
            gitProvider.setItemLabelConverter(new Converter<GitProvider, String>() {
                @Override
                public String convert(GitProvider gitProvider) {
                    return gitProvider.getName();
                }
            });
            builder.add(gitProvider);
        }
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        NavigationResultBuilder builder = NavigationResultBuilder.create();
        // TODO use git provider based on selection
        addRepoStep(builder);
        builder.add(CreateBuildConfigStep.class);
        registerAttributes(context.getUIContext());
        return builder.build();
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        registerAttributes(context.getUIContext());
        return Results.success();
    }

    protected void registerAttributes(UIContext context) {
        GitProvider provider = gitProvider.getValue();
        if (provider != null) {
            context.getAttributeMap().put(AttributeMapKeys.GIT_PROVIDER, provider);
        }
    }

    protected void addRepoStep(NavigationResultBuilder builder) {
        GitProvider provider = gitProvider.getValue();
        if (provider == null) {
            throw new IllegalArgumentException("No git providers!");
        }
        provider.addRepoStep(builder);
    }

}
