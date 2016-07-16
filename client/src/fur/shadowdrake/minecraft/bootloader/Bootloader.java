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
package fur.shadowdrake.minecraft.bootloader;

import fur.shadowdrake.minecraft.Configuration;
import fur.shadowdrake.minecraft.LafList;
import fur.shadowdrake.minecraft.ProxyAddress;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 *
 * @author ayron
 */
public class Bootloader {

    private static JavaConsole logOutput;
    private static InitialConfig dialog;

    private static boolean checkInstall(File dir) {
        return new File(dir, "DUInstaller.jar").isFile();
    }

    private static boolean needUpdate(File versionFile, Configuration config,
            Proxy httpProxy) {
        URL u;
        HttpURLConnection connection;
        InputStream is;
        Scanner s;
        String remoteHash;
        String myHash;
        if (!versionFile.isFile()) {
            return true;
        }
        try {
            u = new URL(config.getVersionURL());
            connection = (HttpURLConnection) u.openConnection(httpProxy);
            connection.addRequestProperty("User-agent", "Minecraft Bootloader");
            connection.setUseCaches(false);
            connection.setDefaultUseCaches(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.connect();
            if (connection.getResponseCode() != 200) {
                connection.disconnect();
                return true;
            }
            is = connection.getInputStream();
            s = new Scanner(is).useDelimiter("\\A");
            remoteHash = s.hasNext() ? s.next() : "";
            is.close();
            connection.disconnect();
            myHash = getSha1Sum(versionFile);
            return !remoteHash.equals(myHash);
        } catch (MalformedURLException ex) {
            Logger.getLogger(Bootloader.class.getName()).log(Level.SEVERE, "needUpdate", ex);
        } catch (IOException ex) {
            Logger.getLogger(Bootloader.class.getName()).log(Level.WARNING, "needUpdate", ex);
        }
        return true;
    }

    private static String getSha1Sum(File file) {
        MessageDigest md;
        byte[] dataBytes = new byte[1024];
        byte[] mdbytes;
        StringBuilder sb;

        try {
            try (FileInputStream fis = new FileInputStream(file)) {
                md = MessageDigest.getInstance("SHA1");
                for (int nread = 0; (nread = fis.read(dataBytes)) != -1; md.update(dataBytes, 0, nread));
                mdbytes = md.digest();
                sb = new StringBuilder("");
                for (int i = 0; i < mdbytes.length; i++) {
                    sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16).substring(1));
                }
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(Bootloader.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(null, "Something went wrong.");
            System.exit(0);
        } catch (FileNotFoundException ex) {
            return null;
        } catch (IOException ex) {
            return null;
        }
        return null;
    }

    private static boolean getArtifacts(Configuration config, Proxy httpProxy) {
        URL u;
        HttpURLConnection connection;
        HttpURLConnection downloadConnection;
        byte[] buffer = new byte[4096];
        InputStream is;
        InputStream is2;
        FileOutputStream fos;
        Scanner s;
        URL artifactUrl;
        new File(config.getInstallDir(), "lib").mkdirs();

        try {
            System.out.println("Downloading artifacts...");
            u = new URL(config.getArtifactsURL());
            connection = (HttpURLConnection) u.openConnection(httpProxy);
            connection.addRequestProperty("User-agent", "Minecraft Bootloader");
            connection.setUseCaches(false);
            connection.setDefaultUseCaches(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.connect();
            if (connection.getResponseCode() != 200) {
                connection.disconnect();
                System.out.println("failed.");
                return false;
            }
            is = connection.getInputStream();
            s = new Scanner(is).useDelimiter("\n");
            while (s.hasNext()) {
                String artifact = s.next();
                artifactUrl = new URL("http://" + config.getServerHostname() + "/" + artifact);
                downloadConnection = (HttpURLConnection) artifactUrl.openConnection(httpProxy);
                downloadConnection.addRequestProperty("User-agent", "Minecraft Bootloader");
                downloadConnection.setUseCaches(false);
                downloadConnection.setDefaultUseCaches(false);
                downloadConnection.setConnectTimeout(10000);
                downloadConnection.setReadTimeout(10000);
                downloadConnection.connect();
                System.out.println(artifact);
                if (downloadConnection.getResponseCode() != 200) {
                    downloadConnection.disconnect();
                    connection.disconnect();
                    System.out.println("failed.");
                    return false;
                }
                is2 = downloadConnection.getInputStream();
                fos = new FileOutputStream(new File(new File(config.getInstallDir(), "lib"), artifactUrl.getFile()));
                while (true) {
                    int n = is2.read(buffer);
                    if (n < 0) {
                        break;
                    }
                    fos.write(buffer, 0, n);
                }
                fos.close();
                is2.close();
                downloadConnection.disconnect();
            }
            is.close();
            connection.disconnect();
        } catch (MalformedURLException ex) {
            Logger.getLogger(Bootloader.class.getName()).log(Level.SEVERE, "Artifacts", ex);
            return false;
        } catch (IOException ex) {
            return false;
        }
        return true;
    }

    private static boolean boot(Configuration config) {
        Proxy pHttpProxy = Proxy.NO_PROXY;
        Proxy pFtpProxy = Proxy.NO_PROXY;

        if (!config.getHttpProxy().isEmpty()) {
            try {
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    public PasswordAuthentication getPasswordAuthentication() {
                        if (getRequestorType() == Authenticator.RequestorType.PROXY) {
                            return config.getHttpProxyCredentials();
                        } else {
                            return super.getPasswordAuthentication();
                        }
                    }
                });
                ProxyAddress paddr = new ProxyAddress(config.getHttpProxy(), 3128);
                pHttpProxy = new Proxy(Proxy.Type.HTTP, paddr.getSockaddr());

            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(null, "Invalid http proxy address\nCheck your settings!");
                return false;
            }
        }
        if (!config.getFtpProxy().isEmpty()) {
            try {
                ProxyAddress paddr = new ProxyAddress(config.getFtpProxy(), 2121);
                pFtpProxy = new Proxy(Proxy.Type.HTTP, paddr.getSockaddr());
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(null, "Invalid ftp proxy address\nCheck your settings!");
                return false;
            }
        }
        if (!checkInstall(config.getInstallDir()) || needUpdate(new File(config.getInstallDir(), "DUInstaller.jar"), config, pHttpProxy)) {
            URL u;
            HttpURLConnection connection;
            InputStream is;
            FileOutputStream fos;
            byte[] buffer;

            System.out.println("Update needed.");
            try {
                u = new URL(config.getInstallerURL());
                connection = (HttpURLConnection) u.openConnection(pHttpProxy);
                connection.addRequestProperty("User-agent", "Minecraft Bootloader");
                connection.setUseCaches(false);
                connection.setDefaultUseCaches(false);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.connect();
                if (connection.getResponseCode() != 200) {
                    connection.disconnect();
                    JOptionPane.showMessageDialog(null, "Server returned " + connection.getResponseMessage() + "\nDownload failed.");
                    return false;
                }
                is = connection.getInputStream();
                fos = new FileOutputStream(new File(config.getInstallDir(), "DUInstaller.jar"));
                buffer = new byte[4096];
                System.out.println("Downloading Installer...");
                for (int n = is.read(buffer); n > 0; n = is.read(buffer)) {
                    fos.write(buffer, 0, n);
                }
                fos.close();
                is.close();
                connection.disconnect();
            } catch (FileNotFoundException ex) {
                JOptionPane.showMessageDialog(null, "Can't write launcher file.");
                return false;
            } catch (IOException ex) {
                Logger.getLogger(Bootloader.class.getName()).log(Level.SEVERE, null, ex);
                JOptionPane.showMessageDialog(null, "Download failed: " + ex.getMessage());
                return false;
            }
            if (!getArtifacts(config, pHttpProxy)) {
                return false;
            }
            System.out.println("done.");
        }
        return true;
    }

