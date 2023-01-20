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
package com.wl4g.escg.constant;

import com.wl4g.infra.common.lang.EnvironmentUtil;

/**
 * IAM for gateway constants.
 * 
 * @author James Wong<jamewong1376@gmail.com>
 * @version v1.0
 * @date 2018年11月13日
 * @since
 */
public abstract class EscgConstants extends EnvironmentUtil {

    public static final String CONF_PREFIX_ESCG = "spring.escg";

    //
    // (Static) configuration properties prefix definitions.
    //

    public static final String CONF_PREFIX_ESCG_SERVER = CONF_PREFIX_ESCG + ".server";
    public static final String CONF_PREFIX_ESCG_IPFILTER = CONF_PREFIX_ESCG + ".ipfilter";
    public static final String CONF_PREFIX_ESCG_REQUESTSIZE = CONF_PREFIX_ESCG + ".requestsize";
    public static final String CONF_PREFIX_ESCG_FAULT = CONF_PREFIX_ESCG + ".fault";
    public static final String CONF_PREFIX_ESCG_SECURITY = CONF_PREFIX_ESCG + ".security";
    public static final String CONF_PREFIX_ESCG_TRACE = CONF_PREFIX_ESCG + ".trace";
    public static final String CONF_PREFIX_ESCG_LOGGING = CONF_PREFIX_ESCG + ".logging";
    public static final String CONF_PREFIX_ESCG_REQUESTLIMIT = CONF_PREFIX_ESCG + ".requestlimit";
    public static final String CONF_PREFIX_ESCG_ROUTE = CONF_PREFIX_ESCG + ".route";
    public static final String CONF_PREFIX_ESCG_RETRY = CONF_PREFIX_ESCG + ".retry";
    public static final String CONF_PREFIX_ESCG_CIRCUITBREAKER = CONF_PREFIX_ESCG + ".circuitbreaker";
    public static final String CONF_PREFIX_ESCG_LOADBANANER = CONF_PREFIX_ESCG + ".loadbalancer";
    public static final String CONF_PREFIX_ESCG_RESPONSECACHE = CONF_PREFIX_ESCG + ".responsecache";
    public static final String CONF_PREFIX_ESCG_TRAFFIC = CONF_PREFIX_ESCG + ".traffic";

    //
    // (Dynamic) configuration cache prefix definitions.
    //

    public static final String CACHE_PREFIX_ESCG = "escg";

    public static final String CACHE_PREFIX_ESCG_IPFILTER = CACHE_PREFIX_ESCG + ":ipfilter";

    public static final String CACHE_PREFIX_ESCG_ROUTES = CACHE_PREFIX_ESCG + ":routes";

    public static final String CACHE_PREFIX_ESCG_AUTH = CACHE_PREFIX_ESCG + ":auth";
    public static final String CACHE_PREFIX_ESCG_AUTH_SIGN_SECRET = CACHE_PREFIX_ESCG_AUTH + ":sign:secret";
    public static final String CACHE_PREFIX_ESCG_AUTH_SIGN_REPLAY_BLOOM = CACHE_PREFIX_ESCG_AUTH + ":sign:replay:bloom";
    public static final String CACHE_PREFIX_ESCG_AUTH_SIGN_EVENT_SUCCESS = CACHE_PREFIX_ESCG_AUTH + ":sign:event:success";
    public static final String CACHE_PREFIX_ESCG_AUTH_SIGN_EVENT_FAILURE = CACHE_PREFIX_ESCG_AUTH + ":sign:event:failure";

    public static final String CACHE_PREFIX_ESCG_REQUESTLIMIT = CACHE_PREFIX_ESCG + ":requestlimit";
    public static final String CACHE_PREFIX_ESCG_REQUESTLIMIT_CONF_RATE = CACHE_PREFIX_ESCG_REQUESTLIMIT + ":config:rate";
    public static final String CACHE_PREFIX_ESCG_REQUESTLIMIT_CONF_QUOTA = CACHE_PREFIX_ESCG_REQUESTLIMIT + ":config:quota";
    public static final String CACHE_PREFIX_ESCG_REQUESTLIMIT_TOKEN_RATE = CACHE_PREFIX_ESCG_REQUESTLIMIT + ":token:rate";
    public static final String CACHE_PREFIX_ESCG_REQUESTLIMIT_TOKEN_QUOTA = CACHE_PREFIX_ESCG_REQUESTLIMIT + ":token:quota";
    public static final String CACHE_PREFIX_ESCG_REQUESTLIMIT_EVENT_HITS_RATE = CACHE_PREFIX_ESCG_REQUESTLIMIT
            + ":event:hits:rate";
    public static final String CACHE_PREFIX_ESCG_REQUESTLIMIT_EVENT_HITS_QUOTA = CACHE_PREFIX_ESCG_REQUESTLIMIT
            + ":event:hits:quota";

    public static final String CACHE_SUFFIX_IAM_GATEWAY_RESPONSECACHE = CACHE_PREFIX_ESCG + ":responsecache:data";

    public static final String CACHE_SUFFIX_IAM_GATEWAY_EVENT_YYMMDD = "yyMMdd";

}