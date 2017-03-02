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

import io.fabric8.forge.generator.keycloak.KeycloakEndpoint;
import io.fabric8.forge.generator.keycloak.TokenHelper;
import io.fabric8.forge.generator.kubernetes.KubernetesClientHelper;
import io.fabric8.kubernetes.api.model.DoneableSecret;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ClientResource;
import io.fabric8.utils.Strings;
import org.infinispan.Cache;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static io.fabric8.forge.generator.pipeline.JenkinsPipelineLibrary.getSystemPropertyOrDefault;

/**
 */
public class GitAccount {

    private static final transient Logger LOG = LoggerFactory.getLogger(GitAccount.class);

    private final String username;
    private final String token;
    private final String password;
    private final String email;

    public GitAccount(String username, String token, String password, String email) {
        this.username = username;
        this.token = token;
        this.password = password;
        this.email = email;
    }

    public static GitAccount loadFromSaaS(UIContext context) {
        String authToken = TokenHelper.getMandatoryAuthTokenFor(context, KeycloakEndpoint.GET_GITHUB_TOKEN);
        return new GitAccount(null, authToken, null, null);
    }

    /**
     * Creates a default set of git account details using environment variables for testing
     */
    public static GitAccount createViaEnvironmentVariables(String envVarPrefix) {
        String username = getSystemPropertyOrDefault(envVarPrefix + "_USERNAME", null);
        String password = getSystemPropertyOrDefault(envVarPrefix + "_PASSWORD", null);
        String token = getSystemPropertyOrDefault(envVarPrefix + "_TOKEN", null);
        String email = getSystemPropertyOrDefault(envVarPrefix + "_EMAIL", null);
        return new GitAccount(username, token, password, email);
    }

    public static GitAccount loadGitDetailsFromSecret(Cache<String, GitAccount> cache, String secretName) {
        final String key = KubernetesClientHelper.getUserCacheKey();
        return cache.computeIfAbsent(key, k -> {
            KubernetesClient kubernetesClient = KubernetesClientHelper.createKubernetesClientForUser();
            String namespace = KubernetesClientHelper.getUserSecretNamespace(kubernetesClient);
            GitAccount details = loadFromSecret(kubernetesClient, namespace, secretName);
            LOG.info("Loaded details: " + details + " for cache key: " +key);
            return details;
        });
    }

    public static GitAccount loadFromSecret(KubernetesClient kubernetesClient, String namespace, String secretName) {
        LOG.info("Loading git secret from namespace " + namespace + " with name: " + secretName);
        Secret secret = kubernetesClient.secrets().inNamespace(namespace).withName(secretName).get();
        if (secret != null) {
            Map<String, String> data = secret.getData();
            if (data != null) {
                String username = base64decode(data.get(GitSecretKeys.USERNAME));
                String token = base64decode(data.get(GitSecretKeys.TOKEN));
                String password = base64decode(data.get(GitSecretKeys.PASSWORD));
                String email = base64decode(data.get(GitSecretKeys.EMAIL));
                GitAccount gitAccount = new GitAccount(username, token, password, email);
                LOG.info("Found: " + gitAccount);
                return gitAccount;
            }
        }
        return null;
    }

    public static boolean isValid(GitAccount details) {
        // TODO we should probably test logging in as the user to load their organisations?
        return details != null && details.hasValidData();
    }

    public static Result storeGitDetailsInSecret(KubernetesClient kubernetesClient, String namespace, String secretName, GitAccount details) {
        boolean update = true;
        LOG.info("Storing git account into namespace " + namespace + " with name " + secretName);
        ClientResource<Secret, DoneableSecret> resource = kubernetesClient.secrets().inNamespace(namespace)
                .withName(secretName);
        Secret secret = resource.get();
        if (secret == null) {
            update = false;
            secret = new SecretBuilder().withNewMetadata().withName(secretName).endMetadata()
                    .withData(new HashMap<>()).build();
        }
        Map<String, String> data = secret.getData();
        if (data == null) {
            data = new HashMap<>();
        }
        secret.setData(data);

        data.put(GitSecretKeys.USERNAME, base64encode(details.getUsername()));
        data.put(GitSecretKeys.TOKEN, base64encode(details.getToken()));
        data.put(GitSecretKeys.PASSWORD, base64encode(details.getPassword()));
        data.put(GitSecretKeys.EMAIL, base64encode(details.getEmail()));

        try {
            if (update) {
                resource.replace(secret);
            } else {
                resource.create(secret);
            }
        } catch (Exception e) {
            return Results.fail("Failed to store github secret " + secretName + ". " + e, e);
        }
        return null;
    }

    private static String base64decode(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return new String(Base64.getDecoder().decode(text));
    }

    private static String base64encode(String text) {
        if (Strings.isNullOrBlank(text)) {
            return text;
        }
        return Base64.getEncoder().encodeToString(text.getBytes());
    }

    @Override
    public String toString() {
        return "GitHubDetails{" +
                "username='" + username + '\'' +
                ", email='" + email + '\'' +
                '}';
    }

    public String getUsername() {
        return username;
    }

    public String getToken() {
        return token;
    }

    public String getPassword() {
        return password;
    }

    public String getEmail() {
        return email;
    }

    public boolean hasValidData() {
        if (Strings.isNotBlank(token)) {
            return true;
        }
        return Strings.isNotBlank(username) && Strings.isNotBlank(email) &&
                (Strings.isNotBlank(password) || Strings.isNotBlank(token));
    }

    public String getUserCacheKey() {
        if (Strings.isNotBlank(username)) {
            return username;
        } else if (Strings.isNotBlank(token)) {
            return "token/" + token;
        }
        throw new IllegalArgumentException("No cache key available for user: " + this);
    }

}
