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
package com.wl4g.escg.requestlimit;

import static java.lang.String.format;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wl4g.infra.common.lang.FastTimeClock;

/**
 * {@link TestsLimiterController}
 * 
 * @author James Wong &lt;wanglsir@gmail.com, 983708408@qq.com&gt;
 * @date 2021-10-12 v1.0.0
 * @since v1.0.0
 */
@RestController
@RequestMapping("/test/ratelimit")
public class TestsLimiterController {

    @GetMapping("/get")
    public String get(@RequestParam("name") String name) {
        StringBuilder buf = new StringBuilder();
        buf.append(format("System Time:", FastTimeClock.currentTimeMillis()));
        buf.append("<br/>");
        buf.append(format("Ratelimit keyResolver: %s", ""));
        buf.append("<br/>");
        buf.append(format("Configuration replenishRate: %s, burstCapacity: %s", "", ""));
        buf.append("<br/>");
        return buf.toString();
    }

}
