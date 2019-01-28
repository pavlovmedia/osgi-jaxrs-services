package com.pavlovmedia.oss.jaxrs.publisher.impl;

import java.util.Objects;

import org.osgi.framework.ServiceReference;

/**
 * Holds a reference to a ServiceReference AND a JAX object
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
public class JaxReference {
    public final ServiceReference serivceReference;
    public final Object jaxObject;
    
    public Object getJaxObject() {
        return this.jaxObject;
    }
    
    public JaxReference(final ServiceReference serivceReference, final Object jaxObject) {
        Objects.requireNonNull(serivceReference);
        // We allow jaxObject to be null so that we can search the set this is in faster
        
        this.serivceReference = serivceReference;
        this.jaxObject = jaxObject;
    }
    
    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof JaxReference) {
            return this.serivceReference.equals(((JaxReference) obj).serivceReference);
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        return serivceReference.hashCode();
    }
}
