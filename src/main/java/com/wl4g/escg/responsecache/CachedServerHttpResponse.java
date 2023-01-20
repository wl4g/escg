/*
 * Copyright 2002-2020 the original author or authors.
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

package com.wl4g.escg.responsecache;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;

/**
 * {@link CachedServerHttpResponse}
 * 
 * @author James Wong &lt;wanglsir@gmail.com, 983708408@qq.com&gt;
 * @date 2022-05-13 v1.0.0
 * @since v1.0.0
 * @see {@link org.springframework.mock.http.server.reactive.MockServerHttpResponse}
 */
public class CachedServerHttpResponse extends AbstractServerHttpResponse {

    private Flux<DataBuffer> body = Flux
            .error(new IllegalStateException("No content was written nor was setComplete() called on this response."));

    private Function<Flux<DataBuffer>, Mono<Void>> writeHandler;

    public CachedServerHttpResponse(HttpHeaders headers) {
        this(new DefaultDataBufferFactory(), headers);
    }

    public CachedServerHttpResponse(DataBufferFactory dataBufferFactory, HttpHeaders headers) {
        super(dataBufferFactory, headers);
        this.writeHandler = body -> {
            // Avoid .then() that causes data buffers to be discarded and
            // released
            Sinks.Empty<Void> completion = Sinks.unsafe().empty();
            this.body = body.cache();
            this.body.subscribe(aVoid -> {
                // Signals are serialized
            }, completion::tryEmitError, completion::tryEmitEmpty);
            return completion.asMono();
        };
    }

    /**
     * Configure a custom handler to consume the response body.
     * <p>
     * By default, response body content is consumed in full and cached for
     * subsequent access in tests. Use this option to take control over how the
     * response body is consumed.
     * 
     * @param writeHandler
     *            the write handler to use returning {@code Mono<Void>} when the
     *            body has been "written" (i.e. consumed).
     */
    public void setWriteHandler(Function<Flux<DataBuffer>, Mono<Void>> writeHandler) {
        Assert.notNull(writeHandler, "'writeHandler' is required");
        this.body = Flux.error(new IllegalStateException("Not available with custom write handler."));
        this.writeHandler = writeHandler;
    }

    @Override
    public <T> T getNativeResponse() {
        throw new IllegalStateException("This is a mock. No running server, no native response.");
    }

    @Override
    protected void applyStatusCode() {
    }

    @Override
    protected void applyHeaders() {
    }

    @Override
    protected void applyCookies() {
        for (List<ResponseCookie> cookies : getCookies().values()) {
            for (ResponseCookie cookie : cookies) {
                getHeaders().add(HttpHeaders.SET_COOKIE, cookie.toString());
            }
        }
    }

    @Override
    protected Mono<Void> writeWithInternal(Publisher<? extends DataBuffer> body) {
        return this.writeHandler.apply(Flux.from(body));
    }

    @Override
    protected Mono<Void> writeAndFlushWithInternal(Publisher<? extends Publisher<? extends DataBuffer>> body) {

        return this.writeHandler.apply(Flux.from(body).concatMap(Flux::from));
    }

    @Override
    public Mono<Void> setComplete() {
        return doCommit(() -> Mono.defer(() -> this.writeHandler.apply(Flux.empty())));
    }

    /**
     * Return the response body or an error stream if the body was not set.
     */
    public Flux<DataBuffer> getBody() {
        return this.body;
    }

    /**
     * Aggregate response data and convert to a String using the "Content-Type"
     * charset or "UTF-8" by default.
     */
    public Mono<String> getBodyAsString() {

        Charset charset = Optional.ofNullable(getHeaders().getContentType()).map(MimeType::getCharset).orElse(
                StandardCharsets.UTF_8);

        return DataBufferUtils.join(getBody()).map(buffer -> {
            String s = buffer.toString(charset);
            DataBufferUtils.release(buffer);
            return s;
        }).defaultIfEmpty("");
    }

}
