/*
 * Copyright (C) 2010 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jfrog.hudson;

import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildBadgeAction;
import org.jfrog.hudson.util.ExtractorUtils;

/**
 * Result of the redeploy publisher. Currently only a link to Artifactory build info.
 *
 * @author Yossi Shaul
 */
public class BuildInfoResultAction implements BuildBadgeAction {

    private final String url;

    /**
     * @deprecated Only here to keep compatibility with version 1.0.7 and below (part of the xstream de-serialization)
     */
    @Deprecated
    private transient ArtifactoryRedeployPublisher artifactoryRedeployPublisher;
    /**
     * @deprecated Only here to keep compatibility with version 1.0.7 and below (part of the xstream de-serialization)
     */
    @Deprecated
    private transient AbstractBuild build;

    public BuildInfoResultAction(String artifactoryRootUrl, AbstractBuild build) {
        url = generateUrl(artifactoryRootUrl, build);
    }

    public String getIconFileName() {
        return "/plugin/artifactory/images/artifactory-icon.png";
    }

    public String getDisplayName() {
        return "Artifactory Build Info";
    }

    public String getUrlName() {
        // for backward compatibility if url is empty calculate it from the old structs
        if (url == null && artifactoryRedeployPublisher != null && build != null) {
            return generateUrl(artifactoryRedeployPublisher.getArtifactoryName(), build);
        } else {
            return url;
        }
    }

    private String generateUrl(String artifactoryRootUrl, AbstractBuild build) {
        return artifactoryRootUrl + "/webapp/builds/"
                + Util.rawEncode(ExtractorUtils.sanitizeBuildName(build.getParent().getFullName())) + "/"
                + build.getNumber();
    }
}