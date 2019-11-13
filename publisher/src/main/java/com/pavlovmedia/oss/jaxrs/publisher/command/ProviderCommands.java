/*
 * Copyright 2017 Pavlov Media
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pavlovmedia.oss.jaxrs.publisher.command;
 

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.pavlovmedia.oss.jaxrs.publisher.api.Publisher;

/**
 * Commands to facilitate debugging of the JAX-RS system.
 * 
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
@Component(
    service=ProviderCommands.class,
    property= {
        Publisher.SCAN_IGNORE + "=true",
        "osgi.command.scope=jax",
        "osgi.command.function=getEndpoints",
        "osgi.command.function=getFeatures",
        "osgi.command.function=getProviders"
    }) 
public class ProviderCommands {
    @Reference
    Publisher publisher;
    
    /**
     * Lists all the registred endpoints
     */
    public void getEndpoints() {
        System.out.println("JAX-RS Endpoint mappings: ");
        publisher.getEndpoints().forEach((k,v) -> {
            System.out.println(k);
            v.forEach(e -> System.out.println(String.format("\t%s", e)));
        });
    }
    
    /**
     * Lists all the registered features
     */
    public void getFeatures() {
        System.out.println("JAX-RS Features:");
        publisher.getFeatures().forEach(f -> System.out.println(f.getClass().getName()));
    }
    
    /**
     * Lists all the registered providers
     */
    public void getProviders() {
        System.out.println("JAX-RS Providers:");
        publisher.getProviders().forEach(p -> {
            System.out.println(String.format("%s with the following interfaces:", p.getClass().getName()));
            for (Class<?> i :p.getClass().getInterfaces()) {
                System.out.println(String.format("\t%s", i.getName()));
            }
        });
    }
}
