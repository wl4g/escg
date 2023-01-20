/*
 * Copyright 2017 ~ 2025 the original authors James Wong.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.wl4g.escg.traffic.config;

import static java.util.Arrays.asList;

import java.time.Duration;
import java.util.List;

import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.cloud.gateway.config.HttpClientProperties.Pool;
import org.springframework.cloud.gateway.config.HttpClientProperties.Proxy;
import org.springframework.cloud.gateway.config.HttpClientProperties.Ssl;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * {@link TrafficProperties}
 * 
 * @author James Wong &lt;wanglsir@gmail.com, 983708408@qq.com&gt;
 * @date 2022-04-26 v1.0.0
 * @since v1.0.0
 */
@Getter
@Setter
@ToString
@Validated
public class TrafficProperties {

    private ReplicationProperties defaultReplication = new ReplicationProperties();

    @Getter
    @Setter
    @ToString
    @Validated
    public static class ReplicationProperties {

        /**
         * The URIs to clone request data traffic to multiple target servers.
         */
        private List<String> targetUris = asList("http://localhost:8080/", "https://localhost:8080/");

        /**
         * The sampling percentage rate of traffic that needs to be replication.
         */
        private double percentage = 1d;

        /** Enables wiretap debugging for Netty HttpClient. */
        private boolean wiretap = false;

        /** The connect timeout in millis, the default is 45s. */
        private Integer connectTimeout = 45_000;

        /** The response timeout. */
        private Duration responseTimeout = Duration.ofMillis(60_000);

        /** The max response header size. */
        private DataSize maxHeaderSize = DataSize.ofBytes(65535);

        /** The max initial line length. */
        private DataSize maxInitialLineLength;

        /** Pool configuration for Netty HttpClient. */
        private Pool pool = new Pool();

        /** Proxy configuration for Netty HttpClient. */
        private Proxy proxy = new Proxy();

        /** SSL configuration for Netty HttpClient. */
        private Ssl ssl = new Ssl();

        public HttpClientProperties toHttpClientProperties() {
            HttpClientProperties hcp = new HttpClientProperties();
            hcp.setWiretap(isWiretap());
            hcp.setConnectTimeout(getConnectTimeout());
            hcp.setResponseTimeout(getResponseTimeout());
            hcp.setMaxHeaderSize(getMaxHeaderSize());
            hcp.setMaxInitialLineLength(getMaxInitialLineLength());
            hcp.setPool(getPool());
            hcp.setProxy(getProxy());
            hcp.setSsl(getSsl());
            return hcp;
        }
    }

}
