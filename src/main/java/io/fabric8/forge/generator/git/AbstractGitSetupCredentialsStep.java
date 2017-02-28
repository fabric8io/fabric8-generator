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

import io.fabric8.forge.generator.kubernetes.KubernetesClientHelper;
import io.fabric8.forge.generator.pipeline.AbstractDevToolsCommand;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.utils.Strings;
import org.jboss.forge.addon.ui.input.UIInput;
import org.jboss.forge.addon.ui.result.Result;

/**
 */
public abstract class AbstractGitSetupCredentialsStep extends AbstractDevToolsCommand {
    private KubernetesClient kubernetesClient;
    private String namespace;

    protected GitAccount loadGitAccountFromSecret(String githubSecretName) {
        kubernetesClient = KubernetesClientHelper.createKubernetesClientForUser();
        namespace = KubernetesClientHelper.getUserSecretNamespace(kubernetesClient);
        return GitAccount.loadFromSecret(kubernetesClient, namespace, githubSecretName);
    }

    protected void setIfNotBlank(UIInput<String> input, String value) {
        if (Strings.isNotBlank(value)) {
            input.setValue(value);
        }
    }

    protected Result storeGitAccountInSecret(GitAccount details, String githubSecretName) {
        return GitAccount.storeGitDetailsInSecret(kubernetesClient, namespace, githubSecretName, details);
    }
}
