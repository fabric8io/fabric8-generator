/**
 *  Copyright 2005-2015 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package io.fabric8.forge.generator.versions;

import io.fabric8.utils.Strings;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VersionHelper {
    private static final transient Logger LOG = LoggerFactory.getLogger(VersionHelper.class);
    
    private static Map<String, String> groupArtifactVersionMap;
         

    /**
     * Retrieves the version of fabric8 to use
     */
    public static String fabric8Version() {
        return getVersion("io.fabric8", "kubernetes-api");
    }

    /**
     * Retrieves the version of fabric8 maven plugin to use
     */
    public static String fabric8MavenPluginVersion() {
        return getVersion("io.fabric8", "fabric8-maven-plugin");
    }

    public static String getVersion(String groupId, String artifactId) {
        String key = "" + groupId + "/" + artifactId;
        Map map = getGroupArtifactVersionMap();
        String version = (String)map.get(key);
        if(version == null) {
            LOG.warn("Could not find the version for groupId: " + groupId + " artifactId: " + artifactId + " in: " + map);
        }

        return version;
    }

    public static String getVersion(String groupId, String artifactId, String defaultVersion) {
        String answer = getVersion(groupId, artifactId);
        if(Strings.isNullOrBlank(answer)) {
            answer = defaultVersion;
        }

        return answer;
    }

    protected static Map<String, String> getGroupArtifactVersionMap() {
        if(groupArtifactVersionMap == null) {
            groupArtifactVersionMap = new HashMap<>();
            InputStream in = VersionHelper.class.getResourceAsStream("versions.properties");
            if(in == null) {
                LOG.warn("Could not find versions.properties on the classpath!");
            } else {
                Properties properties = new Properties();

                try {
                    properties.load(in);
                } catch (IOException var7) {
                    throw new RuntimeException("Failed to load versions.properties: " + var7, var7);
                }

                Set entries = properties.entrySet();

                for (Object entry1 : entries) {
                    Map.Entry entry = (Map.Entry) entry1;
                    Object key = entry.getKey();
                    Object value = entry.getValue();
                    if (key != null && value != null) {
                        groupArtifactVersionMap.put(key.toString(), value.toString());
                    }
                }
            }
        }

        return groupArtifactVersionMap;
    }

    public static String after(String text, String after) {
        if (!text.contains(after)) {
            return null;
        }
        return text.substring(text.indexOf(after) + after.length());
    }

    public static String before(String text, String before) {
        if (!text.contains(before)) {
            return null;
        }
        return text.substring(0, text.indexOf(before));
    }

    public static String between(String text, String after, String before) {
        text = after(text, after);
        if (text == null) {
            return null;
        }
        return before(text, before);
    }

}
