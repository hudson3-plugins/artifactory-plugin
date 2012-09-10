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

package org.jfrog.hudson.release.gradle;

import com.google.common.collect.Maps;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.release.scm.AbstractScmCoordinator;
import org.jfrog.hudson.release.scm.ScmCoordinator;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A release wrapper for Gradle projects. Allows performing release steps on Gradle. This class is not a direct {@link
 * hudson.tasks.BuildWrapper} but is called from the {@link org.jfrog.hudson.gradle.ArtifactoryGradleConfigurator} as
 * part of the release procedure.
 *
 * @author Tomer Cohen
 */
public class GradleReleaseWrapper {
    private final static Logger debuggingLogger = Logger.getLogger(GradleReleaseWrapper.class.getName());

    private String tagPrefix;
    private String releaseBranchPrefix;
    private String alternativeTasks;
    private String releasePropsKeys;
    private String nextIntegPropsKeys;

    private transient ScmCoordinator scmCoordinator;

    @DataBoundConstructor
    public GradleReleaseWrapper(String releaseBranchPrefix, String tagPrefix, String alternativeTasks,
            String releasePropsKeys, String nextIntegPropsKeys) {
        this.releaseBranchPrefix = releaseBranchPrefix;
        this.tagPrefix = tagPrefix;
        this.alternativeTasks = alternativeTasks;
        this.releasePropsKeys = releasePropsKeys;
        this.nextIntegPropsKeys = nextIntegPropsKeys;
    }

    public ScmCoordinator getScmCoordinator() {
        return scmCoordinator;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public String getTagPrefix() {
        return tagPrefix;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setTagPrefix(String tagPrefix) {
        this.tagPrefix = tagPrefix;
    }

    public String getReleasePropsKeys() {
        return releasePropsKeys;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setReleasePropsKeys(String releasePropsKeys) {
        this.releasePropsKeys = releasePropsKeys;
    }

    public String getNextIntegPropsKeys() {
        return nextIntegPropsKeys;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setNextIntegPropsKeys(String nextIntegPropsKeys) {
        this.nextIntegPropsKeys = nextIntegPropsKeys;
    }

    public String getReleaseBranchPrefix() {
        return releaseBranchPrefix;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setReleaseBranchPrefix(String releaseBranchPrefix) {
        this.releaseBranchPrefix = releaseBranchPrefix;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public String getAlternativeTasks() {
        return alternativeTasks;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setAlternativeTasks(String alternativeTasks) {
        this.alternativeTasks = alternativeTasks;
    }

    public String[] getReleasePropsKeysList() {
        return stringToArray(getReleasePropsKeys());
    }

    public String[] getNextIntegPropsKeysList() {
        return stringToArray(getNextIntegPropsKeys());
    }

    private String[] stringToArray(String commaSeparatedString) {
        commaSeparatedString = StringUtils.trimToEmpty(commaSeparatedString);
        return StringUtils.split(commaSeparatedString, ", ");
    }

    public void setUp(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {
        final GradleReleaseAction releaseAction = build.getAction(GradleReleaseAction.class);
        if (releaseAction == null) {
            // this is a normal non release build, continue with normal environment
            return;
        }
        log(listener, "Release build triggered");
        scmCoordinator = AbstractScmCoordinator.createScmCoordinator(build, listener, releaseAction);
        scmCoordinator.prepare();
        // TODO: replace the versioning mode with something else
        if (!releaseAction.getVersioning().equals(GradleReleaseAction.VERSIONING.NONE)) {
            scmCoordinator.beforeReleaseVersionChange();
            // change to release properties values
            boolean modified = changeProperties(build, releaseAction, true, listener);
            scmCoordinator.afterReleaseVersionChange(modified);
        }
    }

    public boolean tearDown(AbstractBuild build, BuildListener listener) {
        Result result = build.getResult();
        if (result == null || result.isWorseThan(Result.SUCCESS)) {
            // revert will happen by the listener
            return true;
        }
        final GradleReleaseAction releaseAction = build.getAction(GradleReleaseAction.class);
        try {
            scmCoordinator.afterSuccessfulReleaseVersionBuild();
            if (!releaseAction.getVersioning().equals(GradleReleaseAction.VERSIONING.NONE)) {
                scmCoordinator.beforeDevelopmentVersionChange();
                boolean modified = changeProperties(build, releaseAction, false, listener);
                scmCoordinator.afterDevelopmentVersionChange(modified);
            }
        } catch (Exception e) {
            listener.getLogger().println("Failure in post build SCM action: " + e.getMessage());
            debuggingLogger.log(Level.FINE, "Failure in post build SCM action: ", e);
            return false;
        }
        return true;
    }

    private boolean changeProperties(AbstractBuild build, GradleReleaseAction release, boolean releaseVersion,
            BuildListener listener) throws IOException, InterruptedException {
        FilePath root = release.getModuleRoot(build.getEnvironment(listener));
        debuggingLogger.fine("Root directory is: " + root.getRemote());
        String[] modules = release.getReleaseProperties();
        Map<String, String> modulesByName = Maps.newHashMap();
        for (String module : modules) {
            String version = releaseVersion ? release.getReleaseVersionFor(module) :
                    release.getCurrentVersionFor(module);
            modulesByName.put(module, version);
        }

        String[] additionalModuleProperties = release.getNextIntegProperties();
        for (String property : additionalModuleProperties) {
            String version = releaseVersion ? release.getReleaseVersionFor(property) :
                    release.getNextVersionFor(property);
            modulesByName.put(property, version);
        }
        debuggingLogger.fine("Changing version of gradle properties");
        FilePath gradlePropertiesFilePath = new FilePath(root, "gradle.properties");
        String next = releaseVersion ? "release" : "development";
        log(listener,
                "Changing gradle.properties at " + gradlePropertiesFilePath.getRemote() + " for " + next + " version");
        scmCoordinator.edit(gradlePropertiesFilePath);
        return gradlePropertiesFilePath.act(new GradlePropertiesTransformer(modulesByName));
    }

    private void log(BuildListener listener, String message) {
        listener.getLogger().println("[RELEASE] " + message);
    }
}
