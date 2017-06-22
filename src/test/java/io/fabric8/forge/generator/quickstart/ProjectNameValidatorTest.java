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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 */
public class ProjectNameValidatorTest {

    @Test
    public void testNameValidations() throws Exception {
        assertNameValidation(true, "foo", "foo-bar", "foo123-456", "f1234", "f-b-z");
        assertNameValidation(false, "1foo", "-foo", "foo-", "Foo", "foo bar", "foo(");
    }

    public static void assertNameValidation(boolean expectedValid, String... values) {
        for (String value : values) {
            String message = ProjectNameValidator.validProjectName(value);
            if (expectedValid) {
                assertEquals("Validation message for '" + value + "' should be null", null, message);
            } else {
                assertNotNull("Validation message for '" + value + "' should be not null!", message);
            }
        }
    }
}
