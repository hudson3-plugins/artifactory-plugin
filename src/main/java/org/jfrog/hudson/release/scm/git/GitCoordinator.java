/*
 * Copyright (C) 2011 JFrog Ltd.
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

package org.jfrog.hudson.release.scm.git;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.plugins.git.Branch;
import hudson.plugins.git.Revision;
import hudson.plugins.git.util.BuildData;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.release.ReleaseAction;
import org.jfrog.hudson.release.scm.AbstractScmCoordinator;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Git scm coordinator. Interacts with the {@link GitManager} to fulfill the release process.
 *
 * @author Yossi Shaul
 */
public class GitCoordinator extends AbstractScmCoordinator {
    private static Logger debuggingLogger = Logger.getLogger(GitCoordinator.class.getName());

    private final ReleaseAction releaseAction;
    private GitManager scmManager;
    private String releaseBranch;
    private String checkoutBranch;
    // the commit hash of the initial checkout
    private String baseCommitIsh;

    private State state = new State();

    private static class State {
        String currentWorkingBranch;
        boolean releaseBranchCreated;
        boolean releaseBranchPushed;
        boolean tagCreated;
        boolean tagPushed;
    }

    public GitCoordinator(AbstractBuild build, BuildListener listener, ReleaseAction releaseAction) {
        super(build, listener);
        this.releaseAction = releaseAction;
    }

    public void prepare() throws IOException, InterruptedException {
        releaseBranch = releaseAction.getReleaseBranch();

        // find the current local built branch
        BuildData buildData = build.getAction(BuildData.class);
        Revision lastRevision = buildData.getLastBuiltRevision();
        Branch branch = lastRevision.getBranches().iterator().next();
        checkoutBranch = StringUtils.removeStart(branch.getName(), "origin/");

        scmManager = new GitManager(build, listener);

        baseCommitIsh = scmManager.getCurrentCommitHash();
    }

    public void beforeReleaseVersionChange() throws IOException, InterruptedException {
        if (releaseAction.isCreateReleaseBranch()) {
            // create a new branch for the release and start it
            scmManager.checkoutBranch(releaseBranch, true);
            state.currentWorkingBranch = releaseBranch;
            state.releaseBranchCreated = true;
        } else {
            // make sure we are on the checkout branch
            scmManager.checkoutBranch(checkoutBranch, false);
            state.currentWorkingBranch = checkoutBranch;
        }
    }

    public void afterSuccessfulReleaseVersionBuild() throws InterruptedException, IOException {
        if (modifiedFilesForReleaseVersion) {
            // commit local changes
            log(String.format("Committing release version on branch '%s'", state.currentWorkingBranch));
            scmManager.commitWorkingCopy(releaseAction.getDefaultVcsConfig().getTagComment());
        }

        if (releaseAction.isCreateVcsTag()) {
            // create tag
            scmManager.createTag(releaseAction.getTagUrl(), releaseAction.getTagComment());
            state.tagCreated = true;
        }

        if (modifiedFilesForReleaseVersion) {
            // push the current branch
            scmManager.push(scmManager.getRemoteUrl(), state.currentWorkingBranch);
            state.releaseBranchPushed = true;
        }

        if (releaseAction.isCreateVcsTag()) {
            // push the tag
            scmManager.pushTag(scmManager.getRemoteUrl(), releaseAction.getTagUrl());
            state.tagPushed = true;
        }
    }

    public void beforeDevelopmentVersionChange() throws IOException, InterruptedException {
        if (releaseAction.isCreateReleaseBranch()) {
            // done working on the release branch, checkout back to master
            scmManager.checkoutBranch(checkoutBranch, false);
            state.currentWorkingBranch = checkoutBranch;
        }
    }

    @Override
    public void afterDevelopmentVersionChange(boolean modified) throws IOException, InterruptedException {
        super.afterDevelopmentVersionChange(modified);
        if (modified) {
            log(String.format("Committing next development version on branch '%s'", state.currentWorkingBranch));
            scmManager.commitWorkingCopy(releaseAction.getNextDevelCommitComment());
        }
    }

    public void buildCompleted() throws IOException, InterruptedException {
        if (build.getResult().isBetterOrEqualTo(Result.SUCCESS)) {
            // pull before attempting to push changes?
            //scmManager.pull(scmManager.getRemoteUrl(), checkoutBranch);
            if (modifiedFilesForDevVersion) {
                scmManager.push(scmManager.getRemoteUrl(), state.currentWorkingBranch);
            }
        } else {
            // go back to the original checkout branch (required to delete the release branch and reset the working copy)
            scmManager.checkoutBranch(checkoutBranch, false);
            state.currentWorkingBranch = checkoutBranch;

            if (state.releaseBranchCreated) {
                safeDeleteBranch(releaseBranch);
            }
            if (state.releaseBranchPushed) {
                safeDeleteRemoteBranch(scmManager.getRemoteUrl(), releaseBranch);
            }
            if (state.tagCreated) {
                safeDeleteTag(releaseAction.getTagUrl());
            }
            if (state.tagPushed) {
                safeDeleteRemoteTag(scmManager.getRemoteUrl(), releaseAction.getTagUrl());
            }
            // reset changes done on the original checkout branch (next dev version)
            safeRevertWorkingCopy();
        }
    }

    private void safeDeleteBranch(String branch) {
        try {
            scmManager.deleteLocalBranch(branch);
        } catch (Exception e) {
            debuggingLogger.log(Level.FINE, "Failed to delete release branch: ", e);
            log("Failed to delete release branch: " + e.getLocalizedMessage());
        }
    }

    private void safeDeleteRemoteBranch(String remoteRepository, String branch) {
        try {
            scmManager.deleteRemoteBranch(remoteRepository, branch);
        } catch (Exception e) {
            debuggingLogger.log(Level.FINE, "Failed to delete remote release branch: ", e);
            log("Failed to delete remote release branch: " + e.getLocalizedMessage());
        }
    }

    private void safeDeleteTag(String tag) {
        try {
            scmManager.deleteLocalTag(tag);
        } catch (Exception e) {
            debuggingLogger.log(Level.FINE, "Failed to delete tag: ", e);
            log("Failed to delete tag: " + e.getLocalizedMessage());
        }
    }

    private void safeDeleteRemoteTag(String remoteRepository, String tag) {
        try {

            scmManager.deleteRemoteTag(remoteRepository, tag);
        } catch (Exception e) {
            debuggingLogger.log(Level.FINE, "Failed to delete remote tag: ", e);
            log("Failed to delete remote tag: " + e.getLocalizedMessage());
        }
    }

    private void safeRevertWorkingCopy() {
        try {
            scmManager.revertWorkingCopy(baseCommitIsh);
        } catch (Exception e) {
            debuggingLogger.log(Level.FINE, "Failed to revert working copy: ", e);
            log("Failed to revert working copy: " + e.getLocalizedMessage());
        }
    }

    public String getRemoteUrlForPom() {
        return null;
    }
}