    private static void makeDialog(Configuration config) {
        java.awt.EventQueue.invokeLater(() -> {
            logOutput.setState(JFrame.ICONIFIED);
            dialog = new InitialConfig(new javax.swing.JFrame(), false, config);
            dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    System.exit(0);
                }
            });
            dialog.setActionListener((ActionEvent e) -> {
                new Thread(() -> {
                    if (dialog.getResult()) {
                        logOutput.setState(JFrame.NORMAL);
                        if (boot(config)) {
                            startInstaller(config);
                        } else {
                            dialog.setVisible(true);
                        }
                    } else {
                        System.exit(0);
                    }
                }, "Boot").start();
            });
            dialog.setVisible(true);
        });
    }

    private static void startInstaller(Configuration config) {
        if (dialog != null) {
            dialog.dispose();
        }
        EventQueue.invokeLater(() -> {
            try {
                URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{new File(config.getInstallDir(), "DUInstaller.jar").toURI().toURL()});
                Class Installer = urlClassLoader.loadClass("fur.shadowdrake.minecraft.InstallerWindow");
                Constructor constructor = Installer.getConstructor(new Class[]{Configuration.class});
                Object installer = constructor.newInstance(config);
                Method setVisible = Installer.getMethod("setVisible", new Class[]{boolean.class});
                setVisible.invoke(installer, true);
                logOutput.setState(JFrame.ICONIFIED);
            } catch (MalformedURLException | ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                Logger.getLogger(Bootloader.class.getName()).log(Level.SEVERE, "Start Installer", ex);
                logOutput.setRelease(true);
            }
        });
    }

    public static void main(String args[]) {

        String laf;
        final Object mutex = new Object();

        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);
        System.setProperty("insubstantial.logEDT", "false");
        
        logOutput = new JavaConsole();
        logOutput.setRelease(true);
        Configuration config = new Configuration();
        laf = config.getLaf();

        java.awt.EventQueue.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(new LafList().get(laf));
                SwingUtilities.updateComponentTreeUI(logOutput);
                logOutput.pack();

            } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | UnsupportedLookAndFeelException e) {
                Logger.getLogger(Bootloader.class
                        .getName()).log(Level.WARNING, null, e);
            }
            synchronized (mutex) {
                logOutput.setVisible(true);
                mutex.notifyAll();
            }
        });
        logOutput.setRelease(false);
        synchronized (mutex) {
            try {
                mutex.wait();
            } catch (InterruptedException ex) {
            }
        }
        if (!checkInstall(config.getInstallDir())) {
            makeDialog(config);
        } else if (boot(config)) {
            startInstaller(config);
        } else {
            makeDialog(config);
        }
    }
}
