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
package com.pavlovmedia.oss.jaxrs.publisher.impl.swagger;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import javax.servlet.ServletConfig;
import javax.ws.rs.core.Application;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.http.HttpService;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerFactory;

import com.pavlovmedia.oss.jaxrs.publisher.api.Publisher;
import io.swagger.annotations.Api;
import io.swagger.config.SwaggerConfig;
import io.swagger.jaxrs.config.DefaultJaxrsScanner;
import io.swagger.jaxrs.config.SwaggerScannerLocator;
import io.swagger.jaxrs.listing.ApiListingResource;
import io.swagger.jaxrs.listing.SwaggerSerializers;
import io.swagger.models.Contact;
import io.swagger.models.Info;
import io.swagger.models.License;
import io.swagger.models.Swagger;

/**
 * This is a service that sets up all the plumbing needed to serve
 * swagger documents from JAX-RS.
 * 
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
@Component(
    service = SwaggerEndpoint.class,
    property= {
        Publisher.SCAN_IGNORE + "=true",
        "com.eclipsesource.jaxrs.publish=false"
    })
public class SwaggerEndpoint extends DefaultJaxrsScanner implements SwaggerConfig {
    private static final String SCANNER_ID = "swagger.scanner.id.default";
    @Reference
    HttpService httpService;
    
    @Reference(policy=ReferencePolicy.DYNAMIC)
    volatile SwaggerConfiguration config;
    
    @Reference
    Publisher publisher;
    
    @Reference(service = LoggerFactory.class)
    Logger logger;
    
    private ServiceRegistration<?> apiResource;
    private ServiceRegistration<?> serializerResource;
    
    @Activate
    /**
     * Sets up our bundle, adding in the scanner and registering services
     * that JAX-RS + Swagger need
     * 
     * @param context the context for this bundle
     */
    protected void activator(final BundleContext context) {
        logger.info("SwaggerEndpoint activate");
        // Add our scanner as the default so it will just fire
        SwaggerScannerLocator.getInstance().putScanner(SCANNER_ID, this);
        
        serializerResource = context.registerService(SwaggerSerializers.class.getName(), new SwaggerSerializers(), null);
        apiResource = context.registerService(ApiListingResource.class.getName(), new ApiListingResource(), null);
    }
    
    @Deactivate
    protected void deactivate() {
        // Clean up our scanner
        SwaggerScannerLocator.getInstance().putScanner(SCANNER_ID, new DefaultJaxrsScanner());
        
        apiResource.unregister();
        serializerResource.unregister();
    }
    
    @Override
    public Set<Class<?>> classesFromContext(final Application app, final ServletConfig sc) {
        HashSet<Class<?>> ret = new HashSet<>();
        publisher.getRawEndpoints().stream()
                .map(o -> o.getClass())
                .filter(c -> c.isAnnotationPresent(Api.class))
                .forEach(ret::add);
        publisher.getReaderListeners().forEach(ret::add);
        return ret;
    }

    @Override
    public Swagger configure(final Swagger swagger) {
        swagger.setBasePath(publisher.getPath());
        return merge(swagger);
    }

    @Override
    public String getFilterClass() {
        return "";
    }
    /**
     * This will merge up an existing swagger with the info from this
     * configuration.
     * 
     * @param swagger swagger to merge into
     * @return the same object with the configuration injected
     */
    public Swagger merge(final Swagger swagger) {
        Info info = swagger.getInfo();
        if (null == info) {
            info = new Info();
            swagger.info(info);
        }
        
        Contact contact = info.getContact();
        if (null == contact) {
            contact = new Contact();
            info.contact(contact);
        }
        
        License license = info.getLicense();
        if (null == license) {
            license = new License();
            info.license(license);
        }
        
        ifStringPresent(config.title, info::title);
        ifStringPresent(config.description, info::description);
        ifStringPresent(config.apiVersion, info::version);
        ifStringPresent(config.contactName, contact::name);
        ifStringPresent(config.contactUrl, contact::url);
        ifStringPresent(config.contactEmail, contact::email);
        ifStringPresent(config.licenseName, license::name);
        ifStringPresent(config.licenseUrl, license::url);

        return swagger;
    }
    
    private static void ifStringPresent(final String str, final Consumer<String> action) {
        if (null != str && !str.isEmpty()) {
            action.accept(str);
        }
    }
}
