package com.smartticket.backend.config;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.HealthCheckHandler;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Stop discovery traffic before Spring shuts the HTTP listener down. */
@Configuration
@ConditionalOnProperty(name="eureka.client.enabled",havingValue="true",matchIfMissing=true)
public class DiscoveryDrainConfig {
  @Bean
  public DiscoveryLifecycle discoveryLifecycle(
      ObjectProvider<HealthEndpoint> endpoint,
      ObjectProvider<ApplicationInfoManager> manager,
      ApplicationEventPublisher events,
      @Value("${app.discovery.drain-ms:20000}") long drainMillis) {
    return new DiscoveryLifecycle(endpoint,manager,events,drainMillis);
  }

  public static final class DiscoveryLifecycle implements SmartLifecycle, HealthCheckHandler {
    private final ObjectProvider<HealthEndpoint> endpoint;
    private final ObjectProvider<ApplicationInfoManager> manager;
    private final ApplicationEventPublisher events;
    private final long drainMillis;
    private volatile boolean running;
    private volatile boolean draining;

    public DiscoveryLifecycle(ObjectProvider<HealthEndpoint> endpoint,
        ObjectProvider<ApplicationInfoManager> manager,ApplicationEventPublisher events,long drainMillis) {
      if(drainMillis<0 || drainMillis>30000)throw new IllegalArgumentException("Discovery drain must be 0-30000ms");
      this.endpoint=endpoint;this.manager=manager;this.events=events;this.drainMillis=drainMillis;
    }

    @Override public InstanceStatus getStatus(InstanceStatus currentStatus) {
      if(draining)return InstanceStatus.DOWN;
      if(!running)return InstanceStatus.STARTING;
      try {
        var health=endpoint.getObject().healthForPath("readiness");
        return health!=null && Status.UP.equals(health.getStatus())?InstanceStatus.UP:InstanceStatus.DOWN;
      } catch(RuntimeException e) {
        return InstanceStatus.DOWN;
      }
    }

    @Override public void start(){running=true;}
    @Override public boolean isRunning(){return running;}
    // HTTP graceful shutdown uses a lower phase; our delay runs while HTTP still serves.
    @Override public int getPhase(){return Integer.MAX_VALUE;}
    @Override public void stop(){stop(()->{});}
    @Override public void stop(Runnable callback) {
      if(!running){callback.run();return;}
      draining=true;
      try {
        AvailabilityChangeEvent.publish(events,this,ReadinessState.REFUSING_TRAFFIC);
        var info=manager.getIfAvailable();
        // DOWN is a client health state, not the sticky operator OUT_OF_SERVICE override.
        if(info!=null)info.setInstanceStatus(InstanceStatus.DOWN);
        // Covers health replication (5s), server cache (2s), client fetch (5s), LB cache (2s).
        Thread.sleep(drainMillis);
      } catch(InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        running=false;
        callback.run();
      }
    }
  }
}
