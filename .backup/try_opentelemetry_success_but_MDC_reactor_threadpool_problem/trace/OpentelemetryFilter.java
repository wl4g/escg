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
package com.wl4g.iam.gateway.trace;

import static com.wl4g.infra.common.lang.Assert2.notNullOf;
import static com.wl4g.infra.core.constant.CoreInfraConstants.TRACE_REQUEST_ID_HEADER;
import static com.wl4g.infra.core.constant.CoreInfraConstants.TRACE_REQUEST_SEQ_HEADER;
import static java.util.Objects.isNull;

import java.net.URI;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.wl4g.iam.gateway.trace.config.GrayTraceProperties;
import com.wl4g.iam.gateway.util.IamGatewayUtil;
import com.wl4g.iam.gateway.util.IamGatewayUtil.SafeFilterOrdered;
import com.wl4g.infra.context.utils.web.ReactiveRequestExtractor;
import com.wl4g.infra.context.web.matcher.SpelRequestMatcher;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * {@link OpentelemetryGlobalFilter}
 * 
 * The {@link org.springframework.web.server.WebFilter} interface is implemented
 * here instead of the GlobalFilter interface, because it needs to correspond to
 * {@link com.wl4g.infra.context.logging.trace.reactive.SimpleTraceMDCWebFilter}.
 * 
 * {@link org.springframework.web.server.WebFilter} is executed first, followed
 * by {@link org.springframework.cloud.gateway.filter.GlobalFilter},
 * {@link org.springframework.cloud.gateway.filter.GatewayFilter}
 * 
 * @author James Wong &lt;wanglsir@gmail.com, 983708408@qq.com&gt;
 * @date 2021-09-02 v1.0.0
 * @since v1.0.0
 * @see {@link org.springframework.cloud.sleuth.instrument.web.TraceWebFilter}
 *      from spring-cloud-sleuth-instrumentation-3.1.1.jar
 */
@Slf4j
public class OpentelemetryFilter implements WebFilter, Ordered {

    private final TraceProperties traceConfig;
    private final OpenTelemetry openTelemetry;
    private final SpelRequestMatcher requestMatcher;

    public OpentelemetryFilter(TraceProperties traceConfig, OpenTelemetry openTelemetry) {
        this.traceConfig = notNullOf(traceConfig, "traceConfig");
        this.openTelemetry = notNullOf(openTelemetry, "openTelemetry");
        // Build gray request matcher.
        this.requestMatcher = new SpelRequestMatcher(traceConfig.getPreferMatchRuleDefinitions());
    }

    /**
     * @see {@link org.springframework.cloud.gateway.handler.FilteringWebHandler#loadFilters()}
     */
    @Override
    public int getOrder() {
        return SafeFilterOrdered.ORDER_TRACE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!isTraceRequest(exchange)) {
            if (log.isDebugEnabled()) {
                ServerHttpRequest request = exchange.getRequest();
                log.debug("Not to meet the conditional rule to enable tracing. - uri: {}, headers: {}, queryParams: {}",
                        request.getURI(), request.getHeaders(), request.getQueryParams());
            }
            return chain.filter(exchange);
        }

        Tracer tracer = openTelemetry.getTracer(traceConfig.getServiceName());
        return buildTraceSpan(tracer, exchange).flatMap(span -> {
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();
            String traceId = span.getSpanContext().getTraceId();
            span.setAttribute(TRACE_TAG_PARAMETERS, request.getQueryParams().toString());

            // Sets the traces information in the request response header,
            // corresponding to the common infrastructure frameworks.
            request.mutate()
                    .header(TRACE_REQUEST_ID_HEADER, traceId)
                    .header(TRACE_REQUEST_SEQ_HEADER, span.getSpanContext().getSpanId())
                    .build();
            HttpHeaders responseHeaders = response.getHeaders();
            responseHeaders.add(TRACE_REQUEST_ID_HEADER, traceId);

            Scope scope = span.makeCurrent();
            inject(exchange);
            return chain.filter(exchange).doOnError(span::recordException).doFinally(signal -> {
                scope.close();
                span.end();
            });
        });
    }

    /**
     * Check if enable tracking needs to be filtered.
     * 
     * @param exchange
     * @return
     */
    protected boolean isTraceRequest(ServerWebExchange exchange) {
        if (!traceConfig.isEnabled()) {
            return false;
        }
        return requestMatcher.matches(new ReactiveRequestExtractor(exchange.getRequest()),
                traceConfig.getPreferOpenMatchExpression());
    }

    protected void inject(ServerWebExchange exchange) {
        HttpHeaders traceHeaders = new HttpHeaders();

        TextMapPropagator propagator = openTelemetry.getPropagators().getTextMapPropagator();
        propagator.inject(Context.current(), traceHeaders, HttpHeaders::add);

        exchange.getRequest().mutate().headers(headers -> headers.addAll(traceHeaders)).build();
    }

    public static Mono<Span> buildTraceSpan(Tracer tracer, ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        return exchange.getPrincipal().defaultIfEmpty(IamGatewayUtil.UNKNOWN_PRINCIPAL).flatMap(principal -> {
            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            String routeId = isNull(route) ? "Unknown" : route.getId();
            URI uri = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
            String path = isNull(uri) ? "Unknown" : uri.getPath();

            Span span = tracer.spanBuilder(path)
                    .setNoParent()
                    // .setSpanKind(SpanKind.SERVER)
                    .setSpanKind(SpanKind.CLIENT)
                    .setAttribute(TRACE_TAG_ROUTEID, routeId)
                    .setAttribute(TRACE_TAG_PRINCIPAL, principal.getName())
                    .setAttribute(TRACE_TAG_PARAMETERS, request.getQueryParams().toString())
                    .setAttribute(SemanticAttributes.HTTP_METHOD, request.getMethod().name())
                    .startSpan();
            return Mono.just(span);
        });
    }

    public static final String VAR_ROUTE_ID = "routeId";
    public static final String TRACE_TAG_ROUTEID = "routeId";
    public static final String TRACE_TAG_PRINCIPAL = "principal";
    public static final String TRACE_TAG_PARAMETERS = "parameters";

}
