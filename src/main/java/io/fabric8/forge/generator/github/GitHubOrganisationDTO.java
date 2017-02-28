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
package io.fabric8.forge.generator.github;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.kohsuke.github.GHOrganization;

import java.io.IOException;
import java.net.URL;

/**
 * Represents a github organisation you can pick
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GitHubOrganisationDTO implements Comparable<GitHubOrganisationDTO> {
    private String name;
    private String avatarUrl;
    private String htmlUrl;

    public GitHubOrganisationDTO() {
    }

    public GitHubOrganisationDTO(String name) {
        this.name = name;
        this.htmlUrl = "https://github.com/" + name;

        // TODO should we add an avatar for the current user?
    }

    public GitHubOrganisationDTO(GHOrganization organization) throws IOException {
        this.name = organization.getName();
        this.avatarUrl = organization.getAvatarUrl();
        URL htmlUrl = organization.getHtmlUrl();
        if (htmlUrl != null) {
            this.htmlUrl = htmlUrl.toString();
        }
    }

    @Override
    public String toString() {
        return "GitHubOrganisationDTO{" +
                "name='" + name + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        GitHubOrganisationDTO that = (GitHubOrganisationDTO) o;

        return name != null ? name.equals(that.name) : that.name == null;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

    @Override
    public int compareTo(GitHubOrganisationDTO that) {
        return this.name.compareTo(that.name);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    public void setHtmlUrl(String htmlUrl) {
        this.htmlUrl = htmlUrl;
    }
}
