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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerFactory;

import com.pavlovmedia.oss.jaxrs.publisher.api.Publisher;

/**
 * This is a tracker that will track all services in OSGi. It operates very
 * much like the osgi-jax-rs-connector that is being replaced.
 * 
 * Because it operates on {@link ServiceEvent}, care needs to be taken with getting
 * and ungetting services.
 * 
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
@Component(factory=WidcardServiceTracker.FACTORY_NAME,
    property= {
        "com.eclipsesource.jaxrs.publish=false"
    })
public class WidcardServiceTracker extends BaseObjectTracker {
    public static final String FACTORY_NAME = "com.pavlovmedia.oss.jaxrs.provider.impl.WidcardServiceTracker";
    public static final String FACTORY_FILTER = "(component.factory="+FACTORY_NAME+")";
    
    /** This service filter grabs everything that isn't marked with ignore */
    private static final String SERVICE_FILTER = "(&(objectClass=*)(!(" + Publisher.SCAN_IGNORE + "=*)))";
    
    /** This is a blocker to keep us from parsing while we are shutting down */
    private final AtomicBoolean processing = new AtomicBoolean(true);

    @Reference(service = LoggerFactory.class)
    Logger logger;
    
    @Override
    public void logDebug(final String format, final Object... args) {
        logger.debug(String.format(format, args));
    }
    
    @Override
    public void logInfo(final String format, final Object... args) {
        logger.info(String.format(format, args));
    }
    
    @Override
    public void logError(final Exception e, final String format, final Object... args) {
        logger.info(String.format(format, args), e);
    }
    
    /** The context is saved so we can get and unget services */
    private BundleContext context;
    
    /** This is a tracker for any {@link ServiceReference} that we hold */
    final List<ServiceReference<?>> openReferences = new CopyOnWriteArrayList<>();
    
    /**
     * This activate is here to control the service
     * tracker directly. It will start an event watcher and then scan all the 
     * existing services looking for something that appears to be JAX-RS, which is
     * handled by the base class.
     * 
     * @param properties OSGi properties passed along
     * @param context OSGi bundle context for this bundle
     * 
     * @throws InvalidSyntaxException should never be thrown because the filter is a constant
     */
    @Activate
    protected void activate(final Map<String, Object> properties, final BundleContext context) throws InvalidSyntaxException {
        logDebug("Starting %s", this.getClass().getName());
        onTargetChange = Optional.ofNullable((Runnable) properties.get(CALLBACK));
        
        this.context = context;
        context.addServiceListener(this::serviceChanged, SERVICE_FILTER);
        
        // This walks all the existing services so bundle start
        // ordering has no impact on JAX-RS functions
        try {
            Arrays.asList(context.getAllServiceReferences(null, SERVICE_FILTER))
                .forEach(this::tryAddService);
        } catch (InvalidSyntaxException e) {
            logError(e, "Failed to pull existing services");
        }
        
        // If we pre-bound anything, notify now
        if (!jaxrsTargets.isEmpty()) {
            logDebug("Kicking initial page set");
            onTargetChange.ifPresent(Runnable::run);
        } else {
            logDebug("No pages to bind yet");
        }
    }
    
    /**
     * Not sure if it is really needed, but get rid of the service listener before
     * a full shutdown occurs
     */
    @Deactivate
    protected void deactivate() {
        processing.set(false);
        context.removeServiceListener(this::serviceChanged);
    }
    
    /**
     * This is the tracker event that tells about services coming and
     * going. We just take these events and pass along info to the 
     * parent class to determine if they are jax classes or not.
     * 
     * @param event the OSGi service event
     */
    private void serviceChanged(final ServiceEvent event) {
        // If we are shutting down just stop looking at services
        if (!processing.get()) {
            return;
        }
        
        ServiceReference<?> serviceReference = event.getServiceReference();
        
        switch(event.getType()) {
            case ServiceEvent.REGISTERED:
                // See if we really want to use this
                tryAddService(serviceReference);
                break;
            case ServiceEvent.UNREGISTERING:
                if (openReferences.contains(serviceReference)) {
                    logger.debug(String.format("Removing service %s", serviceReference));
                    openReferences.remove(serviceReference);
                    context.ungetService(serviceReference);
                } else {
                    logger.debug(String.format("Did not find service reference %s in %s", serviceReference, openReferences));
                }
                removeTarget(serviceReference);
                break;
            default:
                // Do nothing
                break;
        }
    }
    
    /**
     * Takes an OSGi service and adds it if it matches the
     * criteria for being a JAX-RS target.
     * 
     * @param serviceReference the service reference of the service
     */
    private void tryAddService(final ServiceReference<?> serviceReference) {
        try {
            Object jaxPage = context.getService(serviceReference);
            if (null != jaxPage) {
                if (addTarget(serviceReference, jaxPage)) {
                    openReferences.add(serviceReference);
                } else {
                    context.ungetService(serviceReference);
                }
            }
        } catch (IllegalStateException e) {
            logError(e, "Failed to look at service %s", serviceReference);
        }
    }
}
