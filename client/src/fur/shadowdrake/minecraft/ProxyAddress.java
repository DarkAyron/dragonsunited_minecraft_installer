/*
 * Copyright (C) 2016 ayron
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fur.shadowdrake.minecraft;

import java.net.InetSocketAddress;

/**
 *
 * @author ayron
 */
public class ProxyAddress {
    private final InetSocketAddress sockaddr;
    
    public ProxyAddress(String address, int defaultPort) throws IllegalArgumentException {
        String[] parts = address.split(":");
        int port;
        try {
            port = Integer.parseInt(parts[1]);
        } catch (ArrayIndexOutOfBoundsException ex) {
            port = defaultPort;
        }
        sockaddr = new InetSocketAddress(parts[0], port);
    }

    public InetSocketAddress getSockaddr() {
        return sockaddr;
    }
}
