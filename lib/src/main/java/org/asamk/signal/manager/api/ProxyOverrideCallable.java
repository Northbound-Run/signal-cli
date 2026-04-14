package org.asamk.signal.manager.api;

/**
 * Callable variant used by {@link org.asamk.signal.manager.Manager#withProxyOverride}
 * that permits throwing arbitrary checked exceptions so command handlers can bubble
 * IO / user errors without wrapping them.
 */
@FunctionalInterface
public interface ProxyOverrideCallable<T> {

    T call() throws Exception;
}
