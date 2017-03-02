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
package io.fabric8.forge.generator.git;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.forge.addon.utils.dto.OutputFormat;
import org.jboss.forge.addon.ui.command.UICommand;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UIValidationContext;
import org.jboss.forge.addon.ui.input.UISelectOne;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

import static io.fabric8.forge.addon.utils.OutputFormatHelper.toJson;

/**
 * A command which checks if we have a git account setup correctly
 */
public class CheckGitAccounts implements UICommand {

    @Inject
    @WithAttributes(label = "Format", defaultValue = "Text", description = "Format output as text or json")
    private UISelectOne<OutputFormat> format;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(getClass()).name("fabric8: Check Git Accounts")
                .description("Checks that you have at least one git repository account setup")
                .category(Categories.create("Fabric8"));
    }

    @Override
    public boolean isEnabled(UIContext context) {
        return true;
    }
    

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        builder.add(format);
    }

    @Override
    public void validate(UIValidationContext context) {

    }

    @Override
    public Result execute(UIExecutionContext uiExecutionContext) throws Exception {
        List<GitProvider> gitServices = GitProvider.loadGitProviders();
        List<String> validServices = new ArrayList<>();
        for (GitProvider gitService : gitServices) {
            if (gitService.isConfiguredCorrectly()) {
                validServices.add(gitService.getName());
            }
        }
        return Results.success(formatResult(validServices));
    }


    protected String formatResult(List<String> result) throws JsonProcessingException {
        OutputFormat outputFormat = format.getValue();
        switch (outputFormat) {
            case JSON:
                return toJson(result);
            default:
                return "git providers: " + String.join(", ", result);
        }
    }
}
