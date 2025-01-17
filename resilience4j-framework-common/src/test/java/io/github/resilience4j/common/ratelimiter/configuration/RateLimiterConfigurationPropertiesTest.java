/*
 * Copyright 2019 Mahmoud Romeh
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.resilience4j.common.ratelimiter.configuration;

import io.github.resilience4j.common.CompositeCustomizer;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigurationProperties;
import io.github.resilience4j.core.ConfigurationNotFoundException;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * test custom init of rate limiter properties
 */
public class RateLimiterConfigurationPropertiesTest {

    @Test
    public void testRateLimiterRegistry() {
        //Given
        RateLimiterConfigurationProperties.InstanceProperties instanceProperties1 = new RateLimiterConfigurationProperties.InstanceProperties();
        instanceProperties1.setLimitForPeriod(2);
        instanceProperties1.setWritableStackTraceEnabled(false);
        instanceProperties1.setSubscribeForEvents(true);
        instanceProperties1.setEventConsumerBufferSize(100);
        instanceProperties1.setLimitRefreshPeriod(Duration.ofMillis(100));
        instanceProperties1.setTimeoutDuration(Duration.ofMillis(100));

        RateLimiterConfigurationProperties.InstanceProperties instanceProperties2 = new RateLimiterConfigurationProperties.InstanceProperties();
        instanceProperties2.setLimitForPeriod(4);
        instanceProperties2.setSubscribeForEvents(true);
        instanceProperties2.setWritableStackTraceEnabled(true);

        RateLimiterConfigurationProperties rateLimiterConfigurationProperties = new RateLimiterConfigurationProperties();
        rateLimiterConfigurationProperties.getInstances().put("backend1", instanceProperties1);
        rateLimiterConfigurationProperties.getInstances().put("backend2", instanceProperties2);
        Map<String,String> globalTagsForRateLimiters=new HashMap<>();
        globalTagsForRateLimiters.put("testKey1","testKet2");
        rateLimiterConfigurationProperties.setTags(globalTagsForRateLimiters);
        //Then
        assertThat(rateLimiterConfigurationProperties.getTags().size()).isEqualTo(1);
        assertThat(rateLimiterConfigurationProperties.getInstances().size()).isEqualTo(2);
        assertThat(rateLimiterConfigurationProperties.getLimiters().size()).isEqualTo(2);
        RateLimiterConfig rateLimiter = rateLimiterConfigurationProperties
            .createRateLimiterConfig("backend1", compositeRateLimiterCustomizer());
        assertThat(rateLimiter).isNotNull();
        assertThat(rateLimiter.getLimitForPeriod()).isEqualTo(2);
        assertThat(rateLimiter.isWritableStackTraceEnabled()).isFalse();

        RateLimiterConfig rateLimiter2 = rateLimiterConfigurationProperties
            .createRateLimiterConfig("backend2", compositeRateLimiterCustomizer());
        assertThat(rateLimiter2).isNotNull();
        assertThat(rateLimiter2.getLimitForPeriod()).isEqualTo(4);
        assertThat(rateLimiter2.isWritableStackTraceEnabled()).isTrue();


    }

