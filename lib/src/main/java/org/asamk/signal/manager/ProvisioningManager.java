package org.asamk.signal.manager;

import org.asamk.signal.manager.api.ProxyConfig;
import org.asamk.signal.manager.api.ProxyOverrideCallable;
import org.asamk.signal.manager.api.UserAlreadyExistsException;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeoutException;

public interface ProvisioningManager {

    URI getDeviceLinkUri() throws TimeoutException, IOException;

    String finishDeviceLink(String deviceName) throws IOException, TimeoutException, UserAlreadyExistsException;

    /**
     * Run {@code callable} with the effective proxy temporarily replaced by
     * {@code override}. Implementations may rebuild internal provisioning
     * sockets against the override and restore them when the callable
     * returns. If {@code override} is {@code null}, the callable runs with
     * the existing proxy.
     */
    <T> T withProxyOverride(ProxyConfig override, ProxyOverrideCallable<T> callable) throws Exception;
}
