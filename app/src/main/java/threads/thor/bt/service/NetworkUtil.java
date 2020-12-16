/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
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

package threads.thor.bt.service;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import threads.LogUtils;
import threads.thor.bt.BtException;

/**
 * Provides useful network functions.
 *
 * @since 1.0
 */
public class NetworkUtil {
    private static final String TAG = NetworkUtil.class.getSimpleName();

    public static boolean hasIpv6() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                for (InterfaceAddress address : networkInterface.getInterfaceAddresses()) {
                    InetAddress ia = address.getAddress();
                    if (ia instanceof Inet6Address) {
                        if (!ia.isLinkLocalAddress() &&
                                !ia.isLoopbackAddress() &&
                                !ia.isSiteLocalAddress()) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return false;
    }

    /**
     * Get address for a local internet link.
     *
     * @since 1.0
     */
    public static InetAddress getInetAddressFromNetworkInterfaces() {
        InetAddress selectedAddress = null;
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            outer:
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (!inetAddress.isMulticastAddress() && !inetAddress.isLoopbackAddress()
                            && inetAddress.getAddress().length == 4) {
                        selectedAddress = inetAddress;
                        break outer;
                    }
                }
            }

        } catch (SocketException e) {
            throw new BtException("Failed to retrieve network address", e);
        }
        // explicitly returning a loopback address here instead of null;
        // otherwise we'll depend on how JDK classes handle this,
        // e.g. java/net/Socket.java:635
        return (selectedAddress == null) ? InetAddress.getLoopbackAddress() : selectedAddress;
    }
}
