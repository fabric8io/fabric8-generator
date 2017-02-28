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
public class Configuration {
    private static Boolean _onPremise;

    /**
     * Returns true if the generator is running on premise such as when used inside the fabric8 upstream project.
     * <p>
     * When in on premise mode we provide a wizard to let the user setup their github credentials and also check for
     * other kinds of on premise git repositories.
     */
    public static boolean isOnPremise() {
        if (_onPremise == null) {
            String value = System.getenv(EnvironmentVariables.ON_PREMISE);
            _onPremise = Strings.isNotBlank(value) && value.equalsIgnoreCase("true");
        }
        return _onPremise.booleanValue();
    }
}
