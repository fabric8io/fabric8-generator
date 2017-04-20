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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 */
public class ObsidianWizardTransformer implements NavigationResultTransformer {
    private static final transient Logger LOG = LoggerFactory.getLogger(ObsidianWizardTransformer.class);

    private Set<String> commandNames = new HashSet<>(Arrays.asList(
            //"Launchpad: New Project",
            "Launchpad: New Starter Project"
    ));

    @Override
    public boolean handles(UINavigationContext context) {
        UICommand currentCommand = context.getCurrentCommand();
        String name = currentCommand.getMetadata(context.getUIContext()).getName();
        if (commandNames.contains(name)) {
            return true;
        }
        return false;
    }

    @Override
    public NavigationResult transform(UINavigationContext context, NavigationResult currentFlow) {
        NavigationResultBuilder builder = NavigationResultBuilder.create(currentFlow);
        CommonSteps.addPipelineGitHubAndOpenShiftSteps(builder);
        return builder.build();
    }

    @Override
    public int priority() {
        return 1000;
    }
}
