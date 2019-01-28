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
package com.pavlovmedia.oss.jaxrs.publisher.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.Feature;

/**
 * This is mainly to help utility classes extract more information from
 * JAX-RS
 *
 * This can be very useful for tools that want to inspect what information
 * is being passed to Jersey as well as getting notified when changes happen.
 * 
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
public interface Publisher {
    /** 
     * This is used as an OSGi property on a class to cause the
     * scanner to skip the service as a potential target for
     * JAX-RS
     */
    String SCAN_IGNORE = "jaxSkip";
    
    /**
     * Gets a list of the current endpoints that have been
     * passed along to Jersey
     * 
     * @return a map that is the class name and a list of endpoints
     */
    Map<String, List<EndpointInfo>> getEndpoints();
    
    /**
     * This is a raw set of Objects that are endpoint services
     * that have been passed along to jersey
     * 
     * @return a list of objects representing JAX-RS pages
     */
    Set<Object> getRawEndpoints();
    
    /**
     * Gets the list of registered provider services that have
     * been passed along to Jersey
     * 
     * @return a list of provider objects
     */
    Set<Object> getProviders();
    
    /**
     * Gets the list of registered feature services that have
     * been passed along to Jersey
     * 
     * @return a list of feature services
     */
    Set<Feature> getFeatures();
    
    /**
     * Adds a class definition for a reader listener object.
     * Note that these are not OSGi classes
     * @param clazz
     */
    void addReaderListener(Class<?> clazz);
    
    /**
     * Remove a class definition for a reader listener object.
     * @param clazz
     */
    void removeReaderListener(Class<?> clazz);
    
    /**
     * Gets the list of {@link ReaderListener} objects that
     * have been detected by the runtime.
     * 
     * @return
     */
    Set<Class<?>> getReaderListeners();
    
    /** Gets the web path of this publisher */
    String getPath();
    
    /**
     * This is used to pass along a lambda that will be triggered
     * whenever service changes cause us to reconfigure Jersey.
     * 
     * @param onChange a callback that notes a change
     * @return an id used by {@link #unsubscribe(String)} that
     * will stop the callbacks.
     */
    String subscribe(Runnable onChange);
    
    /**
     * Removes a callback with by id tracked from {@link #subscribe(Runnable)} so
     * it will no longer be called.
     * @param id a key returned from {@link #subscribe(Runnable)}
     */
    void unsubscribe(String id);
}