    @Test
    public void testCreateRateLimiterRegistryWithSharedConfigs() {
        //Given
        RateLimiterConfigurationProperties.InstanceProperties defaultProperties = new RateLimiterConfigurationProperties.InstanceProperties();
        defaultProperties.setLimitForPeriod(3);
        defaultProperties.setLimitRefreshPeriod(Duration.ofNanos(5000000));
        defaultProperties.setSubscribeForEvents(true);
        defaultProperties.setWritableStackTraceEnabled(false);

        RateLimiterConfigurationProperties.InstanceProperties sharedProperties = new RateLimiterConfigurationProperties.InstanceProperties();
        sharedProperties.setLimitForPeriod(2);
        sharedProperties.setLimitRefreshPeriod(Duration.ofNanos(6000000));
        sharedProperties.setSubscribeForEvents(true);

        RateLimiterConfigurationProperties.InstanceProperties backendWithDefaultConfig = new RateLimiterConfigurationProperties.InstanceProperties();
        backendWithDefaultConfig.setBaseConfig("defaultConfig");
        backendWithDefaultConfig.setLimitForPeriod(200);
        backendWithDefaultConfig.setSubscribeForEvents(true);
        backendWithDefaultConfig.setWritableStackTraceEnabled(true);

        RateLimiterConfigurationProperties.InstanceProperties backendWithSharedConfig = new RateLimiterConfigurationProperties.InstanceProperties();
        backendWithSharedConfig.setBaseConfig("sharedConfig");
        backendWithSharedConfig.setLimitForPeriod(300);
        backendWithSharedConfig.setSubscribeForEvents(true);
        backendWithSharedConfig.setWritableStackTraceEnabled(true);

        RateLimiterConfigurationProperties rateLimiterConfigurationProperties = new RateLimiterConfigurationProperties();
        rateLimiterConfigurationProperties.getConfigs().put("defaultConfig", defaultProperties);
        rateLimiterConfigurationProperties.getConfigs().put("sharedConfig", sharedProperties);

        rateLimiterConfigurationProperties.getInstances()
            .put("backendWithDefaultConfig", backendWithDefaultConfig);
        rateLimiterConfigurationProperties.getInstances()
            .put("backendWithSharedConfig", backendWithSharedConfig);

        //Then
        assertThat(rateLimiterConfigurationProperties.getInstances().size()).isEqualTo(2);

        // Should get default config and override LimitForPeriod
        RateLimiterConfig rateLimiter1 = rateLimiterConfigurationProperties
            .createRateLimiterConfig("backendWithDefaultConfig", compositeRateLimiterCustomizer());
        assertThat(rateLimiter1).isNotNull();
        assertThat(rateLimiter1.getLimitForPeriod()).isEqualTo(200);
        assertThat(rateLimiter1.getLimitRefreshPeriod()).isEqualTo(Duration.ofMillis(5));
        assertThat(rateLimiter1.isWritableStackTraceEnabled()).isTrue();

        // Should get shared config and override LimitForPeriod
        RateLimiterConfig rateLimiter2 = rateLimiterConfigurationProperties
            .createRateLimiterConfig("backendWithSharedConfig", compositeRateLimiterCustomizer());
        assertThat(rateLimiter2).isNotNull();
        assertThat(rateLimiter2.getLimitForPeriod()).isEqualTo(300);
        assertThat(rateLimiter2.getLimitRefreshPeriod()).isEqualTo(Duration.ofMillis(6));
        assertThat(rateLimiter2.isWritableStackTraceEnabled()).isTrue();

        // Unknown backend should get default config of Registry
        RateLimiterConfig rerateLimiter3 = rateLimiterConfigurationProperties
            .createRateLimiterConfig("unknownBackend", compositeRateLimiterCustomizer());
        assertThat(rerateLimiter3).isNotNull();
        assertThat(rerateLimiter3.getLimitForPeriod()).isEqualTo(50);
        assertThat(rerateLimiter3.isWritableStackTraceEnabled()).isTrue();


    }

