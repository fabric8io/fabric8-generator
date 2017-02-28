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
package io.fabric8.forge.generator.transformers;

import io.fabric8.forge.generator.CommonSteps;
import org.jboss.forge.addon.ui.command.UICommand;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.result.NavigationResult;
import org.jboss.forge.addon.ui.result.navigation.NavigationResultBuilder;
import org.jboss.forge.addon.ui.result.navigation.NavigationResultTransformer;
import org.obsidiantoaster.generator.ui.quickstart.NewProjectFromQuickstartWizard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public class NewProjectFromQuickstartWizardTransformer implements NavigationResultTransformer {
    private static final transient Logger LOG = LoggerFactory.getLogger(NewProjectFromQuickstartWizardTransformer.class);

    public NewProjectFromQuickstartWizardTransformer() {
         System.out.println("========== created " + this);
        LOG.info("========== created " + this);
    }

    @Override
    public boolean handles(UINavigationContext context) {
        System.out.println("============================ handles() " + this);
        LOG.info("============================ handles() " + this);

        try {
            UICommand currentCommand = context.getCurrentCommand();
            String name = currentCommand.getMetadata(context.getUIContext()).getName();
            String className = currentCommand.getClass().getName();
            LOG.info("========== testing for NewProjectGeneratorWizard on name: " + name + " class: " + className);

            if ("obsidian-new-quickstart".equals(name) || className.equals(NewProjectFromQuickstartWizard.class.getName())) {
                return true;
            }

            return context.getCurrentCommand() instanceof NewProjectFromQuickstartWizard;
        } catch (Exception e) {
            LOG.error("========= Caught: " + e, e);
            return false;
        }
    }

    @Override
    public NavigationResult transform(UINavigationContext context, NavigationResult currentFlow) {
        System.out.println("============= adding transformer " + this);
        NavigationResultBuilder builder = NavigationResultBuilder.create(currentFlow);
        CommonSteps.addPipelineGitHubAndOpenShiftSteps(builder);
        return builder.build();
    }

    @Override
    public int priority() {
        return 1000;
    }
}
