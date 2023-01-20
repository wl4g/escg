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
package com.wl4g.iam.gateway.loadbalance;

import static com.wl4g.infra.common.lang.Assert2.notNullOf;
import static com.wl4g.infra.common.log.SmartLoggerFactory.getLogger;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.equalsAnyIgnoreCase;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;
import static org.springframework.util.CollectionUtils.isEmpty;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.ReactiveLoadBalancerClientFilter;
import org.springframework.cloud.gateway.support.DelegatingServiceInstance;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.web.server.ServerWebExchange;

import com.wl4g.iam.gateway.loadbalance.config.CanaryLoadBalancerProperties;
import com.wl4g.iam.gateway.loadbalance.chooser.CanaryLoadBalancerChooser;
import com.wl4g.iam.gateway.loadbalance.chooser.CanaryLoadBalancerChooser.CanaryLoadBalancerKind;
import com.wl4g.iam.gateway.loadbalance.chooser.chain.CanaryLoadBalancerRuleChain;
import com.wl4g.iam.gateway.loadbalance.chooser.chain.DefaultCanaryLoadBalancerRuleChain;
import com.wl4g.iam.gateway.loadbalance.stats.LoadBalancerStats;
import com.wl4g.iam.gateway.loadbalance.stats.LoadBalancerStats.ServiceInstanceStatus;
import com.wl4g.infra.common.log.SmartLogger;
import com.wl4g.infra.common.framework.operator.GenericOperatorAdapter;

import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * Grayscale Load-Balancer filter. </br>
 * </br>
 * 
 * Note: The retry filter should be executed before the load balancing filter,
 * so that other back-end servers can be selected when retrying. see to:
 * {@link org.springframework.cloud.gateway.handler.FilteringWebHandler.DefaultGatewayFilterChain#filter(ServerWebExchange)}
 * 
 * @author James Wong &lt;wanglsir@gmail.com, 983708408@qq.com&gt;
 * @date 2022-04-03 v1.0.0
 * @since v1.0.0
 */
public class CanaryLoadBalancerClientFilter extends ReactiveLoadBalancerClientFilter {

    private final SmartLogger log = getLogger(getClass());
    private final LoadBalancerProperties loadBalancerConfig;
    private final GenericOperatorAdapter<CanaryLoadBalancerRule.CanaryLoadBalancerKind, CanaryLoadBalancerRule> ruleAdapter;
    private final LoadBalancerStats loadBalancerStats;

    public CanaryLoadBalancerClientFilter(LoadBalancerClientFactory clientFactory, LoadBalancerProperties loadBalancerConfig,
            GenericOperatorAdapter<CanaryLoadBalancerRule.CanaryLoadBalancerKind, CanaryLoadBalancerRule> ruleAdapter,
            LoadBalancerStats loadBalancerStats) {
        super(clientFactory, loadBalancerConfig);
        this.loadBalancerConfig = notNullOf(loadBalancerConfig, "loadBalancerConfig");
        this.ruleAdapter = notNullOf(ruleAdapter, "ruleAdapter");
        this.loadBalancerStats = notNullOf(loadBalancerStats, "loadBalancerStats");
    }

    /**
     * Note: The retry filter should be executed before the load balancing
     * filter, so that other back-end servers can be selected when retrying.
     * see:
     * {@link org.springframework.cloud.gateway.handler.FilteringWebHandler#loadFilters()}
     * and
     * {@link org.springframework.cloud.gateway.handler.FilteringWebHandler#handle(ServerWebExchange)}
     * and
     * {@link org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter#order}
     * and
     * {@link org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory}
     */
    @Override
    public int getOrder() {
        return super.getOrder();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!loadBalancerConfig.isEnabled()) {
            return chain.filter(exchange);
        }

        URI requestUri = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
        String schemePrefix = exchange.getAttribute(GATEWAY_SCHEME_PREFIX_ATTR);

        // Ignore the URi prefix If it does not start with LB, go to the
        // next filter.
        if (isNull(requestUri) || (!equalsAnyIgnoreCase("LB", requestUri.getScheme(), schemePrefix))) {
            return chain.filter(exchange);
        }

        // According to the original URL of the gateway. Replace the URI of
        // http://IP:PORT/path
        addOriginalRequestUrl(exchange, requestUri);
        if (log.isTraceEnabled()) {
            log.trace(ReactiveLoadBalancerClientFilter.class.getSimpleName() + " url before: " + requestUri);
        }

        Response<ServiceInstance> response = choose(exchange);
        if (!response.hasServer()) {
            throw NotFoundException.create(loadBalancerConfig.isUse404(), "Unable to find instance for " + requestUri.getHost());
        }
        URI uri = exchange.getRequest().getURI();

        // if the `lb:<scheme>` mechanism was used, use `<scheme>` as the
        // default, if the loadbalancer doesn't provide one.
        String overrideScheme = !isBlank(schemePrefix) ? requestUri.getScheme() : null;
        DelegatingServiceInstance instance = new DelegatingServiceInstance(response.getServer(), overrideScheme);

        URI newRequestUri = reconstructURI(instance, uri);

        if (log.isTraceEnabled()) {
            log.trace("LoadBalancerClientFilter url chosen: {}", newRequestUri);
        }
        exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, newRequestUri);

        return chain.filter(exchange).doOnRequest(v -> {
            loadBalancerStats.connect(instance, 1);
        }).doFinally(signal -> {
            if (signal == SignalType.ON_COMPLETE || signal == SignalType.CANCEL || signal == SignalType.ON_ERROR) {
                loadBalancerStats.disconnect(instance, 1);
            }
        });
    }

    private Response<ServiceInstance> choose(ServerWebExchange exchange) {
        URI uri = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
        String serviceId = uri.getHost();

        // TODO use dynamic LB type.

        List<CanaryLoadBalancerRule> rules = new ArrayList<>();
        // TODO for testing
        rules.add(ruleAdapter.forOperator(CanaryLoadBalancerKind.R));
        CanaryLoadBalancerRuleChain chain = new DefaultCanaryLoadBalancerRuleChain(rules);

        List<ServiceInstanceStatus> chosen = chain.choose(loadBalancerStats, serviceId, exchange.getRequest(), null, chain);
        if (isEmpty(chosen)) {
            return new EmptyResponse();
        }

        // After the rule chain has been processed, there should usually be only
        // 1 or a few available instances left.
        return new DefaultResponse(chosen.get(0).getInstance());
    }

}