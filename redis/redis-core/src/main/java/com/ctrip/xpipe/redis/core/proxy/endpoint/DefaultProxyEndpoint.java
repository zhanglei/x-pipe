package com.ctrip.xpipe.redis.core.proxy.endpoint;

import com.ctrip.xpipe.endpoint.DefaultEndPoint;

import java.net.InetSocketAddress;

/**
 * @author chen.zhu
 * <p>
 * May 09, 2018
 */
public class DefaultProxyEndpoint extends DefaultEndPoint implements ProxyEndpoint {

    public DefaultProxyEndpoint(String url) {
        super(url);
    }

    public DefaultProxyEndpoint(String ip, int port) {
        this("proxy://" + ip + ":" + port);
    }

    public DefaultProxyEndpoint(InetSocketAddress address) {
        this(address.getHostName(), address.getPort());
    }

    public DefaultProxyEndpoint() {
    }

    @Override
    public boolean isSslEnabled() {
        return PROXY_SCHEME.PROXYTLS.matches(getScheme());
    }

    @Override
    public String rawUri() {
        return getRawUrl();
    }
}