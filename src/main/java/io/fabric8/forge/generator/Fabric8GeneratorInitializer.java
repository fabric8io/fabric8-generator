/**
 * Copyright 2005-2015 Red Hat, Inc.
 * <p>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.forge.generator;

import io.fabric8.forge.generator.pipeline.JenkinsPipelineLibrary;
import org.jboss.forge.addon.maven.archetype.ArchetypeCatalogFactoryRegistry;
import org.jboss.forge.furnace.container.cdi.events.Local;
import org.jboss.forge.furnace.event.PostStartup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.event.Observes;

/**
 */
public class Fabric8GeneratorInitializer {
    public static final String OBSIDIAN_QUICKSTARTS_CATALOG = "Quickstarts";
    private static final transient Logger LOG = LoggerFactory.getLogger(Fabric8GeneratorInitializer.class);

    public void onInit(@Observes @Local PostStartup startup, ArchetypeCatalogFactoryRegistry registry, JenkinsPipelineLibrary jenkinsPipelineLibrary) throws Exception {
        if (Configuration.isOnPremise()) {
/*
            registry.addArchetypeCatalogFactory(OBSIDIAN_QUICKSTARTS_CATALOG,
                    getClass().getResource("archetype-catalog.xml"));
*/

        }
        LOG.info("Using Jenkinsfile library at: " + jenkinsPipelineLibrary.getWorkflowFolder());

    }
/*
    @Produces
    @ApplicationScoped
    public List<ProjectType> getSupportedProjectTypes(
            SpringBootProjectType springBoot,
            WildFlySwarmProjectType wildFlySwarm,
            VertxProjectType vertx) {
        return Arrays.asList(springBoot, wildFlySwarm, vertx);
    }*/
}
