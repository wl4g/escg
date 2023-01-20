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
package com.wl4g.escg.requestlimit.limiter;

import java.util.Map;

import org.springframework.web.server.ServerWebExchange;

import com.wl4g.escg.requestlimit.IamRequestLimiterFilterFactory;
import com.wl4g.escg.requestlimit.config.IamRequestLimiterProperties.LimiterProperties.AbstractLimiterProperties;
import com.wl4g.escg.requestlimit.limiter.IamRequestLimiter.RequestLimiterPrivoder;
import com.wl4g.escg.requestlimit.limiter.quota.RedisQuotaRequestLimiterStrategy;
import com.wl4g.escg.requestlimit.limiter.rate.RedisRateRequestLimiterStrategy;
import com.wl4g.infra.common.framework.operator.Operator;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import reactor.core.publisher.Mono;

/**
 * {@link IamRequestLimiter}
 * 
 * @author James Wong &lt;wanglsir@gmail.com, 983708408@qq.com&gt;
 * @date 2022-04-21 v1.0.0
 * @since v1.0.0
 */
public interface IamRequestLimiter extends Operator<RequestLimiterPrivoder> {

    Mono<LimitedResult> isAllowed(
            IamRequestLimiterFilterFactory.Config config,
            ServerWebExchange exchange,
            String routeId,
            String limitKey);

    AbstractLimiterProperties getDefaultLimiter();

    @Getter
    @ToString
    @AllArgsConstructor
    public static class LimitedResult {
        private final boolean allowed;
        private final long tokensLeft;
        private final Map<String, String> headers;
    }

    @Getter
    @AllArgsConstructor
    public static enum RequestLimiterPrivoder {
        RedisRateLimiter(RedisRateRequestLimiterStrategy.class),

        RedisQuotaLimiter(RedisQuotaRequestLimiterStrategy.class);

        private final Class<? extends RequestLimiterStrategy> strategyClass;
    }

}
