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
package io.fabric8.forge.generator.cache;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.eviction.EvictionType;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import java.util.concurrent.TimeUnit;

/**
 */
public class CacheManagerProvider {

    @Produces
    @ApplicationScoped
    public EmbeddedCacheManager createCacheManager() {
        EmbeddedCacheManager manager = new DefaultCacheManager();

        addCache(manager, CacheNames.USER_NAMESPACES, 1000, 30);

        addCache(manager, CacheNames.GITHUB_ACCOUNT_FROM_SECRET, 1000, 30);
        addCache(manager, CacheNames.GITHUB_ORGANISATIONS, 1000, 30);

        addCache(manager, CacheNames.GOGS_ACCOUNT_FROM_SECRET, 1000, 30);
        addCache(manager, CacheNames.GOGS_ORGANISATIONS, 1000, 30);

        return manager;
    }

    protected static void addCache(EmbeddedCacheManager manager, String name, int cacheCount, int lifespanSeconds) {
        manager.defineConfiguration(name, new ConfigurationBuilder()
                .memory().evictionType(EvictionType.COUNT).size(cacheCount).
                        eviction().expiration().lifespan(lifespanSeconds, TimeUnit.SECONDS)
                .build());
    }
}