    @Test
    public void testCreateRateLimiterRegistryWithDefaultConfig() {
        //Given
        RateLimiterConfigurationProperties.InstanceProperties defaultProperties = new RateLimiterConfigurationProperties.InstanceProperties();
        defaultProperties.setLimitForPeriod(3);
        defaultProperties.setLimitRefreshPeriod(Duration.ofNanos(5000000));
        defaultProperties.setSubscribeForEvents(true);
        defaultProperties.setWritableStackTraceEnabled(false);

        RateLimiterConfigurationProperties.InstanceProperties sharedProperties = new RateLimiterConfigurationProperties.InstanceProperties();
        sharedProperties.setLimitForPeriod(2);
        sharedProperties.setLimitRefreshPeriod(Duration.ofNanos(6000000));
        sharedProperties.setSubscribeForEvents(true);

        RateLimiterConfigurationProperties.InstanceProperties backendWithoutBaseConfig = new RateLimiterConfigurationProperties.InstanceProperties();
        backendWithoutBaseConfig.setLimitForPeriod(200);
        backendWithoutBaseConfig.setSubscribeForEvents(true);
        backendWithoutBaseConfig.setWritableStackTraceEnabled(true);

        RateLimiterConfigurationProperties.InstanceProperties backendWithSharedConfig = new RateLimiterConfigurationProperties.InstanceProperties();
        backendWithSharedConfig.setBaseConfig("sharedConfig");
        backendWithSharedConfig.setLimitForPeriod(300);
        backendWithSharedConfig.setSubscribeForEvents(true);
        backendWithSharedConfig.setWritableStackTraceEnabled(true);

        RateLimiterConfigurationProperties rateLimiterConfigurationProperties = new RateLimiterConfigurationProperties();
        rateLimiterConfigurationProperties.getConfigs().put("default", defaultProperties);
        rateLimiterConfigurationProperties.getConfigs().put("sharedConfig", sharedProperties);

        rateLimiterConfigurationProperties.getInstances()
            .put("backendWithoutBaseConfig", backendWithoutBaseConfig);
        rateLimiterConfigurationProperties.getInstances()
            .put("backendWithSharedConfig", backendWithSharedConfig);

        //Then
        assertThat(rateLimiterConfigurationProperties.getInstances().size()).isEqualTo(2);

        // Should get default config and override LimitForPeriod
        RateLimiterConfig rateLimiter1 = rateLimiterConfigurationProperties
            .createRateLimiterConfig("backendWithoutBaseConfig", compositeRateLimiterCustomizer());
        assertThat(rateLimiter1).isNotNull();
        assertThat(rateLimiter1.getLimitForPeriod()).isEqualTo(200);
        assertThat(rateLimiter1.getLimitRefreshPeriod()).isEqualTo(Duration.ofNanos(5000000));
        assertThat(rateLimiter1.isWritableStackTraceEnabled()).isTrue();

        // Should get shared config and override LimitForPeriod
        RateLimiterConfig rateLimiter2 = rateLimiterConfigurationProperties
            .createRateLimiterConfig("backendWithSharedConfig", compositeRateLimiterCustomizer());
        assertThat(rateLimiter2).isNotNull();
        assertThat(rateLimiter2.getLimitForPeriod()).isEqualTo(300);
        assertThat(rateLimiter2.getLimitRefreshPeriod()).isEqualTo(Duration.ofMillis(6));
        assertThat(rateLimiter2.isWritableStackTraceEnabled()).isTrue();

        // Unknown backend should get default config of Registry
        RateLimiterConfig rateLimiter3 = rateLimiterConfigurationProperties
            .createRateLimiterConfig("unknownBackend", compositeRateLimiterCustomizer());
        assertThat(rateLimiter3).isNotNull();
        assertThat(rateLimiter3.getLimitForPeriod()).isEqualTo(3);
        assertThat(rateLimiter3.isWritableStackTraceEnabled()).isFalse();
    }

    @Test
    public void testCreateRateLimiterRegistryWithUnknownConfig() {
        RateLimiterConfigurationProperties rateLimiterConfigurationProperties = new RateLimiterConfigurationProperties();

        RateLimiterConfigurationProperties.InstanceProperties instanceProperties = new RateLimiterConfigurationProperties.InstanceProperties();
        instanceProperties.setBaseConfig("unknownConfig");
        rateLimiterConfigurationProperties.getInstances().put("backend", instanceProperties);

        //When
        assertThatThrownBy(
            () -> rateLimiterConfigurationProperties
                .createRateLimiterConfig("backend", compositeRateLimiterCustomizer()))
            .isInstanceOf(ConfigurationNotFoundException.class)
            .hasMessage("Configuration with name 'unknownConfig' does not exist");
    }

    @Test
    public void testFindRateLimiterProperties() {
        RateLimiterConfigurationProperties rateLimiterConfigurationProperties = new RateLimiterConfigurationProperties();
        RateLimiterConfigurationProperties.InstanceProperties instanceProperties = new RateLimiterConfigurationProperties.InstanceProperties();
        instanceProperties.setLimitForPeriod(3);
        instanceProperties.setLimitRefreshPeriod(Duration.ofNanos(5000000));
        instanceProperties.setSubscribeForEvents(true);

        rateLimiterConfigurationProperties.getInstances().put("default", instanceProperties);

        assertThat(
            rateLimiterConfigurationProperties.findRateLimiterProperties("default").isPresent())
            .isTrue();
        assertThat(
            rateLimiterConfigurationProperties.findRateLimiterProperties("custom").isPresent())
            .isFalse();
    }

