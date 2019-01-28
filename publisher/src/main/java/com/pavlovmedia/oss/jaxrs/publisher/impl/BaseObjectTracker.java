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
package com.pavlovmedia.oss.jaxrs.publisher.impl;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

import javax.ws.rs.Path;
import javax.ws.rs.core.Feature;
import javax.ws.rs.ext.Provider;

import org.osgi.framework.ServiceReference;

/**
 * This is the skeleton for a service tracker that handles filtering
 * of objects, parsing them, and storing them.
 * 
 * The logic of how to get the services is left to the subclasses that
 * use this tracker
 * 
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
public class BaseObjectTracker {
    /** This is a configuration property used to add a reconfiguration callback */
    public static final String CALLBACK = "callback";
    
    /**
     * This holds the set of targets that we provide to Jersey
     */
    protected final Set<JaxReference> jaxrsTargets = new CopyOnWriteArraySet<>();
    
    /**
     * This is the callback registered during the factory creation
     * that will be signaled when a target is added or removed
     */
    protected Optional<Runnable> onTargetChange = Optional.empty();
    
    /**
     * This is a very brain-dead logging method, it really needs
     * to be overridden by one that logs using the OSGi logger.
     * 
     * @param format
     * @param args
     */
    public void logDebug(final String format, final Object...args) {
        System.out.println(String.format(format, args));
    }
    
    /**
     * This is a very brain-dead logging method, it really needs
     * to be overridden by one that logs using the OSGi logger.
     * 
     * @param format
     * @param args
     */
    public void logInfo(final String format, final Object...args) {
        System.out.println(String.format(format, args));
    }
    
    /**
     * This is a very brain-dead logging method, it really needs
     * to be overridden by one that logs using the OSGi logger.
     * 
     * @param e
     * @param format
     * @param args
     */
    public void logError(final Exception e, final String format, final Object...args) {
        System.out.println(String.format(format, args));
        e.printStackTrace();
    }
    
    /**
     * This will return a set of objects as needed by
     * the Jersey application singleton call.
     * 
     * This list may already be of objects already
     * so the conversion here may be extra, but you
     * can always override it in that case.
     * @return
     */
    public Set<Object> getJaxrsTargets() {
        return jaxrsTargets.stream()
                .map(JaxReference::getJaxObject)
                .collect(Collectors.toSet());
    }
    
    /**
     * This is called from the implementing class to add a JAX-RS target
     * @param target
     */
    protected boolean addTarget(final ServiceReference serviceReference, final Object target) {
        if (isJaxrsTarget(target.getClass(), target)) {
            logInfo("Adding target %s", target);
            if (jaxrsTargets.add(new JaxReference(serviceReference, target))) {
                onTargetChange.ifPresent(Runnable::run);
                return true;
            }
        }
        return false;
    }
    
    /**
     * This is called from the implementing class to remove a JAX-RS target
     * @param target the service to be removed
     */
    protected boolean removeTarget(final ServiceReference target) {
        // Sometimes service tracking gets us null objects
        if (null != target) {
            logDebug("Removing target %s", target);
            // JaxReference is keyed by the service reference, so we can leave the jaxObject null for this
            jaxrsTargets.remove(new JaxReference(target, null));
            onTargetChange.ifPresent(Runnable::run);
            return true;
        }
        return false;
    }
    
    /**
     * The goal of this method is to see if this object is something
     * that JAX-RS can use, it does so by looking at the class of the
     * object in question
     * 
     * @param clazz class to inspect
     * @return true if this is a class type that jaxrs will use
     */
    public boolean isJaxrsTarget(final Class<?> clazz, final Object target) {
        Annotation[] annotations = clazz.getDeclaredAnnotations();
        if (null != annotations) {
            Arrays.asList(annotations).forEach(a -> logDebug("%s", a.annotationType()));
        }
        
        // To be a jax target we can be a Feature, annotated with Path, or annotated with Provider
        return null != clazz.getDeclaredAnnotation(Path.class) 
                || null != clazz.getDeclaredAnnotation(Provider.class)
                || target instanceof Feature;
    }
}
