/*
 * Copyright (C) 2011 JFrog Ltd.
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

package org.jfrog.hudson.util;

import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.DeployerOverrider;
import org.jfrog.hudson.ServerDetails;

/**
 * Container class for build context fields
 *
 * @author Tomer Cohen
 */
public class PublisherContext {

    private ArtifactoryServer artifactoryServer;
    private ServerDetails serverDetails;
    private DeployerOverrider deployerOverrider;
    private boolean runChecks;
    private boolean includePublishArtifacts;
    private String violationRecipients;
    private String scopes;
    private boolean licenseAutoDiscovery;
    private boolean discardOldBuilds;
    private boolean discardBuildArtifacts;
    private boolean deployArtifacts;
    private IncludesExcludes includesExcludes;
    private boolean skipBuildInfoDeploy;
    private boolean includeEnvVars;
    private boolean evenIfUnstable;
    private boolean deployMaven;
    private boolean deployIvy;
    private String artifactsPattern = "";
    private String ivyPattern = "";
    private String matrixParams;
    private boolean maven2Compatible;
    private boolean enableIssueTrackerIntegration;
    private boolean aggregateBuildIssues;
    private String aggregationBuildStatus;

    private PublisherContext() {
    }

    public ArtifactoryServer getArtifactoryServer() {
        return artifactoryServer;
    }

    public String getArtifactsPattern() {
        return getCleanString(artifactsPattern);
    }

    public String getMatrixParams() {
        return matrixParams;
    }

    public String getIvyPattern() {
        return getCleanString(ivyPattern);
    }

    public boolean isDiscardBuildArtifacts() {
        return discardBuildArtifacts;
    }

    public ServerDetails getServerDetails() {
        return serverDetails;
    }

    public IncludesExcludes getIncludesExcludes() {
        return includesExcludes;
    }

    public boolean isSkipBuildInfoDeploy() {
        return skipBuildInfoDeploy;
    }

    public boolean isIncludeEnvVars() {
        return includeEnvVars;
    }

    public DeployerOverrider getDeployerOverrider() {
        return deployerOverrider;
    }

    public String getScopes() {
        return scopes;
    }

    public boolean isRunChecks() {
        return runChecks;
    }

    public boolean isIncludePublishArtifacts() {
        return includePublishArtifacts;
    }

    public String getViolationRecipients() {
        return violationRecipients;
    }

    public boolean isLicenseAutoDiscovery() {
        return licenseAutoDiscovery;
    }

    public boolean isDiscardOldBuilds() {
        return discardOldBuilds;
    }

    public boolean isDeployArtifacts() {
        return deployArtifacts;
    }

    public final String getArtifactoryName() {
        return serverDetails != null ? serverDetails.artifactoryName : null;
    }

    public boolean isDeployMaven() {
        return deployMaven;
    }

    public boolean isDeployIvy() {
        return deployIvy;
    }

    public boolean isEvenIfUnstable() {
        return evenIfUnstable;
    }

    private String getCleanString(String stringToClean) {
        return StringUtils.removeEnd(StringUtils.removeStart(stringToClean, "\""), "\"");
    }

    public boolean isMaven2Compatible() {
        return maven2Compatible;
    }

    public boolean isEnableIssueTrackerIntegration() {
        return enableIssueTrackerIntegration;
    }

    public boolean isAggregateBuildIssues() {
        return aggregateBuildIssues;
    }

    public String getAggregationBuildStatus() {
        return aggregationBuildStatus;
    }

    public static class Builder {
        PublisherContext publisher = new PublisherContext();

        public PublisherContext build() {
            if (publisher.artifactoryServer == null) {
                throw new IllegalArgumentException("artifactoryServer cannot be null");
            }
            if (publisher.serverDetails == null) {
                throw new IllegalArgumentException("serverDetails cannot be null");
            }
            return publisher;
        }

        public Builder artifactoryServer(ArtifactoryServer artifactoryServer) {
            publisher.artifactoryServer = artifactoryServer;
            return this;
        }

        public Builder serverDetails(ServerDetails serverDetails) {
            publisher.serverDetails = serverDetails;
            return this;
        }

        public Builder deployerOverrider(DeployerOverrider deployerOverrider) {
            publisher.deployerOverrider = deployerOverrider;
            return this;
        }

        public Builder runChecks(boolean runChecks) {
            publisher.runChecks = runChecks;
            return this;
        }

        public Builder includePublishArtifacts(boolean includePublishArtifacts) {
            publisher.includePublishArtifacts = includePublishArtifacts;
            return this;
        }

        public Builder violationRecipients(String violationRecipients) {
            publisher.violationRecipients = violationRecipients;
            return this;
        }

        public Builder scopes(String scopes) {
            publisher.scopes = scopes;
            return this;
        }

        public Builder licenseAutoDiscovery(boolean licenseAutoDiscovery) {
            publisher.licenseAutoDiscovery = licenseAutoDiscovery;
            return this;
        }

        public Builder discardOldBuilds(boolean discardOldBuilds) {
            publisher.discardOldBuilds = discardOldBuilds;
            return this;
        }

        public Builder deployArtifacts(boolean deployArtifacts) {
            publisher.deployArtifacts = deployArtifacts;
            return this;
        }

        public Builder includesExcludes(IncludesExcludes includesExcludes) {
            publisher.includesExcludes = includesExcludes;
            return this;
        }

        public Builder skipBuildInfoDeploy(boolean skipBuildInfoDeploy) {
            publisher.skipBuildInfoDeploy = skipBuildInfoDeploy;
            return this;
        }

        public Builder includeEnvVars(boolean includeEnvVars) {
            publisher.includeEnvVars = includeEnvVars;
            return this;
        }

        public Builder discardBuildArtifacts(boolean discardBuildArtifacts) {
            publisher.discardBuildArtifacts = discardBuildArtifacts;
            return this;
        }

        public Builder matrixParams(String matrixParams) {
            publisher.matrixParams = matrixParams;
            return this;
        }

        public Builder artifactsPattern(String artifactsPattern) {
            publisher.artifactsPattern = artifactsPattern;
            return this;
        }

        public Builder ivyPattern(String ivyPattern) {
            publisher.ivyPattern = ivyPattern;
            return this;
        }

        public Builder deployMaven(boolean deployMaven) {
            publisher.deployMaven = deployMaven;
            return this;
        }

        public Builder deployIvy(boolean deployIvy) {
            publisher.deployIvy = deployIvy;
            return this;
        }

        public Builder evenIfUnstable(boolean evenIfUnstable) {
            publisher.evenIfUnstable = evenIfUnstable;
            return this;
        }

        public Builder maven2Compatible(boolean maven2Compatible) {
            publisher.maven2Compatible = maven2Compatible;
            return this;
        }

        public Builder enableIssueTrackerIntegration(boolean enableIssueTrackerIntegration) {
            publisher.enableIssueTrackerIntegration = enableIssueTrackerIntegration;
            return this;
        }

        public Builder aggregateBuildIssues(boolean aggregateBuildIssues) {
            publisher.aggregateBuildIssues = aggregateBuildIssues;
            return this;
        }

        public Builder aggregationBuildStatus(String aggregationBuildStatus) {
            publisher.aggregationBuildStatus = aggregationBuildStatus;
            return this;
        }
    }
}
