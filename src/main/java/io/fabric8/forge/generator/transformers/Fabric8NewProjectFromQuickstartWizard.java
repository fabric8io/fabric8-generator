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
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.result.NavigationResult;
import org.jboss.forge.addon.ui.result.navigation.NavigationResultBuilder;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;
import org.obsidiantoaster.generator.ui.quickstart.NewProjectFromQuickstartWizard;

/**
 */
public class Fabric8NewProjectFromQuickstartWizard extends NewProjectFromQuickstartWizard {

    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(this.getClass()).
                name("fabric8: New Quickstart").
                description("Generate your project from a quickstart").
                category(Categories.create(new String[]{"Fabric8"}));
    }


    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        NavigationResult result = super.next(context);
        NavigationResultBuilder builder = NavigationResultBuilder.create(result);
        CommonSteps.addPipelineGitHubAndOpenShiftSteps(builder);
        return builder.build();

    }
}
