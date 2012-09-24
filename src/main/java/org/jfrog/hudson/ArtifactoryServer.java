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

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.util.Scrambler;
import hudson.util.XStream2;
import org.apache.commons.lang.StringUtils;
import org.jfrog.build.api.util.NullLog;
import org.jfrog.build.client.ArtifactoryBuildInfoClient;
import org.jfrog.build.client.ArtifactoryDependenciesClient;
import org.jfrog.build.client.ArtifactoryHttpClient;
import org.jfrog.build.client.ArtifactoryVersion;
import org.jfrog.build.client.ProxyConfiguration;
import org.jfrog.hudson.util.Credentials;
import org.jfrog.hudson.util.JenkinsBuildInfoLog;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents an artifactory instance.
 *
 * @author Yossi Shaul
 */
public class ArtifactoryServer implements Serializable {
    private static final Logger log = Logger.getLogger(ArtifactoryServer.class.getName());

    private static final int DEFAULT_CONNECTION_TIMEOUT = 300;    // 5 Minutes

    private final String url;

    private final Credentials deployerCredentials;
    private Credentials resolverCredentials;

    // Network timeout in seconds to use both for connection establishment and for unanswered requests
    private int timeout = DEFAULT_CONNECTION_TIMEOUT;
    private boolean bypassProxy;

    /**
     * List of repository keys, last time we checked. Copy on write semantics.
     */
    private transient volatile List<String> repositories;

    private transient volatile List<VirtualRepository> virtualRepositories;

    @DataBoundConstructor
    public ArtifactoryServer(String url, Credentials deployerCredentials, Credentials resolverCredentials, int timeout,
            boolean bypassProxy) {
        this.url = StringUtils.removeEnd(url, "/");
        this.deployerCredentials = deployerCredentials;
        this.resolverCredentials = resolverCredentials;
        this.timeout = timeout > 0 ? timeout : DEFAULT_CONNECTION_TIMEOUT;
        this.bypassProxy = bypassProxy;
    }

    public String getName() {
        return url;
    }

    public String getUrl() {
        return url;
    }

    public Credentials getDeployerCredentials() {
        return deployerCredentials;
    }

    public Credentials getResolverCredentials() {
        return resolverCredentials;
    }

    public int getTimeout() {
        return timeout;
    }

    public boolean isBypassProxy() {
        return bypassProxy;
    }