    @Test
    public void testRateLimiterConfigWithBaseConfig() {
        RateLimiterConfigurationProperties.InstanceProperties defaultConfig = new RateLimiterConfigurationProperties.InstanceProperties();
        defaultConfig.setLimitForPeriod(2000);
        defaultConfig.setLimitRefreshPeriod(Duration.ofMillis(100L));

        RateLimiterConfigurationProperties.InstanceProperties sharedConfigWithDefaultConfig = new RateLimiterConfigurationProperties.InstanceProperties();
        sharedConfigWithDefaultConfig.setLimitRefreshPeriod(Duration.ofMillis(1000L));
        sharedConfigWithDefaultConfig.setBaseConfig("defaultConfig");

        RateLimiterConfigurationProperties.InstanceProperties instanceWithSharedConfig = new RateLimiterConfigurationProperties.InstanceProperties();
        instanceWithSharedConfig.setBaseConfig("sharedConfig");


        RateLimiterConfigurationProperties rateLimiterConfigurationProperties = new RateLimiterConfigurationProperties();
        rateLimiterConfigurationProperties.getConfigs().put("defaultConfig", defaultConfig);
        rateLimiterConfigurationProperties.getConfigs().put("sharedConfig", sharedConfigWithDefaultConfig);
        rateLimiterConfigurationProperties.getInstances().put("instanceWithSharedConfig", instanceWithSharedConfig);


        RateLimiterConfig instance = rateLimiterConfigurationProperties
            .createRateLimiterConfig(instanceWithSharedConfig, compositeRateLimiterCustomizer(), "instanceWithSharedConfig");
        assertThat(instance).isNotNull();
        assertThat(instance.getLimitForPeriod()).isEqualTo(2000);
        assertThat(instance.getLimitRefreshPeriod()).isEqualTo(Duration.ofMillis(1000L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgumentOnEventConsumerBufferSize() {
        RateLimiterConfigurationProperties.InstanceProperties defaultProperties = new RateLimiterConfigurationProperties.InstanceProperties();
        defaultProperties.setEventConsumerBufferSize(-1);
    }

    @Test
    public void testFindRateLimiterPropertiesWithoutDefaultConfig() {
        //Given
        RateLimiterConfigurationProperties.InstanceProperties backendWithoutBaseConfig = new RateLimiterConfigurationProperties.InstanceProperties();

        RateLimiterConfigurationProperties rateLimiterConfigurationProperties = new RateLimiterConfigurationProperties();
        rateLimiterConfigurationProperties.getInstances().put("backendWithoutBaseConfig", backendWithoutBaseConfig);

        //Then
        assertThat(rateLimiterConfigurationProperties.getInstances().size()).isEqualTo(1);

        // Should get default config and overwrite registerHealthIndicator, allowHealthIndicatorToFail and eventConsumerBufferSize
        Optional<RateLimiterConfigurationProperties.InstanceProperties> rateLimiterProperties =
            rateLimiterConfigurationProperties.findRateLimiterProperties("backendWithoutBaseConfig");
        assertThat(rateLimiterProperties).isPresent();
        assertThat(rateLimiterProperties.get().getRegisterHealthIndicator()).isNull();
        assertThat(rateLimiterProperties.get().getAllowHealthIndicatorToFail()).isNull();
        assertThat(rateLimiterProperties.get().getEventConsumerBufferSize()).isNull();
    }

    @Test
    public void testFindCircuitBreakerPropertiesWithDefaultConfig() {
        //Given
        RateLimiterConfigurationProperties.InstanceProperties defaultProperties = new RateLimiterConfigurationProperties.InstanceProperties();
        defaultProperties.setRegisterHealthIndicator(true);
        defaultProperties.setEventConsumerBufferSize(99);

        RateLimiterConfigurationProperties.InstanceProperties backendWithoutBaseConfig = new RateLimiterConfigurationProperties.InstanceProperties();

        RateLimiterConfigurationProperties rateLimiterConfigurationProperties = new RateLimiterConfigurationProperties();
        rateLimiterConfigurationProperties.getConfigs().put("default", defaultProperties);
        rateLimiterConfigurationProperties.getInstances().put("backendWithoutBaseConfig", backendWithoutBaseConfig);

        //Then
        assertThat(rateLimiterConfigurationProperties.getInstances().size()).isEqualTo(1);

        // Should get default config and overwrite registerHealthIndicator and eventConsumerBufferSize but not allowHealthIndicatorToFail
        Optional<RateLimiterConfigurationProperties.InstanceProperties> rateLimiterProperties =
            rateLimiterConfigurationProperties.findRateLimiterProperties("backendWithoutBaseConfig");
        assertThat(rateLimiterProperties).isPresent();
        assertThat(rateLimiterProperties.get().getRegisterHealthIndicator()).isTrue();
        assertThat(rateLimiterProperties.get().getAllowHealthIndicatorToFail()).isNull();
        assertThat(rateLimiterProperties.get().getEventConsumerBufferSize()).isEqualTo(99);
    }

    private CompositeCustomizer<RateLimiterConfigCustomizer> compositeRateLimiterCustomizer() {
        return new CompositeCustomizer<>(Collections.emptyList());
    }

}
