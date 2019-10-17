package com.pavlovmedia.oss.jaxrs.publisher.impl.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name="SwaggerConfigurationConfig", description="Configuration for SwaggerConfiguration")
public @interface SwaggerConfigurationConfig {
    @AttributeDefinition(name="title")
    String title() default "JAX-RS Project";
    
    @AttributeDefinition(name="description")
    String description();
    
    @AttributeDefinition(name="apiVersion", description="The version of this api")
    String apiVersion() default "1.0";
    
    @AttributeDefinition(name="contactName")
    String contactName();
    
    @AttributeDefinition(name="contactUrl")
    String contactUrl();
    
    @AttributeDefinition(name="contactEmail")
    String contactEmail();
    
    @AttributeDefinition(name="licenseName", description="Something like 'Apache 2.0'")
    String licenseName();
    
    @AttributeDefinition(name="licenseUrl")
    String licenseUrl();
}