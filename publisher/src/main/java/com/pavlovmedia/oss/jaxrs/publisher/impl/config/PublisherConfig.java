package com.pavlovmedia.oss.jaxrs.publisher.impl.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import com.pavlovmedia.oss.jaxrs.publisher.impl.JerseyPublisher;

// Define the OSGi Property Configuration DTO that will replace the OSGi Properties map passed into @Activate, @Deactivate and @Modified methods
// The methods in this interface replace the 'old' @Property's where metatype=true
@ObjectClassDefinition(
        // The name and description of the @ObjectClassDefinition define the name/description that show in the OSGi Console for the Component.
        name="PublisherConfig", 
        description="Configuration for Jersey Publisher"
)
public @interface PublisherConfig {
    // Each method marked with @AttributeDefinition will render the OSGi Configuration Manager web console as a field.
    // The _'s in the method names are transformed to . when the OSGi property names are generated.
    // Example: max_size -> max.size, user_name_default -> user.name.default
    @AttributeDefinition(name=JerseyPublisher.PATH, description = "Path to serve JAX-RS endpoints from")
    String path() default "/services";
}