    public List<String> getRepositoryKeys() {
        Credentials resolvingCredentials = getResolvingCredentials();
        ArtifactoryBuildInfoClient client = createArtifactoryClient(resolvingCredentials.getUsername(),
                resolvingCredentials.getPassword(), createProxyConfiguration(Hudson.getInstance().proxy));
        try {
            repositories = client.getLocalRepositoriesKeys();
        } catch (IOException e) {
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.WARNING, "Could not obtain local repositories list from '" + url + "'", e);
            } else {
                log.log(Level.WARNING,
                        "Could not obtain local repositories list from '" + url + "': " + e.getMessage());
            }
            return Lists.newArrayList();
        } catch(IllegalArgumentException e) {
        	return Lists.newArrayList();
        }
        finally {
            client.shutdown();
        }
        return repositories;
    }

    public List<String> getReleaseRepositoryKeysFirst() {
        List<String> repositoryKeys = getRepositoryKeys();
        if (repositoryKeys == null || repositoryKeys.isEmpty()) {
            return Lists.newArrayList();
        }
        Collections.sort(repositoryKeys, new RepositoryComparator());
        return repositoryKeys;
    }

    public List<String> getSnapshotRepositoryKeysFirst() {
        List<String> repositoryKeys = getRepositoryKeys();
        if (repositoryKeys == null || repositoryKeys.isEmpty()) {
            return Lists.newArrayList();
        }
        Collections.sort(repositoryKeys, Collections.reverseOrder(new RepositoryComparator()));
        return repositoryKeys;
    }

    public Map getStagingStrategy(PluginSettings selectedStagingPlugin, String buildName) throws IOException {
        Credentials resolvingCredentials = getResolvingCredentials();
        ArtifactoryBuildInfoClient client = createArtifactoryClient(resolvingCredentials.getUsername(),
                resolvingCredentials.getPassword(), createProxyConfiguration(Hudson.getInstance().proxy));
        try {
            return client.getStagingStrategy(selectedStagingPlugin.getPluginName(), buildName,
                    selectedStagingPlugin.getParamMap());
        } finally {
            client.shutdown();
        }
    }

    private static class RepositoryComparator implements Comparator<String>, Serializable {

        public int compare(String o1, String o2) {
            if (o1.contains("snapshot") && !o2.contains("snapshot")) {
                return 1;
            } else {
                return -1;
            }
        }
    }

    public List<VirtualRepository> getVirtualRepositoryKeys() {
        Credentials resolvingCredentials = getResolvingCredentials();
        ArtifactoryBuildInfoClient client = createArtifactoryClient(resolvingCredentials.getUsername(),
                resolvingCredentials.getPassword(), createProxyConfiguration(Hudson.getInstance().proxy));
        try {
            List<String> keys = client.getVirtualRepositoryKeys();
            virtualRepositories = Lists.newArrayList(Lists.transform(keys, new Function<String, VirtualRepository>() {
                public VirtualRepository apply(String from) {
                    return new VirtualRepository(from, from);
                }
            }));
        } catch (IOException e) {
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.WARNING, "Could not obtain virtual repositories list from '" + url + "'", e);
            } else {
                log.log(Level.WARNING,
                        "Could not obtain virtual repositories list from '" + url + "': " + e.getMessage());
            }
            return Lists.newArrayList();
        } finally {
            client.shutdown();
        }
        virtualRepositories
                .add(0, new VirtualRepository("-- To use Artifactory for resolution select a virtual repository --",
                        ""));
        return virtualRepositories;
    }

    public boolean isArtifactoryPro() {
        Credentials resolvingCredentials = getResolvingCredentials();
        try {
            ArtifactoryHttpClient client = new ArtifactoryHttpClient(url, resolvingCredentials.getUsername(),
                    resolvingCredentials.getPassword(), new NullLog());
            ArtifactoryVersion version = client.getVersion();
            return version.hasAddons();
        } catch (IOException e) {
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.WARNING, "Could not obtain artifactory version from '" + url + "'", e);
            } else {
                log.log(Level.WARNING,
                        "Could not obtain artifactory version from '" + url + "': " + e.getMessage());
            }
        }
        return false;
    }

    public List<UserPluginInfo> getStagingUserPluginInfo() {
        List<UserPluginInfo> infosToReturn = Lists.newArrayList(UserPluginInfo.NO_PLUGIN);
        gatherUserPluginInfo(infosToReturn, "staging");
        return infosToReturn;
    }

    public List<UserPluginInfo> getPromotionsUserPluginInfo() {
        List<UserPluginInfo> infosToReturn = Lists.newArrayList(UserPluginInfo.NO_PLUGIN);
        gatherUserPluginInfo(infosToReturn, "promotions");
        return infosToReturn;
    }

    /**
     * This method might run on slaves, this is why we provide it with a proxy from the master config
     */
    public ArtifactoryBuildInfoClient createArtifactoryClient(String userName, String password,
            ProxyConfiguration proxyConfiguration) {
        ArtifactoryBuildInfoClient client = new ArtifactoryBuildInfoClient(url, userName, password, new NullLog());
        client.setConnectionTimeout(timeout);
        if (!bypassProxy && proxyConfiguration != null) {
        	if (proxyConfiguration.host != null && proxyConfiguration.port != 0)
        		client.setProxyConfiguration(proxyConfiguration.host,
        				proxyConfiguration.port,
        				proxyConfiguration.username,
        				proxyConfiguration.password);
        }

        return client;
    }

    public ProxyConfiguration createProxyConfiguration(hudson.ProxyConfiguration proxy) {
        ProxyConfiguration proxyConfiguration = null;
        if (proxy != null) {
            proxyConfiguration = new ProxyConfiguration();
            proxyConfiguration.host = proxy.name;
            proxyConfiguration.port = proxy.port;
            proxyConfiguration.username = proxy.getUserName();
            proxyConfiguration.password = proxy.getPassword();
        }

        return proxyConfiguration;
    }


    /**
     * This method might run on slaves, this is why we provide it with a proxy from the master config
     */
    public ArtifactoryDependenciesClient createArtifactoryDependenciesClient(String userName, String password,
            ProxyConfiguration proxyConfiguration, BuildListener listener) {
        ArtifactoryDependenciesClient client = new ArtifactoryDependenciesClient(url, userName, password,
                new JenkinsBuildInfoLog(listener));
        client.setConnectionTimeout(timeout);
        if (!bypassProxy && proxyConfiguration != null) {
        	if (proxyConfiguration.host != null && proxyConfiguration.port != 0)
        		client.setProxyConfiguration(proxyConfiguration.host, proxyConfiguration.port, proxyConfiguration.username,
        				proxyConfiguration.password);
        }

        return client;
    }

    /**
     * When upgrading from an older version, a user might have resolver credentials as local variables. This converter
     * Will check for existing old resolver credentials and "move" them to a credentials object instead
     */
    public static final class ConverterImpl extends XStream2.PassthruConverter<ArtifactoryServer> {

        public ConverterImpl(XStream2 xstream) {
            super(xstream);
        }

        @Override
        protected void callback(ArtifactoryServer server, UnmarshallingContext context) {
            if (StringUtils.isNotBlank(server.userName) && (server.resolverCredentials == null)) {
                server.resolverCredentials = new Credentials(server.userName, Scrambler.descramble(server.password));
            }
        }
    }

    /**
     * Decides what are the preferred credentials to use for resolving the repo keys of the server
     *
     * @return Preferred credentials for repo resolving. Never null.
     */
    public Credentials getResolvingCredentials() {
        if (getResolverCredentials() != null) {
            return getResolverCredentials();
        }

        if (getDeployerCredentials() != null) {
            return getDeployerCredentials();
        }

        return new Credentials(null, null);
    }

    /**
     * @deprecated: Use org.jfrog.hudson.DeployerOverrider#getOverridingDeployerCredentials()
     */
    @Deprecated
    private transient String userName;

    /**
     * @deprecated: Use org.jfrog.hudson.DeployerOverrider#getOverridingDeployerCredentials()
     */
    @Deprecated
    private transient String password;    // base64 scrambled password

    private void gatherUserPluginInfo(List<UserPluginInfo> infosToReturn, String pluginKey) {
        Credentials resolvingCredentials = getResolvingCredentials();
        ArtifactoryBuildInfoClient client = createArtifactoryClient(resolvingCredentials.getUsername(),
                resolvingCredentials.getPassword(), createProxyConfiguration(Hudson.getInstance().proxy));
        try {
            Map<String, List<Map>> userPluginInfo = client.getUserPluginInfo();
            if (userPluginInfo != null && userPluginInfo.containsKey(pluginKey)) {
                List<Map> stagingUserPluginInfo = userPluginInfo.get(pluginKey);
                if (stagingUserPluginInfo != null) {
                    for (Map stagingPluginInfo : stagingUserPluginInfo) {
                        infosToReturn.add(new UserPluginInfo(stagingPluginInfo));
                    }
                    Collections.sort(infosToReturn, new Comparator<UserPluginInfo>() {
                        public int compare(UserPluginInfo o1, UserPluginInfo o2) {
                            return o1.getPluginName().compareTo(o2.getPluginName());
                        }
                    });
                }
            }
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to obtain user plugin info: " + e.getMessage());
        } finally {
            client.shutdown();
        }
    }
}
