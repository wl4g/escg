package com.wl4g.escg.loadbalance.chooser;

import java.util.List;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.web.server.ServerWebExchange;

import com.wl4g.escg.loadbalance.CanaryLoadBalancerFilterFactory;
import com.wl4g.escg.loadbalance.stats.LoadBalancerStats;

/**
 * Grayscale load balancer rule for weight-based random.
 * 
 * @author James Wong &lt;wanglsir@gmail.com, 983708408@qq.com&gt;
 * @date 2021-09-03 v1.0.0
 * @since v1.0.0
 */
public class WeightRandomCanaryLoadBalancerChooser extends RandomCanaryLoadBalancerChooser {

    @Override
    public LoadBalancerAlgorithm kind() {
        return LoadBalancerAlgorithm.WR;
    }

    @Override
    protected ServiceInstance doChooseInstance(
            CanaryLoadBalancerFilterFactory.Config config,
            ServerWebExchange exchange,
            LoadBalancerStats stats,
            String serviceId,
            List<ServiceInstance> candidateInstances) {

        // TODO

        return super.doChooseInstance(config, exchange, stats, serviceId, candidateInstances);
    }

}