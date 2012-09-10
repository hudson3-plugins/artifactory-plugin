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

package org.jfrog.hudson;

import hudson.maven.MavenBuild;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenTestDataPublisher;

import org.jfrog.hudson.util.IncludesExcludes;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * Integration test of {@link org.jfrog.hudson.ArtifactoryRedeployPublisher} configuration.
 *
 * @author Yossi Shaul
 */
public class ArtifactoryRedeployPublisherITest extends HudsonTestCase {

    @Test
    public void testConfigurationRoundTrip() throws Exception {
        MavenModuleSet project = hudson.createProject(MavenModuleSet.class, "test" + hudson.getItems().size());

        ArtifactoryRedeployPublisher before = new ArtifactoryRedeployPublisher(null, true,
                new IncludesExcludes("", ""),
                null, false, true, true, false, "", false, "", true,
                true, false, true, "", true, true, "Released", false);
        project.getPublishersList().add(before);

        // submit the configuration form
        submit(createWebClient().getPage(project, "configure").getFormByName("config"));

        ArtifactoryRedeployPublisher after = project.getPublishersList().get(ArtifactoryRedeployPublisher.class);

        assertEqualDataBoundBeans(before, after);
    }
}
