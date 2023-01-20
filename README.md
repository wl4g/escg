# ESCG (Enhanced Spring Cloud Gateway)

A enterprise-level enhanced gateway based on spring cloud gateway, out of the box, supports such as canary request-based response cache filter, canary load balancer, universal signature authentication filter, oidc v1/oauth2.x authentication filter, ip filter, Traffic replication filter, quota-request limiter-based filter Injector, canary-based fault injector filter, and canary-based humanized log filter, etc.

Tips: The latest version and documentation are currently being sorted out. It is recommended to deploy in the test environment first. If you have any questions or suggestions, please submit an Issue

## 1. Developer guide

- Building

```bash
git clone https://github.com/wl4g/escg.git
cd escg/

# The profiles supports: -Pbuild:tar, -Pbuild:springjar, -Pbuild:docker, -Pbuild:native
mvn -U install -DskipTests -Pbuild:tar
```

## 2. Examples

### 2.1 Generating self-certificate

```bash
cd src/main/resources/cert.d/
./cert-tool.sh ca
./cert-tool.sh pem-p12 ca.pem ca-key.pem

./cert-tool.sh cert wl4g.io 192.168.88.2,10.88.8.5
./cert-tool.sh pem-p12 wl4g.io.pem wl4g.io-key.pem

./cert-tool.sh cert client1 192.168.88.2,10.88.8.5
./cert-tool.sh pem-p12 client1.pem client1-key.pem
```

- Note: To create a certificate, you must specify a list of hosts, otherwise, for example, an error will be
reported when executing `curl`: `no alternative certificate subject name matches target host name '192.168.88.3'`,
Using the above script tool will contain `localhost,127.0.0.1` by default.

### 2.2 Ingress TLS

