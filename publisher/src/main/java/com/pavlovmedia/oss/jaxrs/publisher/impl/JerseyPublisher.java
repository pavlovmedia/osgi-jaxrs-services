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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.ws.rs.Path;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Feature;
import javax.ws.rs.ext.Provider;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.glassfish.jersey.media.sse.SseFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.servlet.ServletContainer;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentFactory;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.osgi.service.log.LogService;

import com.pavlovmedia.osgi.oss.utilities.api.component.ComponentHolder;
import com.pavlovmedia.oss.jaxrs.publisher.api.EndpointInfo;
import com.pavlovmedia.oss.jaxrs.publisher.api.Publisher;
import com.pavlovmedia.oss.jaxrs.publisher.impl.swagger.SwaggerEndpoint;

/**
 * This is the main control of the JAX-RS publisher. It wraps
 * around the glassfish container and handles all the service
 * configurations needed for JAX-RS classes.
 * 
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
@Component(metatype=true, immediate=true)
@Service
@Properties({
    // Don't scan this class or we will get a dependency cycle
    @Property(name = Publisher.SCAN_IGNORE, value = "true", propertyPrivate=true),
    @Property(name="com.eclipsesource.jaxrs.publish", boolValue=false, propertyPrivate=true),
    @Property(name = JerseyPublisher.PATH, label = "JAX-RS Path",
            description = "Path to serve JAX-RS endpoints from",
            value = "/services"),
})
public class JerseyPublisher extends Application implements Publisher {
    public static final String PATH = "path";
    public static final String INHIBIT_START = "pavlovStackInhibit";
    @Reference
    LogService logger;
    
    @Reference
    HttpService httpService;
    
    /**
     * This is our tracker that watches {@link ServiceEvent}s to discover
     * new services.
     */
    @Reference(target=WidcardServiceTracker.FACTORY_FILTER)
    ComponentFactory wildcardTrackerFactory;
    ComponentHolder<WidcardServiceTracker> wildcardTracker = new ComponentHolder<>();
    
    /** Keeps track of people watching for changes */
    private ConcurrentHashMap<String, Runnable> changeWatchers = new ConcurrentHashMap<>();
    
    private CopyOnWriteArraySet<Class<?>> readerListenerSet = new CopyOnWriteArraySet<>();
    
    /** The path we are serving on */
    private String jaxPath;
    
    @Override
    public String getPath() {
        return jaxPath;
    }
    
    /** Reference to the bundle context to register and unregister services */
    private BundleContext bundleContext;
    
    /** The servlet that is used to provide JAX-RS */
    private ServletContainer container; 
    
    /** 
     * Used to track if the servlet has been initialized and can get
     * configuration reloads
     */
    private final AtomicBoolean initialized = new AtomicBoolean();
    
    /** Used to track the swagger support */
    private Optional<ServiceReference> swaggerEndpoint = Optional.empty();
    
    /** 
     * This is a set of features we will try to turn on if they
     * have bundles available
     */
    ArrayList<ServiceRegistration> featureRegistrations = new ArrayList<>();
    
    /**
     * Service activator. This sets up the service tracker, starts up Jersey
     * and registers a number of features that get used by common applications.
     * 
     * @param properties OSGi properties
     * @param context the {@link BundleContext for this bundle}
     */
    @Activate
    protected void activate(final Map<String,Object> properties, final BundleContext context) {
        bundleContext = context;
        
        if (Boolean.valueOf(context.getProperty(INHIBIT_START))) {
            logger.log(LogService.LOG_ERROR, "JAX-RS Start inhibited");
            System.err.println("JAX-RS Start inhibited");
            return;
        }
        
        jaxPath = (String) properties.get(PATH);
        info("JerseyPublisher activating at root %s", jaxPath);
        
        // XXX: is this needed?
        System.setProperty("javax.ws.rs.ext.RuntimeDelegate", 
                "org.glassfish.jersey.server.internal.RuntimeDelegateImpl");
        
        HashMap<String,Object> serviceProperties = new HashMap<>();
        serviceProperties.put(BaseObjectTracker.CALLBACK, (Runnable) this::onChange);
        
        debug("Starting Wildcard tracker");
        wildcardTracker.setFactory(wildcardTrackerFactory);
        wildcardTracker.provision(serviceProperties);
        
        tryRegisterFeature(() -> SseFeature.class);

        startServlet();
        
        // This section will try to enable swagger support. If the
        // swagger bundles are present it will do all the work to
        // publish swagger
        tryStartSwagger();
        
        try {
            bundleContext.addServiceListener(this::swaggerCallback, 
                    "(component.name="+SwaggerEndpoint.class.getName()+")");
        } catch (InvalidSyntaxException | NoClassDefFoundError e) {
            // These errors are directly impacted to the optional
            // imports from swagger
            info("Not enabling swagger at this time");
        }
    }
    
    /**
     * This attempts to register a feature if it is provided by
     * another bundle. It is done in this manner to catch the 
     * situation where the bundle is not available. The common case
     * is for SSE, it is listed as an optional import in the manifest
     * so one may choose not to bring it in, and this should allow the
     * system to run, even without it.
     * 
     * @param featureClassSupplier A lambda that returns a class to register
     */
    // This catches Exception because OSGi can make this fail in unusual ways
    private void tryRegisterFeature(final Supplier<Class<?>> featureClassSupplier) {
        try {
            Object feature = featureClassSupplier.get().newInstance();
            ServiceRegistration reg = bundleContext.registerService(featureClassSupplier.get().getName(), feature, null);
            featureRegistrations.add(reg);
        } catch (NoClassDefFoundError | InstantiationException | IllegalAccessException e) {
            info("Failed to register feature if you don't need it, don't worry: %s",
                    e.getMessage());
        }
    }
    /**
     * This method does half the work of starting up swagger. At startup, or when
     * services are added it will be triggered to see if the swagger endpoint
     * service has started.
     */
    private void tryStartSwagger() {
        try {
            // We will search for a service reference that implements swagger
            // this is a loose relation so that we are able to fail easily
            swaggerEndpoint = Optional.ofNullable(bundleContext.getServiceReference(SwaggerEndpoint.class.getName()));
            // Does a get on this service so we are linked to it, but we don't need it
            // directly
            swaggerEndpoint.ifPresent(bundleContext::getService);
            if (swaggerEndpoint.isPresent()) {
                info("Swagger support enabled");
            }
        } catch (NoClassDefFoundError e) {
            // This will happen if we can't resolve the swagger imports
        }
    }
    
    private void swaggerCallback(final ServiceEvent event) {
        switch (event.getType()) {
            case ServiceEvent.REGISTERED:
                if (!swaggerEndpoint.isPresent()) {
                    tryStartSwagger();
                }
                break;
            case ServiceEvent.UNREGISTERING:
                swaggerEndpoint.ifPresent(bundleContext::ungetService);
                break;
            default:
                // Do nothing
                break;
        }
    }
    
    /**
     * This starts up the jersey servlet. Initially this is empty, but that is
     * ok, as soon as the scanner starts up, it will reconfigure.
     */
    private void startServlet() {
        try {
            container = new ServletContainer(ResourceConfig.forApplication(this));
            initialized.set(true);
            Hashtable<String,String> jerseyParams = new Hashtable<>();
            jerseyParams.put("javax.ws.rs.Application", JerseyPublisher.class.getName());
            
            httpService.registerServlet(jaxPath, container, jerseyParams, null);
        } catch (ServletException | NamespaceException e) {
            error(e, "Failed to start up JAX-RS: %s", e.getMessage());
        }
    }
    
    /**
     * Called whenever a change is made that would affect the servlet operation
     * aka add a new service, remove a service.
     */
    protected void onChange() {
        if (initialized.get()) {
            debug("Reloading configuration");
            container.reload(ResourceConfig.forApplication(this));
            changeWatchers.values().forEach(Runnable::run);
        }
    }
    
    @Deactivate
    protected void deactivate() {
        info("Jersey publisher shutting down");
        initialized.set(false);

        wildcardTracker.close();
        
        container = null;
        httpService.unregister(jaxPath);
        
        featureRegistrations.forEach(ServiceRegistration::unregister);
        swaggerEndpoint.ifPresent(bundleContext::ungetService);
    }
    
    @Override
    public Set<Object> getSingletons() {
        HashSet<Object> ret = new HashSet<Object>(super.getSingletons());
        if (initialized.get()) {
            ret.addAll(wildcardTracker.withService(BaseObjectTracker::getJaxrsTargets));
        } else {
            debug("Jersey not up yet");
        }
        return ret;
    }
   
    private void debug(final String format, final Object...args) {
        logger.log(LogService.LOG_DEBUG, String.format(format, args));
    }
    
    private void info(final String format, final Object...args) {
        logger.log(LogService.LOG_INFO, String.format(format, args));
    }
    
    private void error(final Exception e, final String format, final Object...args) {
        logger.log(LogService.LOG_ERROR, String.format(format, args), e);
    }
    
    @Override
    public Map<String, Object> getProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ServerProperties.METAINF_SERVICES_LOOKUP_DISABLE, false);
        properties.put(ServerProperties.FEATURE_AUTO_DISCOVERY_DISABLE, true);
        return properties;
    }
    
    @Override
    public Set<Object> getRawEndpoints() {
        return getSingletons().stream()
                .filter(o -> null != o.getClass().getDeclaredAnnotation(Path.class))
                .collect(Collectors.toSet());
    }
    
    @Override
    public Map<String,List<EndpointInfo>> getEndpoints() {
        return getRawEndpoints().stream()
                .collect(Collectors.toMap(o -> o.getClass().getName(), EndpointInfo::parseEndpoint));
    }

    @Override
    public Set<Object> getProviders() {
        return getSingletons().stream()
                .filter(o -> null != o.getClass().getDeclaredAnnotation(Provider.class))
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Feature> getFeatures() {
        return getSingletons().stream()
                .filter(o -> o instanceof Feature)
                .map(o -> (Feature) o)
                .collect(Collectors.toSet());
    }

    public void addReaderListener(final Class<?> clazz) {
        readerListenerSet.add(clazz);
    }
    
    public void removeReaderListener(final Class<?> clazz) {
        readerListenerSet.remove(clazz);
    }
    
    @Override
    public Set<Class<?>> getReaderListeners() {
        return readerListenerSet;
    }
    
    @Override
    public String subscribe(final Runnable onChange) {
        String id = UUID.randomUUID().toString();
        changeWatchers.put(id, onChange);
        return id;
    }

    @Override
    public void unsubscribe(final String id) {
        changeWatchers.remove(id);
    }
}
