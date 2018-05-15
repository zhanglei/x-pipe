package com.ctrip.xpipe.redis.proxy.config;

import com.ctrip.xpipe.api.config.Config;
import com.ctrip.xpipe.api.foundation.FoundationService;
import com.ctrip.xpipe.config.AbstractConfigBean;
import com.ctrip.xpipe.config.CompositeConfig;
import com.ctrip.xpipe.config.DefaultFileConfig;
import com.ctrip.xpipe.spring.AbstractProfile;
import com.ctrip.xpipe.utils.OsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;


/**
 * @author chen.zhu
 * <p>
 * May 10, 2018
 */

@Component
@Lazy
@Profile(AbstractProfile.PROFILE_NAME_PRODUCTION)
public class DefaultProxyConfig extends AbstractConfigBean implements ProxyConfig {

    private static final Logger logger = LoggerFactory.getLogger(DefaultProxyConfig.class);

    private static final String PROXY_PROPERTIES_PATH = String.format("/opt/data/%s", FoundationService.DEFAULT.getAppId());

    private static final String PROXY_PROPERTIES_FILE = "proxy.properties";

    private static final String KEY_FRONT_END_PORT = "frontend.port";

    private static final String KEY_FRONT_END_WORKER_NUMBERS = "frontend.worker.num";

    private static final String KEY_SSL_ENABLED = "frontend.ssl.enabled";

    private static final String KEY_BACK_END_WORKER_NUMBERS = "backend.worker.num";

    private static final String KEY_ENDPOINT_HEALTH_CHECK_INTERVAL = "endpoint.check.interval.sec";

    private static final String KEY_TRAFFIC_REPORT_INTERVAL = "traffic.report.interval.milli";

    public DefaultProxyConfig() {
        setConfig(initConfig());
    }

    private Config initConfig() {
        CompositeConfig compositeConfig = new CompositeConfig();
        try {
            compositeConfig.addConfig(new DefaultFileConfig(PROXY_PROPERTIES_PATH, PROXY_PROPERTIES_FILE));
        } catch (Exception e) {
            logger.info("[DefaultProxyConfig]{}", e);
        }
        try {
            compositeConfig.addConfig(new DefaultFileConfig());
        } catch (Exception e) {
            logger.info("[DefaultProxyConfig]{}", e);
        }
        return compositeConfig;
    }

    @Override
    public int frontendPort() {
        return getIntProperty(KEY_FRONT_END_PORT, 9527);
    }

    @Override
    public int frontendWorkerEventLoopNum() {
        return getIntProperty(KEY_FRONT_END_WORKER_NUMBERS, OsUtils.getCpuCount());
    }

    @Override
    public long getTrafficReportIntervalMillis() {
        return getLongProperty(KEY_TRAFFIC_REPORT_INTERVAL, 5 * 1000L);
    }

    @Override
    public boolean isSslEnabled() {
        return getBooleanProperty(KEY_SSL_ENABLED, false);
    }

    @Override
    public int backendEventLoopNum() {
        return getIntProperty(KEY_BACK_END_WORKER_NUMBERS, 2);
    }

    @Override
    public int endpointHealthCheckIntervalSec() {
        return getIntProperty(KEY_ENDPOINT_HEALTH_CHECK_INTERVAL, 60);
    }

    @Override
    public String getPassword() {
        return getProperty(KEY_CERT_PASSWORD, "");
    }

    @Override
    public String getServerCertFilePath() {
        return getProperty(KEY_SERVER_CERT_FILE_PATH, "/opt/cert/xpipe_server.jks");
    }

    @Override
    public String getClientCertFilePath() {
        return getProperty(KEY_CLIENT_CERT_FILE_PATH, "/opt/cert/xpipe_client.jks");
    }

    @Override
    public String getCertFileType() {
        return getProperty(KEY_CERT_FILE_TYPE, "JKS");
    }
}