- Preconditions (startup configuration)

  - [docs.oracle.com/javase/7/docs/technotes/guides/security/jsse/ReadDebug.html](https://docs.oracle.com/javase/7/docs/technotes/guides/security/jsse/ReadDebug.html) , The `-Djavax.net.debug` options are `all|ssl|handshake|warning|...`

```bash
java -Djavax.net.debug=all -jar escg-1.0.0-bin.jar --server.ssl.enabled=true --server.ssl.client-auth=NONE
```

- Clients for `curl` testing

```bash
curl -vsSkL -XGET \
-H 'X-Escg-Trace: y' \
-H 'X-Escg-Log: y' \
-H 'X-Escg-Log-Level: 10' \
--cacert ca.pem \
--cert client1.pem \
--key client1-key.pem \
'https://localhost:18085/_fallback' | jq
```

### 2.3 Ingress mTLS

- Preconditions (startup configuration)

```bash
java -Djavax.net.debug=all -jar escg-1.0.0-bin.jar --server.ssl.enabled=true --server.ssl.client-auth=NEED
```

- Clients for `curl` testing

```bash
curl -vsSkL -XGET \
-H 'X-Escg-Trace: y' \
-H 'X-Escg-Log: y' \
-H 'X-Escg-Log-Level: 10' \
--cacert ca.pem \
--cert client1.pem \
--key client1-key.pem \
'https://localhost:18085/_fallback' | jq
```

### 2.4 IP Filter

- Preconditions(`src/test/resources/bootstrap.yml`)

```yaml
spring:
  profiles:
    include: "service-discovery,route-filter-splitting"
```

```bash
export localIp=$(ifconfig|grep -A 4 -E '^em*|^eno*|^enp*|^ens*|^eth*|^wlp*'|grep 'inet'|awk '{print $2}'|head -1 2>/dev/null)

# for testing positive example
curl -vsSkL -XGET \
-H 'X-Escg-Trace: y' \
-H 'X-Escg-Log: y' \
-H 'X-Escg-Log-Level: 10' \
"http://$localIp:18085/productpage-with-IpFilter/get"

# for testing negative example
curl -vsSkL -XPOST \
-H 'X-Escg-Trace: y' \
-H 'X-Escg-Log: y' \
-H 'X-Escg-Log-Level: 10' \
-H 'Content-Type: application/json' \
-H 'X-Forwarded-For: 1.1.1.2' \
-d '{"name":"jack"}' \
"http://$localIp:18085/productpage-with-IpFilter/post"
```

### 2.5 Request Size

TODO

### 2.6 Fault Injection

- The following example requires startup corresponding configuration file: `src/test/resources/bootstrap.yml`

```yaml
spring:
  profiles:
    include: "service-discovery,route-filter-splitting"
```

```bash
## for testing positive example1
curl -vsSkL -XGET \
-H 'X-Escg-Trace: y' \
-H 'X-Escg-Log: y' \
-H 'X-Escg-Log-Level: 10' \
-H 'X-Escg-Fault: y' \
'http://localhost:18085/productpage-with-FaultInjector/get'

## for testing positive example2
curl -vsSkL -XPOST \
-H 'Content-Type: application/json' \
-H 'X-Escg-Trace: y' \
-H 'X-Escg-Log: y' \
-H 'X-Escg-Log-Level: 10' \
-H 'X-Escg-Fault: y' \
-d '{"name":"jack"}' \
'http://localhost:18085/productpage-with-FaultInjector/post'
```

### 2.7 Simple Sign Authing

- Preconditions1 (startup configuration, `src/test/resources/example/example-route-splitting.yml` or `src/test/resources/example/example-route-composite.yml`)

```yaml
...
filters:
  - RewritePath=/productpage-with-SimpleSignAuthing/(?<segment>.*),/$\{segment}
  - name: SimpleSignAuthing
    args:
      app-id-extractor: Parameter
      app-id-param: appId
      secret-param: appSecret
      sign-replay-verify-enabled: true
      sign-replay-verify-bloom-expire-seconds: 604800
      sign-param: sign
      sign-algorithm: S256
      sign-hashing-mode: SimpleParamsBytesSortedHashing
      sign-hashing-include-params: ['*']
      sign-hashing-exclude-params: ['response_type','__iscg_log']
      add-sign-auth-client-id-header: X-Sign-Auth-AppId
...
```

- Preconditions2 (generate mock sign request)

Run the test harness class in Idea/Eclipse/VsCode: `com.wl4g.escg.security.sign.SimpleSignGenerateTool` to generate test request parameters.

- Preconditions3 (setup secret to redis example)

```bash
redis-cli -c -h localhost -p 6379 -a '123456'
## Note: The configuration 'app-id-extractor' is 'Parameter'
set escg:auth:sign:secret:oijvin6crxu2qdqvpgls9jmijz4t6istxs <myAppSecret>
```

- Request

```bash
## for testing positive example
export APPID=oijvin6crxu2qdqvpgls9jmijz4t6istxs
export APPSECRET=njbbaiocxsbzzmtfsnenfvupzjyoioyu
export NONCE=nxFCAq0qrzFwqHcwfX0xvainRmQk6FvO
export TIMESTAMP=1652777325198
export SIGN=aa9c39efe90ef44bbcae258bb0b40653489186ac58266e092e3e420f1caa1573
export REMOTE_IP=127.0.0.1

curl -vL \
-H 'X-Escg-Trace: y' \
-H 'X-Escg-Log: y' \
-H 'X-Escg-Log-Level: 10' \
-H 'X-Response-Type: 10' \
"http://${REMOTE_IP}:18085/productpage-with-SimpleSignAuthing/get?appId=${APPID}&nonce=${NONCE}&timestamp=${TIMESTAMP}&sign=${SIGN}"
```

- Response(correct)

```json
{
  "args": {
    "appId": "oijvin6crxu2qdqvpgls9jmijz4t6istxs", 
    "nonce": "nxFCAq0qrzFwqHcwfX0xvainRmQk6FvO", 
    "sign": "aa9c39efe90ef44bbcae258bb0b40653489186ac58266e092e3e420f1caa1573", 
    "timestamp": "1652777325198"
  }, 
  "headers": {
    "Accept": "*/*", 
    "Content-Length": "0", 
    "Forwarded": "proto=http;host=\"127.0.0.1:18085\";for=\"127.0.0.1:50442\"", 
    "Host": "httpbin.org", 
    "Traceparent": "00-b9072cb8bcff2b47082b2f53199ef1a8-c02cf95da1747fb9-01", 
    "User-Agent": "curl/7.68.0", 
    "X-Amzn-Trace-Id": "Root=1-6283728d-27e55d6370e6b361627c5ea8", 
    "X-Forwarded-Host": "127.0.0.1:18085", 
    "X-Forwarded-Prefix": "/productpage-with-SimpleSignAuthing", 
    "X-Escg-Log": "y", 
    "X-Escg-Log-Dyeing-State": "b9072cb8bcff2b47082b2f53199ef1a8", 
    "X-Escg-Log-Level": "10", 
    "X-Escg-Trace": "y", 
    "X-Response-Type": "10", 
    "X-Sign-Auth-Appid": "oijvin6crxu2qdqvpgls9jmijz4t6istxs"
  }, 
  "origin": "127.0.0.1, 61.140.45.61", 
  "url": "http://127.0.0.1:18085/get?appId=oijvin6crxu2qdqvpgls9jmijz4t6istxs&nonce=nxFCAq0qrzFwqHcwfX0xvainRmQk6FvO&timestamp=1652777325198&sign=aa9c39efe90ef44bbcae258bb0b40653489186ac58266e092e3e420f1caa1573"
}
```

- Response(incorrect)

```json
{"code":423,"status":"Normal","requestId":null,"timestamp":1652781711036,"message":"[TEST-423] illegal_signature","data":{}}
```

- Tip: Cleanup the auth key of the replay sign in redis

```bash
redis-cli -c -h localhost -p 6379 -a '123456'
del escg:auth:sign:replay:bloom:productpage-service-route-with-SimpleSignAuthing
```

- View Authing Events

```bash
## The cache key format example is:'escg:auth:sign:event:failure:<routeId>:<yyMMdd>', of course, both prefixes and suffixes can be configured globally.
hgetall escg:auth:sign:event:success:productpage-service-route-with-SimpleSignAuthing:220517
hgetall escg:auth:sign:event:failure:productpage-service-route-with-SimpleSignAuthing:220517
```

### 2.8 Request Limiter

- 2.8.1 Concurrent requests

```bash
## for testing negative example(limited)
ab -n 2000 -c 15 \
-H 'X-Escg-Trace: y' \
-H 'X-Escg-Log: y' \
-H 'X-Escg-Log-Level: 10' \
-m POST \
'http://localhost:18085/productpage-with-IamRequestLimiter/post?response_type=json'
```

- 2.8.2 Debug limited headers

> Preconditions: Please make sure that the global default setup is: `spring.escg.requestlimit.limiter.[rate|quota].defaultStrategy.includeHeaders=true`,
or configure by routeId+limitKey: `{"includeHeaders":true, ...}`, refer to follower `2.8.4`

```bash
curl -vsSkL -XPOST \
-H 'X-Escg-Trace: y' \
-H 'X-Escg-Log: y' \
-H 'X-Escg-Log-Level: 10' \
-X POST \
'http://localhost:18085/productpage-with-IamRequestLimiter/post?response_type=json'

HTTP/1.1 200 OK
X-Request-Id: 643603ccd11aadf5a705191e5c11b2a4
X-Escg-RateLimit-Requested-Tokens: 1
X-Escg-RateLimit-Replenish-Rate: 10
X-Escg-RateLimit-LimitKey: 127.0.0.1
X-Escg-RateLimit-Burst-Capacity: 200
X-Escg-RateLimit-Remaining: 199
X-Escg-QuotaLimit-Remaining: 474
X-Escg-QuotaLimit-Cycle: 220518
X-Escg-QuotaLimit-LimitKey: /productpage-with-IamRequestLimiter/post
X-Escg-QuotaLimit-Request-Capacity: 1000
...
...
```

- 2.8.3 Configure for global default.

```bash
java -jar escg-1.0.0-bin.jar \
--spring.escg.requestlimit.limiter.rate.defaultStrategy.defaultBurstCapacity=1000 \
--spring.escg.requestlimit.limiter.rate.defaultStrategy.defaultReplenishRate=10 \
--spring.escg.requestlimit.limiter.rate.defaultStrategy.defaultRequestedTokens=1 \
--spring.escg.requestlimit.limiter.quota.defaultStrategy.requestCapacity=1000 \
--spring.escg.requestlimit.limiter.quota.defaultStrategy.cycleDatePattern=yyMMdd
```

- 2.8.4 Configure for routeId + limitKey(e.g: Principal/Header(X-Forward-Ip)/Path/...).

```bash
## The cache key format example is:'escg:requestlimit:config:rate:<routeId>:<limitKey>', of course, both prefixes and suffixes can be configured globally.

## The keyResolver is Header(X-Forward-Ip)
hset escg:requestlimit:config:rate productpage-service-route-with-IamRequestLimiter:127.0.0.1 '{"includeHeaders":true,"burstCapacity":1000,"replenishRate":1,"requestedTokens":1}'

hset escg:requestlimit:config:quota  productpage-service-route-with-IamRequestLimiter:127.0.0.1 '{"requestCapacity":1000,"cycleDatePattern":"yyMMddHH","includeHeaders":true}'

## The keyResolver is Path
hset escg:requestlimit:config:rate productpage-service-route-with-IamRequestLimiter:/productpage-with-IamRequestLimiter/get  '{"includeHeaders":true,"burstCapacity":1000,"replenishRate":1,"requestedTokens":1}'

hset escg:requestlimit:config:quota  productpage-service-route-with-IamRequestLimiter:/productpage-with-IamRequestLimiter/get  '{"requestCapacity":1000,"cycleDatePattern":"yyMMddHH","includeHeaders":true}'

## The keyResolver is Principal(appId)
hset escg:requestlimit:config:rate productpage-service-route-with-IamRequestLimiter:oijvin6crxu2qdqvpgls9jmijz4t6istxs '{"includeHeaders":true,"burstCapacity":1000,"replenishRate":1,"requestedTokens":1}'

hset escg:requestlimit:config:quota productpage-service-route-with-IamRequestLimiter:oijvin6crxu2qdqvpgls9jmijz4t6istxs '{"requestCapacity":1000,"cycleDatePattern":"yyMMddHH","includeHeaders":true}'

## ...
```

- 2.8.5 View request tokens

```bash
## The cache key format example is:'escg:requestlimit:token:quota:<yyMMdd>  <routeId>:<limitKey>', of course, both prefixes and suffixes can be configured globally.
hgetall escg:requestlimit:token:quota:220517
hget escg:requestlimit:token:quota:220517 productpage-service-route-with-IamRequestLimiter:127.0.0.1
```

- 2.8.6 View limited Events

```bash
## The cache key format example is:'escg:requestlimit:event:hits:rate:<routeId>:<yyMMdd>', of course, both prefixes and suffixes can be configured globally.
hgetall escg:requestlimit:event:hits:rate:productpage-service-route-with-IamRequestLimiter:220517
hgetall escg:requestlimit:event:hits:quota:productpage-service-route-with-IamRequestLimiter:220517
```

### 2.9 Traffic Replication

- The following example requires startup corresponding configuration file: `src/test/resources/bootstrap.yml`

```yaml
spring:
  profiles:
    include: "service-discovery,route-filter-splitting"
```

```bash
## Mock actual upstream http server.
python3 -m http.server -b 0.0.0.0 8888

## Mock traffic mirror upstream http server. see: escg-route.yaml#secure-httpbin-service-route
## Online by default: http://httpbin.org/
## [Optional] You can also use docker to local build an httpbin server.
#docker run -d --name=httpbin -p 8889:80 kennethreitz/httpbin

## 1. Send a mock request and observe that both terminals have output.
## 2. Then observe the output of the simulated mirror http server, and the response of the simulated real http server.

## for testing positive example1
curl -vsSkL -XGET -H 'X-Escg-Trace: y' -H 'X-Escg-Log-Level: 10' 'http://localhost:18085/productpage-with-TrafficReplicator/get'

## for testing positive example2
curl -vsSkL -XPOST \
-H 'X-Escg-Trace: y' \
-H 'X-Escg-Log: y' \
-H 'X-Escg-Log-Level: 10' \
-H 'Content-Type: application/json' \
-d '{"name":"jack"}' \
'http://localhost:18085/productpage-with-TrafficReplicator/post'
```

### 2.10 Response Cache

```bash
## for testing positive example
curl -vsSkL -XGET \
-H 'X-Escg-Trace: y' \
-H 'X-Escg-Log: y' \
-H 'X-Escg-Log-Level: 10' \
-H 'X-Escg-Cache: y' \
"http://localhost:18085/productpage-with-ResponseCache/get"

## for testing negative example
curl -vsSkL -XGET \
-H 'X-Escg-Trace: y' \
-H 'X-Escg-Log: y' \
-H 'X-Escg-Log-Level: 10' \
-H 'X-Escg-Cache: n' \
"http://localhost:18085/productpage-with-ResponseCache/get"
```

### 2.11 Retry

TODO

### 2.12 Circuit Breaker

TODO

### 2.13 Canary LoadBalancer

```bash
## for testing positive example
curl -vsSkL -XPOST \
-H 'X-Escg-Trace: y' \
-H 'X-Escg-Log: y' \
-H 'X-Escg-Log-Level: 10' \
-H 'X-Escg-Cache: y' \
-H 'X-Escg-Canary: v1' \
"http://localhost:18085/productpage-with-CanaryLoadBalancer/post"

## for testing positive example
curl -vsSkL -XPOST \
-H 'X-Escg-Trace: y' \
-H 'X-Escg-Log: y' \
-H 'X-Escg-Log-Level: 10' \
-H 'X-Escg-Cache: y' \
-H 'X-Escg-Canary: v2' \
"http://localhost:18085/productpage-with-CanaryLoadBalancer/post"
```

## 3. Admin & Operation APIs

- [docs.spring.io/spring-cloud-escg/docs/2.2.6.RELEASE/reference/html/#actuator-api](https://docs.spring.io/spring-cloud-escg/docs/2.2.6.RELEASE/reference/html/#actuator-api)

- Routes:

```bash
curl -v 'http://localhost:18086/actuator/escg/routes' | jq
```

- Actuator metrics:

```bash
curl -v 'http://localhost:18086/actuator/metrics' | jq
```

- Prometheus metrics:

```bash
curl -v 'http://localhost:18086/actuator/prometheus'
```

## 4. Performace

```bash
ab -n 10000 -c 1000 \
-H 'X-Escg-Trace: y' \
-H 'X-Escg-Log: y' \
-H 'X-Escg-Log-Level: 0' \
-H 'X-Escg-Fault: y' \
-H 'X-Escg-Canary: v1' \
-m POST \
'http://localhost:18085/productpage-with-IamRequestLimiter/post?&response_type=json'
```

## 5. FAQ

### 5.1 When the request limiter is configured, and then the log level is set to `com.wl4g.escg.requestlimit=TRACE`, an error will be displayed: `ERR Error running ... @user_script:1: user_script:1: attempt to call field 'replicate_commands' (a nil value)`

- Refer1: [stackoverflow attempt-to-call-field-replicate-commands-a-nil-value](https://stackoverflow.com/questions/59809151/attempt-to-call-field-replicate-commands-a-nil-value)

- Resolved: Since redis3.0 does not support the command 'replicate_commands()', just upgrade to redis3.2+

