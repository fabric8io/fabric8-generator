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

import io.fabric8.forge.generator.CommonSteps;
import io.fabric8.forge.generator.Configuration;
import io.fabric8.forge.generator.cache.CacheFacade;
import io.fabric8.forge.generator.github.GithubRepoStep;
import io.fabric8.utils.Strings;
import io.openshift.launchpad.ui.input.ProjectName;
import io.openshift.launchpad.ui.quickstart.NewProjectWizard;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.context.UIValidationContext;
import org.jboss.forge.addon.ui.input.InputComponent;
import org.jboss.forge.addon.ui.input.InputComponentFactory;
import org.jboss.forge.addon.ui.input.UISelectOne;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.result.NavigationResult;
import org.jboss.forge.addon.ui.result.navigation.NavigationResultBuilder;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.lang.reflect.Field;
import java.util.Map;

import static java.lang.Character.*;

/**
 * Lets add extra validation to the first page so that users can hit Finish early
 */
public class NewLaunchpadProjectWizard extends NewProjectWizard {
    private static final transient Logger LOG = LoggerFactory.getLogger(NewLaunchpadProjectWizard.class);

    @Inject
    private CacheFacade cacheManager;

    @Inject
    private InputComponentFactory inputComponentFactory;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(this.getClass()).name("Fabric8: New Project").description("Generate your project from a booster").category(Categories.create(new String[]{"Openshift.io"}));
    }

    @Override
    public void validate(UIValidationContext context) {
        super.validate(context);
        final UIContext uiContext = context.getUIContext();
        Map attributeMap = uiContext.getAttributeMap();
        String name = getProjectNameValue();
        if (name == null) {
            name = (String) attributeMap.get("name");
        }
        Object type = getTypeValue();
        if (name != null) {
            if (Strings.isNotBlank(name)) {
                if (invalidProjectName(context, name)) {
                    return;
                }
            }
            if (!Configuration.isOnPremise()) {
                // lets populate the attributes so we can validate another step
                attributeMap.put("name", name);
                attributeMap.put("type", type);

                // TODO lets assume github for now - otherwise we'll have to disable Next until a user picks the git
                // provider otherwise we can't support Finish on the first page
                GithubRepoStep repoStep = new GithubRepoStep(cacheManager, inputComponentFactory);
                UIBuilder builder = new UIBuilder() {
                    @Override
                    public UIBuilder add(InputComponent<?, ?> inputComponent) {
                        return this;
                    }

                    @Override
                    public InputComponentFactory getInputComponentFactory() {
                        return null;
                    }

                    @Override
                    public UIContext getUIContext() {
                        return uiContext;
                    }
                };
                try {
                    repoStep.initializeUI(builder);
                } catch (Exception e) {
                    LOG.error("Failed to initialise " + repoStep + " due to: " + e, e);
                }
                repoStep.validate(context);
            }
        }
    }

    protected boolean invalidProjectName(UIValidationContext context, String name) {
        String errorMessage = ProjectNameValidator.validProjectName(name);
        if (errorMessage != null) {
            context.addValidationError(getNamedInput(context), errorMessage + ".\n");
            return true;
        }
        return false;
    }

    protected InputComponent<?, ?> getNamedInput(UIValidationContext context) {
        InputComponent<?, ?> answer = (InputComponent<?, ?>) getParentClassFieldValue("named");
        if (answer == null) {
            answer = context.getCurrentInputComponent();
        }
        return answer;
    }

    // TODO lets add this to the base class so we can avoid this horrid reflection!
    protected String getProjectNameValue() {
        Object value = getParentClassFieldValue("named");
        if (value instanceof ProjectName) {
            ProjectName projectName = (ProjectName) value;
            return projectName.getValue();
        }
        return null;
    }

    // TODO lets add this to the base class so we can avoid this horrid reflection!
    protected Object getTypeValue() {
        Object value = getParentClassFieldValue("type");
        if (value instanceof UISelectOne) {
            UISelectOne type = (UISelectOne) value;
            return type.getValue();
        }
        return null;
    }

    private Object getParentClassFieldValue(String fieldName) {
        Object value = null;
        try {
            Field field = getClass().getSuperclass().getDeclaredField(fieldName);
            field.setAccessible(true);
            value = field.get(this);
        } catch (Exception e) {
            LOG.error("Could not find field " + fieldName + ": " + e, e);
        }
        return value;
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        NavigationResultBuilder builder = NavigationResultBuilder.create();
        CommonSteps.addPipelineGitHubAndOpenShiftSteps(builder);
        return builder.build();
    }
}
