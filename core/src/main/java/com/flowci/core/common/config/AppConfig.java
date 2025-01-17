/*
 * Copyright 2018 flow.ci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flowci.core.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowci.core.common.event.AsyncEvent;
import com.flowci.core.common.helper.JacksonHelper;
import com.flowci.core.common.helper.ThreadHelper;
import com.flowci.util.FileHelper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.ResolvableType;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executor;

/**
 * @author yang
 */
@Log4j2
@Configuration
@EnableScheduling
@EnableCaching
public class AppConfig {

    @Autowired
    private MultipartProperties multipartProperties;

    @Autowired
    private AppProperties appProperties;

    @PostConstruct
    private void initDirs() throws IOException {
        Path ws = appProperties.getWorkspace();
        FileHelper.createDirectory(ws);
        FileHelper.createDirectory(tmpDir());
        FileHelper.createDirectory(appProperties.getFlowDir());
        FileHelper.createDirectory(appProperties.getSiteDir());
    }

    @PostConstruct
    public void initUploadDir() throws IOException {
        Path path = Paths.get(multipartProperties.getLocation());
        FileHelper.createDirectory(path);
    }

    @Bean("tmpDir")
    public Path tmpDir() {
        return Paths.get(appProperties.getWorkspace().toString(), "tmp");
    }

    @Bean("objectMapper")
    public ObjectMapper objectMapper() {
        return JacksonHelper.create();
    }

    @Bean("appTaskExecutor")
    public ThreadPoolTaskExecutor getAppTaskExecutor() {
        int corePoolSize = appProperties.getCorePoolSize();
        int maxPoolSize = appProperties.getMaxPoolSize();
        return ThreadHelper.createTaskExecutor(maxPoolSize, corePoolSize, 100, "app-task-");
    }

    @Bean(name = "applicationEventMulticaster")
    public ApplicationEventMulticaster simpleApplicationEventMulticaster(TaskExecutor appTaskExecutor) {
        SimpleApplicationEventMulticaster multicaster = new SimpleApplicationEventMulticaster() {

            private ResolvableType resolveDefaultEventType(ApplicationEvent event) {
                return ResolvableType.forInstance(event);
            }

            @Override
            public void multicastEvent(ApplicationEvent event, ResolvableType eventType) {
                ResolvableType type = (eventType != null ? eventType : resolveDefaultEventType(event));
                Executor executor = getTaskExecutor();
                for (ApplicationListener<?> listener : getApplicationListeners(event, type)) {
                    if (listener instanceof AsyncEvent) {
                        executor.execute(() -> invokeListener(listener, event));
                        continue;
                    }
                    invokeListener(listener, event);
                }
            }
        };

        multicaster.setTaskExecutor(appTaskExecutor);
        return multicaster;
    }
}
