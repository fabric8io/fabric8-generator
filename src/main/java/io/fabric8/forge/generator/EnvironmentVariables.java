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
package io.fabric8.forge.generator;

import io.fabric8.utils.Strings;

/**
 */
public class EnvironmentVariables {
    public static final String KEYCLOAK_SAAS = "KEYCLOAK_SAAS_URL";
    public static final String WIT_URL = "WIT_URL";
    public static final String GOGS_URL = "GOGS_URL";
    public static final String NAMESPACE = "KUBERNETES_NAMESPACE";

    public static final String TESTING_OAUTH_HEADER = "TESTING_OAUTH_HEADER";
    public static final String OPENSHIFT_API_URL = "OPENSHIFT_API_URL";

    public static String getWitApiURL() {
        String witAPI = System.getenv(WIT_URL);
        if (Strings.isNullOrBlank(witAPI)) {
            witAPI = "http://core/";
        }
        return witAPI;
    }
}
