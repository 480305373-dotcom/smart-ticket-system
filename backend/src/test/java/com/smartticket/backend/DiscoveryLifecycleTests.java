package com.smartticket.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.smartticket.backend.config.DiscoveryDrainConfig.DiscoveryLifecycle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.ApplicationEventPublisher;

class DiscoveryLifecycleTests {
  @Test void registrationUsesReadinessAndRemainsDownDuringDrain() {
    var endpoint=mock(HealthEndpoint.class);var descriptor=mock(CompositeHealthDescriptor.class);
    when(endpoint.healthForPath("readiness")).thenReturn(descriptor);when(descriptor.getStatus()).thenReturn(Status.UP);
    @SuppressWarnings("unchecked") var endpoints=(ObjectProvider<HealthEndpoint>)mock(ObjectProvider.class);
    @SuppressWarnings("unchecked") var managers=(ObjectProvider<ApplicationInfoManager>)mock(ObjectProvider.class);
    doReturn(endpoint).when(endpoints).getObject();
    var manager=mock(ApplicationInfoManager.class);when(managers.getIfAvailable()).thenReturn(manager);
    var lifecycle=new DiscoveryLifecycle(endpoints,managers,mock(ApplicationEventPublisher.class),0);
    assertEquals(InstanceStatus.STARTING,lifecycle.getStatus(InstanceStatus.UP));
    lifecycle.start();assertEquals(InstanceStatus.UP,lifecycle.getStatus(InstanceStatus.STARTING));
    var callback=mock(Runnable.class);lifecycle.stop(callback);
    verify(manager).setInstanceStatus(InstanceStatus.DOWN);verify(callback).run();
    assertEquals(InstanceStatus.DOWN,lifecycle.getStatus(InstanceStatus.UP));
    assertFalse(lifecycle.isRunning());
  }
  @Test void unavailableReadinessNeverAdvertisesAnInstanceAsHealthy() {
    @SuppressWarnings("unchecked") var endpoints=(ObjectProvider<HealthEndpoint>)mock(ObjectProvider.class);
    @SuppressWarnings("unchecked") var managers=(ObjectProvider<ApplicationInfoManager>)mock(ObjectProvider.class);
    when(endpoints.getObject()).thenThrow(new IllegalStateException("not initialized"));
    var lifecycle=new DiscoveryLifecycle(endpoints,managers,mock(ApplicationEventPublisher.class),0);
    lifecycle.start();assertEquals(InstanceStatus.DOWN,lifecycle.getStatus(InstanceStatus.UP));
    var endpoint=mock(HealthEndpoint.class);var descriptor=mock(CompositeHealthDescriptor.class);
    when(endpoint.healthForPath("readiness")).thenReturn(descriptor);when(descriptor.getStatus()).thenReturn(Status.UP);
    doReturn(endpoint).when(endpoints).getObject();
    assertEquals(InstanceStatus.UP,lifecycle.getStatus(InstanceStatus.DOWN));
  }
}
