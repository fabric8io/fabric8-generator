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
package io.fabric8.forge.generator.pipeline;

import io.fabric8.forge.generator.versions.VersionHelper;
import io.fabric8.utils.DomHelper;
import io.fabric8.utils.Files;
import io.fabric8.utils.Strings;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import static io.fabric8.forge.generator.che.CheStackDetector.parseXmlFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 */
public class PomVersionChangeTest {
    private static final transient Logger LOG = LoggerFactory.getLogger(PomVersionChangeTest.class);

    @Test
    public void testVersionReplacement() throws Exception {
        String basedir = System.getProperty("basedir", ".");
        File testDir = new File(basedir, "src/test/resources/poms");

        File outDir = new File(basedir, "target/test-data/pom-version-change");
        Files.recursiveDelete(outDir);
        outDir.mkdirs();
        Files.copy(testDir, outDir);

        File[] files = outDir.listFiles();
        assertNotNull("No output files!", files);
        assertTrue("No output files!", files.length > 0);

        for (File file : files) {
            StatusDTO status = new StatusDTO();
            ChoosePipelineStep.updatePomVersions(file, status, "myspace");

            List<String> warnings = status.getWarnings();
            for (String warning : warnings) {
                System.out.println("Warning: " + warning);
            }

            // lets assert that the right elements got a version added...
            Document doc;
            try {
                doc = parseXmlFile(file);
            } catch (Exception e) {
                LOG.error("Failed to parse " + file + " " + e, e);
                fail("Failed to parse " + file + " due to " + e);
                continue;
            }

            String expectPluginVersionsText = "";
            NodeList pluginVersionElements = doc.getElementsByTagName("expectPluginVersions");
            if (pluginVersionElements.getLength() > 0) {
                Node item = pluginVersionElements.item(0);
                if (item instanceof Element) {
                    Element element = (Element) item;
                    expectPluginVersionsText = element.getTextContent();
                }
            }

            List<String> expectPluginVersionList = new ArrayList<>();
            if (Strings.isNotBlank(expectPluginVersionsText)) {
                StringTokenizer iter = new StringTokenizer(expectPluginVersionsText);
                while (iter.hasMoreTokens()) {
                    expectPluginVersionList.add(iter.nextToken());
                }
            } else {
                fail("File " + file + " does not contain a <expectPluginVersions> in the <properties> section!");
            }

            assertPluginVersionsMatchIndices(file, doc, expectPluginVersionList);
        }
    }

    protected static void assertPluginVersionsMatchIndices(File file, Document doc, List<String> expectPluginVersionList) {
        String fmpVersion = VersionHelper.fabric8MavenPluginVersion();
        NodeList plugins = doc.getElementsByTagName("plugin");
        int index = 0;
        for (int i = 0, size = plugins.getLength(); i < size; i++) {
            Node item = plugins.item(i);
            if (item instanceof Element) {
                Element element = (Element) item;
                if ("fabric8-maven-plugin".equals(DomHelper.firstChildTextContent(element, "artifactId"))) {
                    String version = DomHelper.firstChildTextContent(element, "version");

                    if (expectPluginVersionList.size() <= index) {
                        fail("file " + file + " does not have enough version expressions in <expectPluginVersions> element has we have at least " + (index + 1) + " fabric8-maven-plugin elements!");
                    }
                    String expected = expectPluginVersionList.get(index);
                    if ("version".equals(expected)) {
                        expected = fmpVersion;
                    }

                    if ("none".equals(expected)) {
                        if (version != null) {
                            fail("file " + file + " has a <version> element in the fabric8-maven-plugin element index " + index + " when it is not expected");
                        }
                    } else {
                        if (version == null) {
                            fail("file " + file + " expected a <version> element in the fabric8-maven-plugin element index " + index + " for " + element);
                        }
                        assertEquals("file " + file + " fabric8-maven-plugin element index " + index + " <version> element", expected, version);
                    }
                    System.out.println("file " + file + " has fabric8-maven-plugin " + index + " version " + expected);
                    index++;
                }
            }
        }
        if (expectPluginVersionList.size() > index) {
            fail("file " + file + " does not contain " + expectPluginVersionList.size() + " fabric8-maven-plugin elements so <expectPluginVersions> element contains too many version expressions!");
        }

        // now lets assert the properties
        Element properties = DomHelper.firstChild(doc.getDocumentElement(), "properties");
        if (properties != null) {
            assertPropertyEqualsIfExists(file, properties, "fabric8.version", VersionHelper.fabric8Version());
            assertPropertyEqualsIfExists(file, properties, "fabric8.maven.plugin.version", fmpVersion);
        }
    }

    protected static void assertPropertyEqualsIfExists(File file, Element properties, String propertyName, String expectedValue) {
        String value = DomHelper.firstChildTextContent(properties, propertyName);
        if (value != null) {
            System.out.println("File " + file + " has property " + propertyName + " = " + value);
            assertEquals("File " + file + " property " + propertyName + " element", expectedValue, value);
        }
    }
}
