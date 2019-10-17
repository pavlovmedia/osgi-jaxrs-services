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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

/**
 * This class represents meta-data about objects that have been
 * handed over to JAX-RS. Note this a simplistic pre-parsing so
 * there may be things that JAX-RS may call 'conflicts' that it 
 * won't deal with. The point is to be a quick debugging 
 * reference.
 *
 * Note that has a similar structure to a DTO where all the properties
 * are final, and you can only construct this using a utility method,
 * in this case {@link #parseEndpoint(Object)}
 * 
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
public final class EndpointInfo {
    public final String path;
    public final List<String> acceptTypes;
    public final List<String> responseTypes;
    public final String verb;
    
    private EndpointInfo(final String path, final String verb, 
            final List<String> acceptTypes, final List<String> responseTypes) { 
        this.path = path;
        this.verb = verb;
        this.acceptTypes = Collections.unmodifiableList(acceptTypes);
        this.responseTypes = Collections.unmodifiableList(responseTypes);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("EndpointInfo: ");
        sb.append(String.format("path: %s, verb: %s", path, verb));
        if (!acceptTypes.isEmpty()) {
            String flat = acceptTypes.stream().reduce((t, u) -> t + "," + u).get();
            sb.append(String.format(", accept: [ %s ]", flat));
        }
        if (!responseTypes.isEmpty()) {
            String flat = responseTypes.stream().reduce((t, u) -> t + "," + u).get();
            sb.append(String.format(", respond: [ %s ]", flat));
        }
        
        return sb.toString();
    }
    
    /**
     * Takes a potential JAX-RS endpoint and turns it into a list of
     * endpoints represented inside the class.
     * 
     * @param target the Object we are parsing
     * @return a list of {@link EndpointInfo} objects
     */
    public static List<EndpointInfo> parseEndpoint(final Object target) {
        return parseEndpoint(target.getClass());
    }
    
    /**
     * Takes a potential JAX-RS endpoint and turns it into a list of
     * endpoints represented inside the class.
     * 
     * @param clazz the class of the endpoint to parse
     * @return a list of {@link EndpointInfo} objects
     */
    public static List<EndpointInfo> parseEndpoint(final Class<?> clazz) {
        Path pathAnnotation = clazz.getDeclaredAnnotation(Path.class);
        if (null == pathAnnotation) {
            return Collections.emptyList();
        }
        
        return Arrays.asList(clazz.getDeclaredMethods()).stream()
            .map(m -> methodToInfo(m, pathAnnotation.value()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }
    
    /**
     * A method that can take in a method and create an EndpointInfo from it.
     * 
     * @param target the method to parse
     * @param parentPath the path of the parent class to use if a new path annotation
     *   is not present
     * @return an {@link EndpointInfo} wrapped in an {@link Optional}, or {@link Optional#empty()}
     *   if this method is not a JAX-RS method
     */
    private static Optional<EndpointInfo> methodToInfo(final Method target, final String parentPath) {
        String verb;
        String path;
        ArrayList<String> acceptTypes = new ArrayList<>();
        ArrayList<String> responseTypes = new ArrayList<>();
        
        // We need a method first
        if (target.isAnnotationPresent(GET.class)) {
            verb = "GET";
        } else if (target.isAnnotationPresent(POST.class)) {
            verb = "POST";
        } else if (target.isAnnotationPresent(PUT.class)) {
            verb = "PUT";
        } else if (target.isAnnotationPresent(DELETE.class)) {
            verb = "DELETE";
        } else if (target.isAnnotationPresent(HEAD.class)) {
            verb = "HEAD";
        } else if (target.isAnnotationPresent(OPTIONS.class)) {
            verb = "OPTIONS";
        } else {
            return Optional.empty();
        }
        
        // We can have an additional path marker here
        if (target.isAnnotationPresent(Path.class)) {
            Path additional = target.getAnnotation(Path.class);
            path = String.format("%s/%s", parentPath, additional.value());
        } else {
            path = parentPath;
        }
        
        // Request and response types
        if (target.isAnnotationPresent(Produces.class)) {
            responseTypes.addAll(Arrays.asList(target.getAnnotation(Produces.class).value()));
        }
        
        if (target.isAnnotationPresent(Consumes.class)) {
            acceptTypes.addAll(Arrays.asList(target.getAnnotation(Consumes.class).value()));
        }
        
        return Optional.of(new EndpointInfo(path, verb, acceptTypes, responseTypes));
    }
    
}
