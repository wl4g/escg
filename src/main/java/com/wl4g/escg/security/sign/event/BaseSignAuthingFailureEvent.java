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
package com.wl4g.escg.security.sign.event;

import org.springframework.context.ApplicationEvent;

import com.wl4g.escg.security.sign.SimpleSignAuthingFilterFactory.AppIdExtractor;
import com.wl4g.escg.security.sign.SimpleSignAuthingFilterFactory.SignAlgorithm;
import com.wl4g.escg.security.sign.SimpleSignAuthingFilterFactory.SignHashingMode;

import lombok.Getter;

/**
 * {@link BaseSignAuthingFailureEvent}
 * 
 * @author James Wong &lt;wanglsir@gmail.com, 983708408@qq.com&gt;
 * @date 2022-04-18 v1.0.0
 * @since v1.0.0
 */
@Getter
public class BaseSignAuthingFailureEvent extends ApplicationEvent {
    private static final long serialVersionUID = -7291654693102770442L;

    private final AppIdExtractor extractor;
    private final SignAlgorithm algorithm;
    private final SignHashingMode mode;
    private final String routeId;
    private final String requsetPath;

    public BaseSignAuthingFailureEvent(String appId, AppIdExtractor extractor, SignAlgorithm algorithm, SignHashingMode mode,
            String routeId, String requsetPath) {
        super(appId);
        this.extractor = extractor;
        this.algorithm = algorithm;
        this.mode = mode;
        this.routeId = routeId;
        this.requsetPath = requsetPath;
    }

}
