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
package com.pavlovmedia.oss.jaxrs.webconsole;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.webconsole.AbstractWebConsolePlugin;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerFactory;
import com.pavlovmedia.oss.jaxrs.publisher.api.EndpointInfo;
import com.pavlovmedia.oss.jaxrs.publisher.api.Publisher;

/**
 * This is a webconsole module that works with Apache Felix to display
 * state about the JAX-RS system.
 *
 * Note that this may work in other OSGi implementations, but has not been
 * tested.
 * 
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
@Component(
        service = javax.servlet.Servlet.class,
        property= {
                Publisher.SCAN_IGNORE + "=true",
                "felix.webconsole.label=JAXRS"
        })
public class JaxrsConsole extends AbstractWebConsolePlugin {
    private static final long serialVersionUID = -8881711830329491641L;
    private static final String PAGE_ROW_FORMAT = "<tr class=\"%s ui-state-default\"><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>";
    private static final String PROVIDER_ROW_FORMAT = "<tr class=\"%s ui-state-default\"><td>%s</td><td>%s</td></tr>";
    private static final String FEATURE_ROW_FORMAT = "<tr class=\"%s ui-state-default\"><td>%s</td></tr>";
    public static final String LABEL = "JAXRS";
    public static final String TITLE = "JAX-RS";

    @Reference
    Publisher publisher;
    
    @Override
    public String getLabel() {
        return LABEL;
    }
    
    @Reference(service = LoggerFactory.class)
    Logger logger;
    
    @Override
    public String getTitle() {
        return TITLE;
    }

    private AtomicBoolean even = new AtomicBoolean();
    
    @Override
    protected void renderContent(final HttpServletRequest req, final HttpServletResponse res)
            throws ServletException, IOException {
        PrintWriter pw = res.getWriter();
        renderPageSet(pw);
        even.set(false);
        renderProviderSet(pw);
        even.set(false);
        renderFeatureSet(pw);
        even.set(false);
        renderReaderListenerSet(pw);
    }
    
    private void renderProviderSet(final PrintWriter pw) {
        pw.println("<br/><p class=\"statline ui-state-highlight\">JAX-RS Providers:</p>");
        pw.println("<table class=\"nicetable\"><thead><tr><th class=\"header\">Class</th><th class=\"header\">Interfaces</th></tr></thead>");
        publisher.getProviders().forEach(provider -> {
            String interfaces = Arrays.asList(provider.getClass().getInterfaces()).stream()
                    .map(Class::getName)
                    .reduce((x,y) -> x+", "+y).get();
            pw.println(String.format(PROVIDER_ROW_FORMAT, rowClass(), provider.getClass().getName(),
                    interfaces));
        });
        pw.println("</table>");
    }
    
    private void renderFeatureSet(final PrintWriter pw) {
        pw.println("<br/><p class=\"statline ui-state-highlight\">JAX-RS Features:</p>");
        pw.println("<table class=\"nicetable\"><thead><tr><th class=\"header\">Class</th></tr></thead>");
        publisher.getFeatures().forEach(feature -> {
            pw.println(String.format(FEATURE_ROW_FORMAT, rowClass(), feature.getClass().getName()));
        });
        pw.println("</table>");
    }
    
    private void renderReaderListenerSet(final PrintWriter pw) {
        pw.println("<br/><p class=\"statline ui-state-highlight\">Swagger ReaderListeners:</p>");
        pw.println("<table class=\"nicetable\"><thead><tr><th class=\"header\">Class</th></tr></thead>");
        publisher.getReaderListeners().forEach(reader -> {
            pw.println(String.format(FEATURE_ROW_FORMAT, rowClass(), reader.getName()));
        });
        pw.println("</table>");
    }
    
    private void renderPageSet(final PrintWriter pw) {
        pw.println("<br/><p class=\"statline ui-state-highlight\">JAX-RS Pages:</p>");
        pw.println("<table class=\"nicetable\"><thead><tr><th class=\"header\">Class</th><th class=\"header\">Path</th>"
                +"<th class=\"header\">Verb</th><th class=\"header\">Accept Types</th><th class=\"header\">Response Types</th></tr></thead>");
        publisher.getEndpoints().forEach(tableRowGenerator(pw));
        pw.println("</table>");
    }
    
    BiConsumer<String,List<EndpointInfo>> tableRowGenerator(final PrintWriter writer) {
        return (className, entries) -> {
            entries.forEach(tableEndpointGenerator(writer, className));
        };
    }
    
    private Consumer<EndpointInfo> tableEndpointGenerator(final PrintWriter writer, final String className) {
        return endpoint -> {
            writer.println(String.format(PAGE_ROW_FORMAT, rowClass(), className, endpoint.path, endpoint.verb, contentListToString(endpoint.acceptTypes), 
                    contentListToString(endpoint.responseTypes)));
        };
    }
    
    private static String contentListToString(final List<String> contentList) {
        StringBuilder sb = new StringBuilder("[ ");
        if (null != contentList && !contentList.isEmpty()) {
          sb.append(contentList.stream()
                  .reduce((t, u) -> t + "," + u)
                  .get());
        }
        sb.append(" ]");
        return sb.toString();
    }
    
    private String rowClass() {
        if (even.get()) {
            even.set(false);
            return "even";
        } else {
            even.set(true);
            return "odd";
        }
    }
}
