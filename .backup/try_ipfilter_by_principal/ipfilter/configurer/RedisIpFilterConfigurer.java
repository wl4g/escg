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
package com.wl4g.iam.gateway.ipfilter.configurer;

import static com.wl4g.infra.common.serialize.JacksonUtils.parseJSON;

import java.util.List;

import javax.validation.constraints.NotBlank;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.wl4g.iam.gateway.ipfilter.config.IpFilterProperties;

import reactor.core.publisher.Mono;

/**
 * {@link RedisIpFilterConfigurer}
 * 
 * @author James Wong &lt;wanglsir@gmail.com, 983708408@qq.com&gt;
 * @date 2022-05-05 v1.0.0
 * @since v1.0.0
 */
public class RedisIpFilterConfigurer implements IpFilterConfigurer {

    private @Autowired IpFilterProperties ipListConfig;
    private @Autowired ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<List<FilterStrategy>> loadStrategy(@NotBlank String routeId, @NotBlank String principalName) {
        String prefix = ipListConfig.getConfigPrefix();
        return getOperation().get(prefix, IpFilterConfigurer.getConfigKey(routeId, principalName))
                .map(json -> parseJSON(json, ipListStrategyTypeRef));
    }

    private ReactiveHashOperations<String, String, String> getOperation() {
        return redisTemplate.opsForHash();
    }

    public static final TypeReference<List<FilterStrategy>> ipListStrategyTypeRef = new TypeReference<List<FilterStrategy>>() {
    };

}
