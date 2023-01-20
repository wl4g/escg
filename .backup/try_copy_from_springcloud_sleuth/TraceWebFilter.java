/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wl4g.iam.gateway.trace;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoOperator;
import reactor.util.annotation.Nullable;
import reactor.util.context.Context;

import org.springframework.beans.BeansException;

import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.docs.AssertingSpan;
import org.springframework.cloud.sleuth.http.HttpServerHandler;
import org.springframework.cloud.sleuth.http.HttpServerRequest;
import org.springframework.cloud.sleuth.http.HttpServerResponse;
import org.springframework.cloud.sleuth.instrument.reactor.ReactorSleuth;
import org.springframework.cloud.sleuth.instrument.reactor.TraceContextPropagator;
import org.springframework.cloud.sleuth.instrument.web.SpanFromContextRetriever;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

/**
 * A {@link WebFilter} that creates / continues / closes and detaches spans for
 * a reactive web application.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
public class TraceWebFilter implements WebFilter, Ordered, ApplicationContextAware {

    // Remember that this can be used in other packages
    protected static final String TRACE_REQUEST_ATTR = Span.class.getName();

    private static final Log log = LogFactory.getLog(TraceWebFilter.class);

    private static final String TRACE_SPAN_WITHOUT_PARENT = TraceWebFilter.class.getName() + ".SPAN_WITH_NO_PARENT";

    private final Tracer tracer;

    private final HttpServerHandler handler;

    private CurrentTraceContext currentTraceContext;

    private ApplicationContext applicationContext;

    private int order;

    private SpanFromContextRetriever spanFromContextRetriever;

    @Deprecated
    public TraceWebFilter(Tracer tracer, HttpServerHandler handler) {
        this.tracer = tracer;
        this.handler = handler;
        this.currentTraceContext = null;
    }

    public TraceWebFilter(Tracer tracer, HttpServerHandler handler, CurrentTraceContext currentTraceContext) {
        this.tracer = tracer;
        this.handler = handler;
        this.currentTraceContext = currentTraceContext;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String uri = exchange.getRequest().getPath().pathWithinApplication().value();
        Mono<Void> source = chain.filter(exchange);
        boolean tracePresent = isTracePresent();
        if (log.isDebugEnabled()) {
            log.debug("Received a request to uri [" + uri + "]");
        }
        return new MonoWebFilterTrace(source, exchange, tracePresent, this, spanFromContextRetriever());
    }

    private boolean isTracePresent() {
        boolean tracePresent = this.tracer.currentSpan() != null;
        if (tracePresent) {
            // clear any previous trace
            this.tracer.withSpan(null); // TODO: dangerous and also allocates
                                        // stuff
        }
        return tracePresent;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private CurrentTraceContext currentTraceContext() {
        if (this.currentTraceContext == null) {
            this.currentTraceContext = this.applicationContext.getBean(CurrentTraceContext.class);
        }
        return this.currentTraceContext;
    }

    private SpanFromContextRetriever spanFromContextRetriever() {
        if (this.spanFromContextRetriever == null) {
            this.spanFromContextRetriever = this.applicationContext.getBeanProvider(SpanFromContextRetriever.class)
                    .getIfAvailable(() -> new SpanFromContextRetriever() {
                    });
        }
        return this.spanFromContextRetriever;
    }

    private static class MonoWebFilterTrace extends MonoOperator<Void, Void> implements TraceContextPropagator {

        final ServerWebExchange exchange;

        final Tracer tracer;

        final Span span;

        final HttpServerHandler handler;

        final AtomicBoolean initialSpanAlreadyRemoved = new AtomicBoolean();

        final boolean initialTracePresent;

        final CurrentTraceContext currentTraceContext;

        final SpanFromContextRetriever spanFromContextRetriever;

        MonoWebFilterTrace(Mono<? extends Void> source, ServerWebExchange exchange, boolean initialTracePresent,
                TraceWebFilter parent, SpanFromContextRetriever spanFromContextRetriever) {
            super(source);
            this.tracer = parent.tracer;
            this.handler = parent.handler;
            this.currentTraceContext = parent.currentTraceContext();
            this.exchange = exchange;
            this.span = exchange.getAttribute(TRACE_REQUEST_ATTR);
            this.initialTracePresent = initialTracePresent;
            this.spanFromContextRetriever = spanFromContextRetriever;
        }

        @Override
        public void subscribe(CoreSubscriber<? super Void> subscriber) {
            Context context = contextWithoutInitialSpan(subscriber.currentContext());
            Span span = findOrCreateSpan(context);
            try (CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(span.context())) {
                this.source.subscribe(new WebFilterTraceSubscriber(subscriber, context, span, this));
            }
        }

        @Override
        public Object scanUnsafe(Attr key) {
            if (key == Attr.RUN_STYLE) {
                return Attr.RunStyle.SYNC;
            }
            return super.scanUnsafe(key);
        }

        private Context contextWithoutInitialSpan(Context context) {
            if (this.initialTracePresent && !this.initialSpanAlreadyRemoved.get()) {
                context = context.delete(Span.class);
                this.initialSpanAlreadyRemoved.set(true);
            }
            return context;
        }

        private Span findOrCreateSpan(Context c) {
            Span span;
            AssertingSpan assertingSpan = null;
            if (c.hasKey(Span.class)) {
                Span parent = c.get(Span.class);
                try (Tracer.SpanInScope spanInScope = this.tracer.withSpan(parent)) {
                    span = this.tracer.nextSpan();
                }
                if (log.isDebugEnabled()) {
                    log.debug("Found span in reactor context" + span);
                }
            } else {
                if (this.span != null) {
                    try (Tracer.SpanInScope spanInScope = this.tracer.withSpan(this.span)) {
                        span = this.tracer.nextSpan();
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("Found span in attribute " + span);
                    }
                }
                span = this.spanFromContextRetriever.findSpan(c);
                if (this.span == null && span == null) {
                    span = this.handler.handleReceive(new WrappedRequest(this.exchange.getRequest()));
                    if (log.isDebugEnabled()) {
                        log.debug("Handled receive of span " + span);
                    }
                } else if (log.isDebugEnabled()) {
                    log.debug("Found tracer specific span in reactor context [" + span + "]");
                }
                assertingSpan = SleuthWebSpan.WEB_FILTER_SPAN.wrap(span);
                this.exchange.getAttributes().put(TRACE_REQUEST_ATTR, assertingSpan);
            }
            if (assertingSpan == null) {
                assertingSpan = SleuthWebSpan.WEB_FILTER_SPAN.wrap(span);
            }
            return assertingSpan;
        }

        static final class WebFilterTraceSubscriber implements CoreSubscriber<Void> {

            final CoreSubscriber<? super Void> actual;

            final Context context;

            final Span span;

            final ServerWebExchange exchange;

            final HttpServerHandler handler;

            WebFilterTraceSubscriber(CoreSubscriber<? super Void> actual, Context context, Span span, MonoWebFilterTrace parent) {
                this.actual = actual;
                this.span = span;
                this.context = ReactorSleuth.wrapContext(context.put(TraceContext.class, span.context()));
                this.exchange = parent.exchange;
                this.handler = parent.handler;
            }

            @Override
            public void onSubscribe(Subscription subscription) {
                this.actual.onSubscribe(subscription);
            }

            @Override
            public void onNext(Void aVoid) {
                // IGNORE
            }

            @Override
            public void onError(Throwable t) {
                terminateSpan(t);
                this.actual.onError(t);
            }

            @Override
            public void onComplete() {
                terminateSpan(null);
                this.actual.onComplete();
            }

            @Override
            public Context currentContext() {
                return this.context;
            }

            private void terminateSpan(@Nullable Throwable t) {
                Object attribute = this.exchange.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
                addClassMethodTag(attribute, this.span);
                addClassNameTag(attribute, this.span);
                Object pattern = this.exchange.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                String httpRoute = pattern != null ? pattern.toString() : "";
                addResponseTagsForSpanWithoutParent(this.exchange, this.exchange.getResponse(), this.span);
                WrappedResponse response = new WrappedResponse(this.exchange.getResponse(),
                        this.exchange.getRequest().getMethodValue(), httpRoute, t);
                this.handler.handleSend(response, this.span);
                if (log.isDebugEnabled()) {
                    log.debug("Handled send of " + this.span);
                }
            }

            private void addClassMethodTag(Object handler, Span span) {
                if (handler instanceof HandlerMethod) {
                    String methodName = ((HandlerMethod) handler).getMethod().getName();
                    SleuthWebSpan.WEB_FILTER_SPAN.wrap(span).tag(SleuthWebSpan.Tags.METHOD, methodName);
                    if (log.isDebugEnabled()) {
                        log.debug("Adding a method tag with value [" + methodName + "] to a span " + span);
                    }
                }
            }

            private void addClassNameTag(Object handler, Span span) {
                if (handler == null) {
                    return;
                }
                String className;
                if (handler instanceof HandlerMethod) {
                    className = ((HandlerMethod) handler).getBeanType().getSimpleName();
                } else {
                    className = handler.getClass().getSimpleName();
                }
                if (log.isDebugEnabled()) {
                    log.debug("Adding a class tag with value [" + className + "] to a span " + span);
                }
                SleuthWebSpan.WEB_FILTER_SPAN.wrap(span).tag(SleuthWebSpan.Tags.CLASS, className);
            }

            private void addResponseTagsForSpanWithoutParent(ServerWebExchange exchange, ServerHttpResponse response, Span span) {
                if (spanWithoutParent(exchange) && response.getStatusCode() != null && span != null) {
                    SleuthWebSpan.WEB_FILTER_SPAN.wrap(span).tag(SleuthWebSpan.Tags.CLASS,
                            String.valueOf(response.getStatusCode().value()));
                }
            }

            private boolean spanWithoutParent(ServerWebExchange exchange) {
                return exchange.getAttribute(TRACE_SPAN_WITHOUT_PARENT) != null;
            }

        }

    }

    static final class WrappedRequest implements HttpServerRequest {

        final ServerHttpRequest delegate;

        WrappedRequest(ServerHttpRequest delegate) {
            this.delegate = delegate;
        }

        @Override
        public Collection<String> headerNames() {
            return this.delegate.getHeaders().keySet();
        }

        @Override
        public ServerHttpRequest unwrap() {
            return delegate;
        }

        @Override
        public String method() {
            return delegate.getMethodValue();
        }

        @Override
        public String path() {
            return delegate.getPath().toString();
        }

        @Override
        public String url() {
            return delegate.getURI().toString();
        }

        @Override
        public String header(String name) {
            return delegate.getHeaders().getFirst(name);
        }

    }

    static final class WrappedResponse implements HttpServerResponse {

        final ServerHttpResponse delegate;

        final String method;

        final String httpRoute;

        final Throwable throwable;

        WrappedResponse(ServerHttpResponse resp, String method, String httpRoute, Throwable throwable) {
            this.delegate = resp;
            this.method = method;
            this.httpRoute = httpRoute;
            this.throwable = throwable;
        }

        @Override
        public String method() {
            return method;
        }

        @Override
        public String route() {
            return httpRoute;
        }

        @Override
        public ServerHttpResponse unwrap() {
            return delegate;
        }

        @Override
        public int statusCode() {
            if (!this.delegate.isCommitted() && this.throwable != null) {
                if (this.throwable instanceof ResponseStatusException) {
                    return ((ResponseStatusException) this.throwable).getRawStatusCode();
                }
                return HttpStatus.INTERNAL_SERVER_ERROR.value();
            }
            HttpStatus statusCode = this.delegate.getStatusCode();
            return statusCode != null ? statusCode.value() : 0;
        }

        @Override
        public Collection<String> headerNames() {
            // TODO: As with the status code, these headers get rewritten later
            return this.delegate.getHeaders().keySet();
        }

        @Override
        public Throwable error() {
            return this.throwable;
        }

    }

}
