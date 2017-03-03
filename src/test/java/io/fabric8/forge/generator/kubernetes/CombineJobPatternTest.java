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
package io.fabric8.forge.generator.kubernetes;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.fabric8.forge.generator.kubernetes.CreateBuildConfigStep.combineJobPattern;
import static org.junit.Assert.assertEquals;

/**
 */
public class CombineJobPatternTest {
    private static final transient Logger LOG = LoggerFactory.getLogger(CombineJobPatternTest.class);

    @Test
    public void testCombineJobPatterns() throws Exception {
        assertJobPatternCombines("", "foo", "foo");
        assertJobPatternCombines("   ", "foo", "foo");
        assertJobPatternCombines("(bar)", "foo", "(bar|foo)");
        assertJobPatternCombines("(bar|whatnot)", "foo", "(bar|whatnot|foo)");
    }

    private void assertJobPatternCombines(String currentValue, String repoName, String expected) {
        String actual = combineJobPattern(currentValue, repoName);
        LOG.debug("job combine from `" + currentValue + "` with `" + repoName + "` = `" + actual + "`");
        assertEquals("job combine from `" + currentValue + "` with `" + repoName + "`", expected, actual);
    }

}
