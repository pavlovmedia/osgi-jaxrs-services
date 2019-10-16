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

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;

import com.pavlovmedia.oss.jaxrs.publisher.api.Publisher;
import com.pavlovmedia.oss.jaxrs.publisher.impl.config.SwaggerConfigurationConfig;

/**
 * This class represents the meta-data that can be served by
 * swagger.
 * 
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
@Component(immediate=true,
    property= {
        Publisher.SCAN_IGNORE + "=true",
        "com.eclipsesource.jaxrs.publish=false"
    },
    service= {
            SwaggerConfiguration.class
    })
@Designate(ocd = SwaggerConfigurationConfig.class)
public class SwaggerConfiguration {
    
    public String title;
    public String description;
    public String apiVersion;
    public String contactName;
    public String contactUrl;
    public String contactEmail;
    public String licenseName;
    public String licenseUrl;
    
    @Activate
    protected void activate(final SwaggerConfigurationConfig properties) {
        parse(properties);
    }
    
    @Modified
    protected void modified(final SwaggerConfigurationConfig properties) {
        parse(properties);
    }
    
    private void parse(final SwaggerConfigurationConfig properties) {
        title = properties.title();
        description = properties.description();
        apiVersion = properties.apiVersion();
        contactName = properties.contactName();
        contactUrl = properties.contactUrl();
        contactEmail = properties.contactEmail();
        licenseName = properties.licenseName();
        licenseUrl = properties.licenseUrl();
    }
}
