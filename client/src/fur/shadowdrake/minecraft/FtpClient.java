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

import fur.shadowdrake.minecraft.json.Pack;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ayron
 */
public final class FtpClient {

    private Socket controlSocket;
    private BufferedReader bisr;
    private PrintStream pos;
    private final LogOutput logOutput;
    private final Configuration config;
    private boolean passive;
    private final Proxy dataProxy;
    private volatile FtpDataChannel channel;
    private final Object mutex;
    private final Semaphore dataChannelSeamaphore;
    private volatile boolean waitingForResponse;
    private Thread commandThread;
    private volatile NetworkException theNetworkError;

    public static final int FTP_OK = 0;
    public static final int FTP_TIMEOUT = -1;
    public static final int FTP_FAIL = -2;
    public static final int FTP_NODATA = -3;
    public static final int FTP_FAIL_UNKNOWN = -4;
    public static final int FTP_DATA_TIMEOUT = -8;

    private int getResponseCode(String response) {
        try {
            int n = Integer.parseInt(response.substring(0, 3));
            return n;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    public FtpClient(Configuration config, LogOutput logOutput) {
        this.config = config;
        this.passive = false;
        this.waitingForResponse = false;
        this.logOutput = logOutput;
        this.mutex = new Object();
        this.dataChannelSeamaphore = new Semaphore(0);
        if (config.isHttpForFtp() && !config.getHttpProxy().isEmpty()) {
            dataProxy = new Proxy(Proxy.Type.HTTP, new ProxyAddress(config.getHttpProxy(), 3128).getSockaddr());
        } else {
            dataProxy = Proxy.NO_PROXY;
        }
    }

    public void connect() throws IOException {
        String response;
        Proxy ftpProxy;
        FtpCommandChannelReader cChannel;
        if (config.isHttpForFtp() && !config.getHttpProxy().isEmpty()) {
            ftpProxy = new Proxy(Proxy.Type.HTTP, new ProxyAddress(config.getHttpProxy(), 3128).getSockaddr());
            passive = true;
        } else {
            ftpProxy = Proxy.NO_PROXY;
        }
        controlSocket = new Socket(ftpProxy);
        controlSocket.connect(new InetSocketAddress(config.getServerHostname(), 21), 10000);
        logOutput.println("Connected to " + config.getServerHostname());
        cChannel = new FtpCommandChannelReader(controlSocket, logOutput);
        cChannel.start();
        bisr = new BufferedReader(cChannel);
        pos = new PrintStream(controlSocket.getOutputStream());
        response = bisr.readLine();
        if (getResponseCode(response) != 220) {
            logOutput.println("Bogus server response. Disconnecting.");
            bisr.close();
            controlSocket.close();
            throw new IOException("Bogus response.");
        }
        if (!config.getFtpCredentials().getUserName().isEmpty() && !config.isHttpForFtp() && !config.getFtpProxy().isEmpty()) {
            PasswordAuthentication credentials = config.getFtpProxyCredentials();
            response = chat("USER " + credentials.getUserName());
            if (getResponseCode(response) == 331) {
                logOutput.println("Bogus server response. Disconnecting.");
                bisr.close();
                controlSocket.close();
                throw new IOException("Bogus response.");
            }
            response = chat("PASS " + new String(credentials.getPassword()));
            switch (getResponseCode(response)) {
                case 530:
                    throw new IOException("Proxy login");
                case 230:
                    break;
                default:
                    logOutput.println("Bogus server response. Disconnecting.");
                    bisr.close();
                    controlSocket.close();
                    throw new IOException("Bogus response.");
            }
        }
    }

    private String chat(String command) throws IOException {
        String response;
        synchronized (mutex) {
            pos.print(command + "\r\n");
            if (command.startsWith("PASS")) {
                logOutput.println("PASS *****");
            } else {
                logOutput.println(command);
            }
            pos.flush();
            response = bisr.readLine();
            return response;
        }
    }

    private InetSocketAddress convertAddressPasv(String addr) {
        int start = addr.indexOf('(');
        int end = addr.indexOf(')');
        String s = addr.substring(start + 1, end);
        String[] numbers = s.split(",");

        try {
            InetAddress inetAddr;
            int port;

            inetAddr = InetAddress.getByName(numbers[0] + '.' + numbers[1] + '.' + numbers[2] + '.' + numbers[3]);
            port = Integer.parseInt(numbers[4]) * 256 + Integer.parseInt(numbers[5]);
            return new InetSocketAddress(inetAddr, port);
        } catch (UnknownHostException ex) {
            Logger.getLogger(FtpClient.class.getName()).log(Level.SEVERE, "convertAddressPasv", ex);
            return null;
        }
    }

    private String convertAddressPort(int port) {
        StringBuilder sb;
        int port1;
        int port2;

        sb = new StringBuilder(controlSocket.getLocalAddress().getHostAddress().replace('.', ','));
        port1 = Math.floorDiv(port, 256);
        port2 = port - port1 * 256;
        sb.append(",").append(String.valueOf(port1)).append(",").append(String.valueOf(port2));
        return sb.toString();
    }

    private int getLength(String str) {
        String numBytes = str.substring(str.indexOf("(") + 1, str.indexOf(" bytes"));
        int n;

        try {
            n = Integer.parseInt(numBytes);
        } catch (NumberFormatException ex) {
            return 0;
        }

        return n;
    }

    public boolean login(PasswordAuthentication credentials) throws IOException {
        int response;
        if (!config.isHttpForFtp() && !config.getFtpProxy().isEmpty()) {
            String connectCmd;
            passive = true;
            switch (config.getFtpProxyMode()) {
                case 0:
                    connectCmd = "USER " + credentials.getUserName() + "@" + config.getServerHostname();
                    break;
                case 1:
                    connectCmd = "SITE " + config.getServerHostname();
                    break;
                case 2:
                    connectCmd = "OPEN " + config.getServerHostname();
                    break;
                default:
                    connectCmd = "USER " + credentials.getUserName() + "@" + config.getServerHostname();
            }
            response = getResponseCode(chat(connectCmd));
            if (response == 421) {
                bisr.close();
                pos.close();
                controlSocket.close();
                throw new IOException("Timeout");
            } else if (response != 220) {
                logOutput.println("Ooops. Disconnecting.");
                bisr.close();
                controlSocket.close();
                throw new IOException("Bogus response.");
            }
        }
        response = getResponseCode(chat("USER " + credentials.getUserName()));
        switch (response) {
            case 421:
                bisr.close();
                pos.close();
                controlSocket.close();
                throw new IOException("Timeout");
            case 331:
                break;
            default:
                logOutput.println("Bogus server response. Disconnecting.");
                bisr.close();
                pos.close();
                controlSocket.close();
                throw new IOException("Bogus response.");
        }
        response = getResponseCode(chat("PASS " + new String(credentials.getPassword())));
        switch (response) {
            case 503:
            case 530:
                return false;
            case 230:
                return true;
            default:
                logOutput.println("Bogus server response. Disconnecting.");
                bisr.close();
                controlSocket.close();
                throw new IOException("Bogus response.");
        }
    }

    public int cwd(String name) {
        String result;
        try {
            result = chat("CWD " + name);
            switch (getResponseCode(result)) {
                case 421:
                    return FTP_TIMEOUT;
                case 250:
                    return FTP_OK;
                case 550:
                    return FTP_FAIL;
                default:
                    return FTP_FAIL;
            }
        } catch (IOException ex) {
            return FTP_FAIL_UNKNOWN;
        }
    }

    public int retr(String filename, ActionListener complete) throws NetworkException {
        String result;
        try {
            waitingForResponse = true;
            result = chat("RETR " + filename);
            waitingForResponse = false;
            switch (getResponseCode(result)) {
                case 421:
                    return FTP_TIMEOUT;
                case 150:
                    new WaitForComplete(complete).start();
                    return getLength(result);
                case 425:
                    return FTP_NODATA;
                case 550:
                    return FTP_FAIL;
                default:
                    return FTP_FAIL;
            }
        } catch (IOException ex) {
            if (theNetworkError != null) {
                Logger.getLogger(FtpClient.class.getName()).log(Level.SEVERE, "Interrupt", ex);
                throw theNetworkError;
            }
            return FTP_FAIL_UNKNOWN;
        }
    }

    public int stor(String filename, ActionListener complete) throws NetworkException {
        String result;
        try {
            waitingForResponse = true;
            result = chat("STOR " + filename);
            waitingForResponse = false;
            switch (getResponseCode(result)) {
                case 421:
                    return FTP_TIMEOUT;
                case 150:
                    new WaitForComplete(complete).start();
                    return FTP_OK;
                case 425:
                    return FTP_NODATA;
                case 553:
                    return FTP_FAIL;
                default:
                    return FTP_FAIL;
            }
        } catch (IOException ex) {
            if (theNetworkError != null) {
                Logger.getLogger(FtpClient.class.getName()).log(Level.SEVERE, "Interrupt", ex);
                throw theNetworkError;
            }
            return FTP_FAIL_UNKNOWN;
        }
    }

    public int uins(Pack modpack, ActionListener complete) throws NetworkException {
        String result;
        try {
            waitingForResponse = true;
            result = chat("SITE UINS " + modpack.name + " " + modpack.version);
            waitingForResponse = false;
            switch (getResponseCode(result)) {
                case 421:
                    return FTP_TIMEOUT;
                case 150:
                    new WaitForComplete(complete).start();
                    return FTP_OK;
                case 425:
                    return FTP_NODATA;
                case 550:
                    return FTP_FAIL;
                default:
                    return FTP_FAIL;
            }
        } catch (IOException ex) {
            if (theNetworkError != null) {
                Logger.getLogger(FtpClient.class.getName()).log(Level.SEVERE, "Interrupt", ex);
                throw theNetworkError;
            }
            return FTP_FAIL_UNKNOWN;
        }
    }

    public int openDataChannel(ActionListener listener) {
        String response;
        this.commandThread = Thread.currentThread();
        if (channel != null) {
            if (passive) {
                listener.actionPerformed(new ActionEvent(channel.dataSocket, FTP_OK, "open"));
            } else {
                channel.setActionListener(listener);
            }
            return FTP_OK;
        }

        try {
            if (passive) {
                response = chat("PASV");
                switch (getResponseCode(response)) {
                    case 421:
                        return FTP_TIMEOUT;
                    case 227:
                        channel = new FtpDataChannel(convertAddressPasv(response), false, listener, dataProxy);
                        channel.start();
                        return FTP_OK;
                    default:
                        return FTP_FAIL_UNKNOWN;
                }
            } else {
                channel = new FtpDataChannel((InetSocketAddress) controlSocket.getRemoteSocketAddress(), true, listener, dataProxy);
                channel.start();
                response = chat("PORT " + convertAddressPort(channel.getSockAddress().getPort()));
                switch (getResponseCode(response)) {
                    case 421:
                        channel.close();
                        return FTP_TIMEOUT;
                    case 200:
                        return FTP_OK;
                    case 500:
                        /* we are behind a NAT and should try passive */
                        passive = true;
                        try {
                            channel.close();
                        } catch (NullPointerException ex) {
                        } finally {
                            channel = null;
                        }
                        return openDataChannel(listener);
                    default:
                        return FTP_FAIL_UNKNOWN;
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(FtpClient.class.getName()).log(Level.SEVERE, "OpenDataChannel", ex);
            return -1;
        }

    }

    public String getLatestVersion(String modpack) throws IOException {
        String response;
        response = chat("SITE VERS " + modpack);
        switch (getResponseCode(response)) {
            case 216:
                return response.substring(4);
            default:
                return null;
        }
    }

    public void close() {
        try {
            pos.println("QUIT");
            logOutput.println("QUIT");
            pos.flush();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
            }
            controlSocket.close();
        } catch (IOException ex) {
            Logger.getLogger(FtpClient.class.getName()).log(Level.WARNING, "close", ex);
        }
    }

    public void closeDataChannel() {
        if (channel != null) {
            try {
                channel.close();
            } catch (NullPointerException ex) {
            } finally {
                channel = null;
            }
        }
    }

    public void abandonDataChannel() {
        if (channel != null) {
            channel.setActionListener((ActionEvent e) -> {
            });
        }
    }

    private class FtpDataChannel extends Thread {

        private final InetSocketAddress destination;
        private ServerSocket listenSocket;
        private Socket dataSocket;
        private final boolean isActive;
        private ActionListener listener;

        public FtpDataChannel(InetSocketAddress destination, boolean isActive,
                ActionListener listener, Proxy proxy) throws IOException {
            super("FTP Data Channel");
            if (isActive) {
                listenSocket = new ServerSocket();
                listenSocket.bind(null);
            } else {
                dataSocket = new Socket(proxy);
            }
            this.destination = destination;
            this.isActive = isActive;
            this.listener = listener;
        }

        public void setActionListener(ActionListener listener) {
            this.listener = listener;
        }

        @Override
        public void run() {
            if (isActive) {
                try {
                    listenSocket.setSoTimeout(10000);
                    dataSocket = listenSocket.accept();
                    listenSocket.close();
                } catch (SocketException ex) {
                } catch (SocketTimeoutException ex) {
                    Logger.getLogger(FtpClient.class.getName()).log(Level.WARNING, "FtpDataChannel, active", ex);
                    try {
                        listenSocket.close();
                    } catch (IOException ex1) {
                    }
                    channel = null;
                    if (waitingForResponse) {
                        theNetworkError = new NetworkException("Data channel timed out while waiting for server response.");
                        commandThread.interrupt();
                    }
                    listener.actionPerformed(new ActionEvent(ex, FTP_DATA_TIMEOUT, "open"));
                } catch (IOException ex) {
                    Logger.getLogger(FtpClient.class.getName()).log(Level.SEVERE, "FtpDataChannel, active", ex);
                    listener.actionPerformed(new ActionEvent(ex, FTP_FAIL_UNKNOWN, "open"));
                }
            } else {
                try {
                    dataSocket.connect(destination, 10000);
                    dataChannelSeamaphore.acquire();
                } catch (SocketTimeoutException ex) {
                    channel = null;
                    Logger.getLogger(FtpClient.class.getName()).log(Level.WARNING, "FtpDataChannel, passive", ex);
                    if (waitingForResponse) {
                        theNetworkError = new NetworkException("Data channel timed out while waiting for server response.");
                        commandThread.interrupt();
                    }
                    listener.actionPerformed(new ActionEvent(ex, FTP_DATA_TIMEOUT, "open"));
                } catch (IOException | InterruptedException ex) {
                    channel = null;
                    Logger.getLogger(FtpClient.class.getName()).log(Level.SEVERE, "FtpDataChannel, passive", ex);
                    listener.actionPerformed(new ActionEvent(ex, FTP_FAIL_UNKNOWN, "open"));
                }

            }
            try {
                listener.actionPerformed(new ActionEvent(dataSocket, FTP_OK, "open"));
            } catch (IllegalArgumentException ex) {
            }
        }

        public void close() {
            try {
                listenSocket.close();
                dataSocket.close();
            } catch (IOException e) {
            }
        }

        public int getPort() {
            if (isActive) {
                return listenSocket.getLocalPort();
            } else {
                return dataSocket.getLocalPort();
            }
        }

        public byte[] getAddress() {
            if (isActive) {
                return ((InetSocketAddress) listenSocket.getLocalSocketAddress()).getAddress().getAddress();
            } else {
                return dataSocket.getLocalAddress().getAddress();
            }
        }

        public InetSocketAddress getSockAddress() {
            if (isActive) {
                return (InetSocketAddress) listenSocket.getLocalSocketAddress();
            } else {
                return (InetSocketAddress) dataSocket.getLocalSocketAddress();
            }
        }
    }

    private class WaitForComplete extends Thread {

        private final ActionListener listener;

        public WaitForComplete(ActionListener listener) {
            this.listener = listener;
            if (passive) {
                dataChannelSeamaphore.release();
            }
        }

        @Override
        public void run() {
            String response;
            try {
                response = bisr.readLine();
                switch (getResponseCode(response)) {
                    case 226:
                        listener.actionPerformed(new ActionEvent("FTP", FTP_OK, "Transfer", 226));
                        break;
                    default:
                        listener.actionPerformed(new ActionEvent("FTP", FTP_FAIL, "Transfer", getResponseCode(response)));
                }
            } catch (IOException ex) {
                Logger.getLogger(FtpClient.class.getName()).log(Level.SEVERE, "WaitForComplete", ex);
                listener.actionPerformed(new ActionEvent("FTP", FTP_FAIL_UNKNOWN, "Transfer"));
            }
        }
    }
}
