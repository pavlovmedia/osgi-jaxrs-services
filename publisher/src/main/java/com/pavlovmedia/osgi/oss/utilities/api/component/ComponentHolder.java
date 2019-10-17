/*
 * Copyright 2016 Pavlov Media
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
package com.pavlovmedia.osgi.oss.utilities.api.component;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Hashtable;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.osgi.service.component.ComponentFactory;
import org.osgi.service.component.ComponentInstance;

import com.pavlovmedia.osgi.oss.utilities.api.functional.ExceptionConsumer;
import com.pavlovmedia.osgi.oss.utilities.api.functional.ExceptionFunction;

/**
 * This is a class that helps simplify how we deal with component service
 * bindings and how we call them.
 * 
 * This implementation is for a 1-1 case which is the general case. You could
 * however, have multiple instances of this using the same factory to track
 * different services with different lifecycles.
 * 
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 * @param T Generic Type
 */
public class ComponentHolder<T> implements AutoCloseable {
    protected Optional<ComponentFactory<?>> factory = Optional.empty();
    protected Optional<ComponentInstance<?>> instance = Optional.empty();
    protected Optional<T> actual = Optional.empty();
    
    /**
     * This sets the inner factory instance that is used to generate
     * a component. It can only be called once before we close this
     * holder.
     * 
     * @param factory - pass a Component Factor to be set 
     */
    public void setFactory(final ComponentFactory<?> factory) {
        if (this.factory.isPresent()) {
          throw new IllegalStateException("There is already a factory set");
        }
        
        this.factory = Optional.of(factory);
    }
    
    /**
     * This will do all the work of provisioning a new instance using
     * the provided properties and will return true if it is successful
     * @param properties - pass a properties Map
     * @return boolean
     */
    @SuppressWarnings("unchecked")
    public boolean provision(final Map<String,Object> properties) {
        if (!factory.isPresent()) {
            throw new IllegalStateException("No factory is set");
        }
        Hashtable<String,Object> props = new Hashtable<>(properties);
        instance = Optional.ofNullable(factory.get().newInstance(props));
        if (instance.isPresent()) {
            actual = Optional.ofNullable((T) instance.get().getInstance());
            return true;
        }
        return false;
    }
    
    /**
     * This will execute an action with an response against the
     * instance.
     * 
     * @param action It is a function with T Input to apply 
     * @param <R> Return from apply
     * @return R the function result
     */
    public <R> R withService(final Function<T,R> action) {
        if (actual.isPresent()) {
            return action.apply(actual.get());
        }
        throw new IllegalStateException("No service is provisioned");
    }
    
    /**
     * This method is to execute a function against this instance that can throw an exception.
     * @param action the action to execute against this component instance
     * @param <R> Return from apply
     * @param <E> Exception that can be thrown
     * @return whatever the action returns
     * @throws E if the action throws an exception
     */
    public <R,E extends Exception> R withExceptionService(final ExceptionFunction<T,R,E> action) throws E {
        if (actual.isPresent()) {
            return action.apply(actual.get());
        }
        throw new IllegalStateException("No service is provisioned");
    }
    
    /**
     * This method is to execute an action with no response against this
     * instance
     * @param action action to execute against this component instance
     */
    public void againstService(final Consumer<T> action) {
        if (actual.isPresent()) {
            action.accept(actual.get());
        } else {
            throw new IllegalStateException("No service is provisioned");
        }
    }
    
    /**
     * This method is to execute an action against this instance with no 
     * response but may throw an exception
     * @param action action to execute against this component instance
     * @param <E> Exception that can be thrown
     * @throws E if the action throws an exception
     */
    public <E extends Exception> void againstExceptionService(final ExceptionConsumer<T, E> action) throws E {
        if (actual.isPresent()) {
            action.consume(actual.get());
        } else {
            throw new IllegalStateException("No service is provisioned");
        }
    }
    
    @SuppressWarnings("unchecked")
    public T getProxy(final Class<T> clazz) {
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), 
                new Class<?>[]{ clazz }, new ServiceHandler());
    }
    
    /**
     * This will do all the cleanup of this holder allowing it be be
     * reused.
     */
    @Override
    public void close() {
        instance.ifPresent(ComponentInstance::dispose);
        factory = Optional.empty();
        instance = Optional.empty();
        actual = Optional.empty();
    }
    
    /**
     * This is an inner proxy-class that can ensure
     * that we have an instance to work off of.
     * 
     * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
     *
     */
    private class ServiceHandler implements InvocationHandler {
        
        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
            if (actual.isPresent()) {
                return method.invoke(actual.get(), args);
            }
            throw new IllegalStateException("No service is provisioned");
        }
    }
}