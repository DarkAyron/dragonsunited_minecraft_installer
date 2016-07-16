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

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.PasswordAuthentication;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 *
 * @author ayron
 */
public class Configuration {

    private static final String VERSIONURL = "http://tharos.shadowdrake.eu/installer_version";
    private static final String INSTALLERURL = "http://tharos.shadowdrake.eu/DUInstaller.jar";
    private static final String ARTIFACTSURL = "http://tharos.shadowdrake.eu/artifacts";

    private static final String VERSIONURLINT = "http://int.tharos.shadowdrake.fur/installer_version";
    private static final String INSTALLERURLINT = "http://int.tharos.shadowdrake.fur/DUInstaller.jar";
    private static final String ARTIFACTSURLINT = "http://int.tharos.shadowdrake.fur/artifacts";

    private static final String SERVER = "tharos.shadowdrake.eu";
    private static final String SERVERINT = "int.tharos.shadowdrake.fur";

    private final Preferences prefs = Preferences.userNodeForPackage(Configuration.class);

    private File installDir;
    private String httpProxy;
    private String ftpProxy;
    private PasswordAuthentication httpProxyCredentials;
    private PasswordAuthentication ftpProxyCredentials;
    private int ftpProxyMode;
    private boolean httpForFtp;
    private String laf;
    private boolean route;
    private boolean vpn;
    private PasswordAuthentication ftpCredentials;

    private File getDefaultDir() {
        String osName = System.getProperty("os.name").toUpperCase();
        String homeDir = System.getProperty("user.home", ".");
        if (osName.contains("LINUX") || osName.contains("SUNOS")) {
            return new File(homeDir, ".minecraft");
        } else if (osName.contains("MAC")) {
            return new File(homeDir, "Library/Application Support/minecraft");
        } else if (osName.contains("WINDOWS")) {
            String appData = System.getenv("APPDATA");
            return new File(appData, ".minecraft");
        } else {
            return new File(homeDir, "minecraft");
        }
    }

    public Configuration() {
        Enumeration<NetworkInterface> interfaces;
        installDir = new File(prefs.get("installDir", getDefaultDir().getAbsolutePath()));
        httpProxy = prefs.get("httpProxy", "");
        ftpProxy = prefs.get("ftpProxy", "");
        httpProxyCredentials = new PasswordAuthentication(prefs.get("httpProxyUser", ""), prefs.get("httpProxyPass", "").toCharArray());
        ftpProxyCredentials = new PasswordAuthentication(prefs.get("ftpProxyUser", ""), prefs.get("ftpProxyPass", "").toCharArray());
        ftpProxyMode = prefs.getInt("ftpProxyMode", 0);
        httpForFtp = prefs.getBoolean("httpForFtp", true);
        laf = prefs.get("laf", "Raven");
        route = false;
        vpn = false;
        ftpCredentials = new PasswordAuthentication(prefs.get("ftpUser", ""), prefs.get("ftpPass", "").toCharArray());
        try {
            interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface current = interfaces.nextElement();
                if (!current.isUp() || current.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = current.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress currentInetAddr = addresses.nextElement();
                    byte[] currentAddr;
                    if (currentInetAddr.isLoopbackAddress() || !(currentInetAddr instanceof Inet4Address)) {
                        continue;
                    }
                    route = true;
                    currentAddr = currentInetAddr.getAddress();
                    if (((currentAddr[0] == 100) && ((currentAddr[1] & 0xe0) == 64))
                            || (currentAddr[0] == -84) && (currentAddr[1] == 19) && currentAddr[2] == -30) {
                        vpn = true;
                        Logger.getLogger(Configuration.class.getName()).log(Level.INFO, "VPN address detected. Using " + SERVERINT);
                    }
                }
            }
        } catch (SocketException ex) {
            Logger.getLogger(Configuration.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public boolean hasRoute() {
        return route;
    }

    public String getVersionURL() {
        return vpn ? VERSIONURLINT : VERSIONURL;
    }

    public String getInstallerURL() {
        return vpn ? INSTALLERURLINT : INSTALLERURL;
    }
    
    public String getArtifactsURL() {
        return vpn ? ARTIFACTSURLINT : ARTIFACTSURL;
    }

    public String getServerHostname() {
        return vpn ? SERVERINT : SERVER;
    }

    public void setInstallDir(File installDir) {
        this.installDir = installDir;
        prefs.put("installDir", installDir.getAbsolutePath());
    }

    public void setHttpProxy(String httpProxy) {
        this.httpProxy = httpProxy;
        prefs.put("httpProxy", httpProxy);
    }

    public void setFtpProxy(String ftpProxy) {
        this.ftpProxy = ftpProxy;
        prefs.put("ftpProxy", ftpProxy);
    }

    public void setHttpProxyCredentials(PasswordAuthentication httpProxyCredentials) {
        this.httpProxyCredentials = httpProxyCredentials;
        prefs.put("httpProxyUser", httpProxyCredentials.getUserName());
        prefs.put("httProxyPass", new String(httpProxyCredentials.getPassword()));
    }

    public void setFtpProxyCredentials(PasswordAuthentication ftpProxyCredentials) {
        this.ftpProxyCredentials = ftpProxyCredentials;
        prefs.put("ftpProxyUser", ftpProxyCredentials.getUserName());
        prefs.put("ftpProxyPass", new String(ftpProxyCredentials.getPassword()));
    }

    public void setHttpForFtp(boolean httpForFtp) {
        this.httpForFtp = httpForFtp;
        prefs.putBoolean("httpForFtp", httpForFtp);
    }

    public void setFtpProxyMode(int ftpProxyMode) {
        this.ftpProxyMode = ftpProxyMode;
        prefs.putInt("ftpProxyMode", ftpProxyMode);
    }

    public void setLaf(String laf) {
        this.laf = laf;
        prefs.put("laf", laf);
    }

    public void setFtpCredentials(PasswordAuthentication ftpCredentials) {
        this.ftpCredentials = ftpCredentials;
        prefs.put("ftpUser", ftpCredentials.getUserName());
        prefs.put("ftpPass", new String(ftpCredentials.getPassword()));
    }

    public File getInstallDir() {
        return installDir;
    }

    public String getHttpProxy() {
        return httpProxy;
    }

    public String getFtpProxy() {
        return ftpProxy;
    }

    public PasswordAuthentication getHttpProxyCredentials() {
        return httpProxyCredentials;
    }

    public PasswordAuthentication getFtpProxyCredentials() {
        return ftpProxyCredentials;
    }

    public int getFtpProxyMode() {
        return ftpProxyMode;
    }

    public boolean isHttpForFtp() {
        return httpForFtp;
    }

    public String getLaf() {
        return laf;
    }

    public PasswordAuthentication getFtpCredentials() {
        return ftpCredentials;
    }
}
