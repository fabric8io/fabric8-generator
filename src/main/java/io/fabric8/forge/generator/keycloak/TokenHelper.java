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
package io.fabric8.forge.generator.keycloak;

import io.fabric8.forge.generator.EnvironmentVariables;
import io.fabric8.utils.Strings;
import org.jboss.forge.addon.ui.context.UIContext;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

/**
 */
public class TokenHelper {

    public static String getMandatoryAuthHeader(UIContext context) {
        String authToken = (String) context.getAttributeMap().get("authorisation");
        if (Strings.isNullOrBlank(authToken)) {
            authToken = System.getenv(EnvironmentVariables.TESTING_OAUTH_HEADER);
        }
        if (Strings.isNullOrBlank(authToken)) {
            throw new WebApplicationException("No authorization header available", Response.Status.UNAUTHORIZED);
        }
        return authToken;
    }

    public static String getMandatoryTokenFor(KeycloakEndpoint endpoint, String authHeader) {
        KeycloakClient client = new KeycloakClient();
        String gitHubToken = client.getTokenFor(endpoint, authHeader);
        if (Strings.isNullOrBlank(gitHubToken)) {
            throw new WebApplicationException("No github auth token available!", Response.Status.UNAUTHORIZED);
        }
        return gitHubToken;
    }
}
