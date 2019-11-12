package com.pavlovmedia.oss.jaxrs.publisher.impl.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import com.pavlovmedia.oss.jaxrs.publisher.impl.JerseyPublisher;

/**
 * This is the configuration object for the publisher
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
@ObjectClassDefinition(
        // The name and description of the @ObjectClassDefinition define the name/description that show in the OSGi Console for the Component.
        name="PublisherConfig", 
        description="Configuration for Jersey Publisher"
)
public @interface PublisherConfig {
    @AttributeDefinition(name=JerseyPublisher.PATH, description = "Path to serve JAX-RS endpoints from")
    String path() default "/services";
}
