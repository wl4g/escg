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
package com.wl4g.escg.route.config;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

import com.wl4g.escg.constant.EscgConstants;
import com.wl4g.escg.route.Https2HttpGlobalFilter;
import com.wl4g.escg.route.RefreshRouteApplicationListener;
import com.wl4g.escg.route.TimeBasedRouteRefresher;
//import com.wl4g.escg.route.ignore.IgnoreGatewayFilterFactory;
//import com.wl4g.escg.route.ignore.IgnoreGatewayFilterFactory.IgnoreGatewayFilter;
import com.wl4g.escg.route.repository.RedisRouteDefinitionRepository;
import com.wl4g.escg.security.config.IamSecurityAutoConfiguration;

/**
 * {@link IamSecurityAutoConfiguration}
 * 
 * @author James Wong &lt;wanglsir@gmail.com, 983708408@qq.com&gt;
 * @date 2021-10-13 v1.0.0
 * @since v1.0.0
 */
public class RouteAutoConfiguration {

    @Bean
    @ConfigurationProperties(prefix = EscgConstants.CONF_PREFIX_ESCG_ROUTE)
    public RouteProperties routeProperties() {
        return new RouteProperties();
    }

    @Bean
    public Https2HttpGlobalFilter https2HttpGlobalFilter(RouteProperties config) {
        return new Https2HttpGlobalFilter(config);
    }

    @Bean
    public RouteDefinitionRepository redisRouteDefinitionRepository() {
        return new RedisRouteDefinitionRepository();
    }

    // @Bean
    // public RouteDefinitionRepository nacosRouteDefinitionRepository() {
    // return new NacosRouteDefinitionRepository();
    // }

    @Bean
    public RefreshRouteApplicationListener refreshRouteApplicationListener() {
        return new RefreshRouteApplicationListener();
    }

    @Bean
    public TimeBasedRouteRefresher timeBasedRouteRefresher() {
        return new TimeBasedRouteRefresher();
    }

    // @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // 官方demo
                .route(p -> p.path("/get").filters(f -> f.addRequestHeader("Hello", "World")).uri("http://httpbin.org:80"))
                // 自定义
                .route(p -> p
                        // 日期时间断言
                        /*
                         * .between(ZonedDateTime.of(2020, 7, 8, 9, 0, 0, 0,
                         * ZoneId.systemDefault()), ZonedDateTime.of(2020, 7, 8,
                         * 14, 0, 0, 0, ZoneId.systemDefault())).and()
                         */
                        // .before(ZonedDateTime.of(2020,7,8,9,0,0,0,
                        // ZoneId.systemDefault()))
                        .after(ZonedDateTime.of(2020, 7, 8, 14, 0, 0, 0, ZoneId.systemDefault()))
                        .and()

                        // cookie断言-- 名称匹配且值符合正则表达式
                        .cookie("token", "[a-zA-Z]+")
                        .and()

                        // head断言
                        .header("session")
                        .and()

                        // host断言??
                        // .host("**.somehost.org").and()
                        // .host("**.wl4g.debug").and()

                        // method断言
                        // .method(HttpMethod.GET, HttpMethod.POST).and()

                        // query断言
                        .query("name")
                        .and()

                        // remote address 断言
                        .remoteAddr("10.0.0.113", "127.0.0.1", "localhost")
                        .and()

                        // 路径
                        .path("/**")
                        // .path("/gateway-example/test/hello")

                        // 过滤
                        .filters(f -> f.addRequestHeader("token", "World").addRequestParameter("age", "18").addResponseHeader(
                                "myResponseheader", "myResponseheader"))

                        // 目标
                        .uri("http://localhost:14086"))
                .build();
    }

}
