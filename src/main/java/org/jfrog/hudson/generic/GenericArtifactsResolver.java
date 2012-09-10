/*
 * Copyright (C) 2012 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jfrog.hudson.generic;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import org.jfrog.build.api.Dependency;
import org.jfrog.build.api.dependency.UserBuildDependency;
import org.jfrog.build.api.util.Log;
import org.jfrog.build.client.ArtifactoryDependenciesClient;
import org.jfrog.build.util.BuildDependenciesHelper;
import org.jfrog.build.util.DependenciesDownloader;
import org.jfrog.build.util.DependenciesHelper;
import org.jfrog.hudson.util.JenkinsBuildInfoLog;

import java.io.IOException;
import java.util.List;

/**
 * Resolves artifacts from Artifactory (published dependencies and build dependencies)
 * This class is used only in free style generic configurator.
 *
 * @author Shay Yaakov
 */
public class GenericArtifactsResolver {
    private final AbstractBuild build;
    private final ArtifactoryDependenciesClient client;
    private String resolvePattern;
    private Log log;

    public GenericArtifactsResolver(AbstractBuild build, BuildListener listener, ArtifactoryDependenciesClient client,
            String resolvePattern) {
        this.build = build;
        this.client = client;
        this.resolvePattern = resolvePattern;
        log = new JenkinsBuildInfoLog(listener);
    }

    public List<Dependency> retrievePublishedDependencies() throws IOException, InterruptedException {
        DependenciesHelper helper = new DependenciesHelper(createDependenciesDownloader(), log);
        return helper.retrievePublishedDependencies(resolvePattern);
    }

    public List<UserBuildDependency> retrieveBuildDependencies() throws IOException, InterruptedException {
        BuildDependenciesHelper helper = new BuildDependenciesHelper(createDependenciesDownloader(), log);
        return helper.retrieveBuildDependencies(resolvePattern);
    }

    private DependenciesDownloader createDependenciesDownloader() {
        return new DependenciesDownloaderImpl(client, build.getWorkspace(), log);
    }
}
