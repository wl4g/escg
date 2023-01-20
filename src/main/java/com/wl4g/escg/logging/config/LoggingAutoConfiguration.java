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
package com.wl4g.escg.logging.config;

import static com.wl4g.escg.constant.EscgConstants.CONF_PREFIX_ESCG_LOGGING;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.wl4g.escg.logging.RequestLoggingGlobalFilter;
import com.wl4g.escg.logging.ResponseLoggingGlobalFilter;

/**
 * {@link LoggingMessageAutoConfiguration}
 * 
 * @author James Wong &lt;wanglsir@gmail.com, 983708408@qq.com&gt;
 * @date 2021-09-03 v1.0.0
 * @since v1.0.0
 */
public class LoggingAutoConfiguration {

    @Bean
    @ConfigurationProperties(prefix = CONF_PREFIX_ESCG_LOGGING)
    public LoggingProperties loggingProperties() {
        return new LoggingProperties();
    }

    @Bean
    public RequestLoggingGlobalFilter requestLoggingGlobalFilter(LoggingProperties loggingConfig) {
        return new RequestLoggingGlobalFilter(loggingConfig);
    }

    @Bean
    public ResponseLoggingGlobalFilter responseLoggingGlobalFilter(LoggingProperties loggingConfig) {
        return new ResponseLoggingGlobalFilter(loggingConfig);
    }

}
