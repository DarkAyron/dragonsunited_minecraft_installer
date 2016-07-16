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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fur.shadowdrake.minecraft.json.Addon;
import fur.shadowdrake.minecraft.json.AddonManifest;
import fur.shadowdrake.minecraft.json.InstalledPacks;
import fur.shadowdrake.minecraft.json.LauncherProfiles;
import fur.shadowdrake.minecraft.json.Manifest;
import fur.shadowdrake.minecraft.json.Pack;
import fur.shadowdrake.minecraft.json.PackDescription;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/**
 *
 * @author ayron
 */
public class InstallPanel extends javax.swing.JPanel {

    private final Configuration config;
    private InstalledPacks packList;
    private List<PackDescription> availablePacks;
    private HashMap<String, Manifest> manifest;
    private final FtpClient ftpClient;
    private final LogOutput log;
    private LauncherProfiles profiles;
    private AddonsPanel addonsPanel;
    private volatile boolean success;
    private volatile int result;
    private ActionListener completedListener;
    private volatile int downloadSize;
    private volatile ArrayList<String> downloadedFiles;
    private final InstallerWindow parentFrame;
    private String workingPack;
    private String workingDir;
    private boolean initialized;
    private boolean addonsOpenedOnce;

    /**
     * Creates new form InstallPanel
     *
     * @param parent
     * @param config
     * @param ftpClient
     * @param log
     */
    public InstallPanel(InstallerWindow parent, Configuration config, FtpClient ftpClient, LogOutput log) {
        this.parentFrame = parent;
        initComponents();
        this.config = config;
        this.ftpClient = ftpClient;
        this.log = log;
        this.initialized = false;
        this.addonsOpenedOnce = false;
    }

    public void prepareInstall(List<PackDescription> packs) {
        availablePacks = packs;
        String[] names;
        names = packs.stream().map(s -> s.name).toArray(s -> new String[s]);
        modpackChooser.setModel(new DefaultComboBoxModel<>(names));
        modpackChooserActionPerformed(null);
        packs.stream().forEach((PackDescription p) -> {
            Manifest mf = manifest.get(p.name);
            if (mf != null) {
                p.addons.forEach((Addon a) -> {
                    a.install = mf.containsKey(a.name);
                });
            }
        });
        force.setSelected(false);
    }

    public void initialize() throws NetworkException {
        File profileFile;
        Gson gson = new GsonBuilder().create();
        InstalledPacks packs = new InstalledPacks();
        try {
            packs = gson.fromJson(new FileReader(new File(config.getInstallDir(), "modpacks.json")), InstalledPacks.class);
        } catch (FileNotFoundException ex) {
            dirBox.setText(config.getInstallDir().getAbsolutePath());
            Pack detected = detectPack(config.getInstallDir());
            if (detected != null) {
                packs.add(detected);
                try (FileWriter fw = new FileWriter(new File(config.getInstallDir(), "modpacks.json"))) {
                    gson.toJson(packs, fw);
                } catch (IOException ex1) {
                    log.println("Writing modpacks file failed.");
                    Logger.getLogger(InstallPanel.class.getName()).log(Level.SEVERE, null, ex1);
                }

            }
        } finally {
            this.packList = packs;
        }
        profileFile = new File(config.getInstallDir(), "launcher_profiles.json");
        if (profileFile.isFile()) {
            try {
                profiles = gson.fromJson(new FileReader(profileFile), LauncherProfiles.class);
            } catch (FileNotFoundException ex) {
                log.println("Profiles file unreadable. It will be recreated from scratch.");
                profiles = new LauncherProfiles();
                profiles.profiles = new HashMap<>();
                packList.stream().forEach((p) -> {
                    addLauncherProfiles(p);
                });
            }
        } else {
            profiles = new LauncherProfiles();
            profiles.profiles = new HashMap<>();
            try (FileWriter fw = new FileWriter(profileFile)) {
                gson.toJson(profiles, fw);
            } catch (IOException ex) {
                log.println("Couldn't create launcher profiles.");
                Logger.getLogger(InstallPanel.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        manifest = new HashMap<>();
        packList.stream().forEach((Pack p) -> {
            try {
                Manifest mf = gson.fromJson(new FileReader(new File(p.directory, "manifest.json")), Manifest.class);
                manifest.put(p.name, mf);
            } catch (FileNotFoundException ex) {
                log.println("No addon manifest for " + p.name);
            }
        });
        initialized = true;
    }

    public void writeFiles() {
        Gson gson = new GsonBuilder().create();
        try (FileWriter fw = new FileWriter(new File(config.getInstallDir(), "modpacks.json"))) {
            gson.toJson(packList, fw);
        } catch (IOException ex1) {
            log.println("Writing modpacks file failed.");
            Logger.getLogger(InstallPanel.class.getName()).log(Level.SEVERE, null, ex1);
        }
        try (FileWriter fw = new FileWriter(new File(config.getInstallDir(), "launcher_profiles.json"))) {
            gson.toJson(profiles, fw);
        } catch (IOException ex1) {
            log.println("Writing launcher profile file failed.");
            Logger.getLogger(InstallPanel.class.getName()).log(Level.SEVERE, null, ex1);
        }

    }

    public void setCompletedListener(ActionListener completedListener) {
        this.completedListener = completedListener;
    }

    public List<Pack> getInstalledPacks() {
        return packList;
    }

    public JPanel getParameterPanel() {
        return parameterPanel;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public List<Pack> checkUpdate() throws IOException {
        List<Pack> unregisterMe = new ArrayList<>();
        List<Pack> updateNeeded = new ArrayList<>();
        log.setStatusText("Checking update");
        for (Pack p : packList) {
            boolean isOk;
            /* check if the modpack is still present */
            try (BufferedReader bfr = new BufferedReader(new FileReader(new File(p.directory, "modpack")))) {
                isOk = bfr.readLine().equals(p.name);
            } catch (IOException ex) {
                isOk = false;
            }
            if (!isOk) {
                log.println("Modpack " + p.name + " is gone. Unregistering it.");
                unregisterMe.add(p);
                continue;
            }
            String latestVersion = ftpClient.getLatestVersion(p.name);
            if ((latestVersion != null) && (!latestVersion.equals(p.version))) {
                updateNeeded.add(p);
            }
        }
        unregisterMe.stream().forEach((Pack t) -> {
            unregisterPack(t.name);
            removeLauncherProfiles(t);
        });
        return updateNeeded;
    }

    private Pack detectPack(File path) throws NetworkException {
        BufferedReader bis;
        String version;
        String modpack;

        try {
            bis = new BufferedReader(new FileReader(new File(path, "ver")));
            version = bis.readLine();
            bis.close();
        } catch (FileNotFoundException ex) {
            return null;
        } catch (IOException ex) {
            return null;
        }

        try {
            bis = new BufferedReader(new FileReader(new File(path, "modpack")));
            modpack = bis.readLine();
            bis.close();
        } catch (FileNotFoundException ex) {
            /* just for this version */
            if (registerRhaokarLightPack(path)) {
                modpack = "rhaokar_light";
            } else {
                return null;
            }
        } catch (IOException ex) {
            return null;
        }
        return new Pack(modpack, path.getAbsolutePath(), version);
    }

    @Deprecated
    private boolean registerRhaokarLightPack(File path) throws NetworkException {
        FileOutputStream fos;

        try {
            fos = new FileOutputStream(new File(path, "modpack"));
            fos.write("rhaokar_light".getBytes());
            fos.close();
        } catch (FileNotFoundException ex) {
            return false;
        } catch (IOException ex) {
            return false;
        }
        if (new File(path, "resourcepacks/01.zip").isFile()) {
            downloadFile("manifest.json");
        } else if (new File(path, "resourcepacks/Soatex_Custom.zip").isFile()) {
            try {
                EventQueue.invokeAndWait(() -> {
                    result = JOptionPane.showConfirmDialog(InstallPanel.this, "An old version of the graphics pack was detected. Do you want to keep it?\nIf you choose no, your selection in addons will be downloaded.", "Addons", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                });
            } catch (InterruptedException | InvocationTargetException ex) {
                Logger.getLogger(InstallPanel.class.getName()).log(Level.SEVERE, null, ex);
            }
            switch (result) {
                case JOptionPane.YES_OPTION:
                    downloadFile("manifest_old.json", "manifest.json");
                    break;
                default:
                    new File(path, "mods/ShadersModCore-v2.3.31-mc1.7.10-f.jar").delete();
                    try {
                        cleanDirectory(new File(path, "resourcepacks"));
                    } catch (IOException ex) {
                    }
                    try {
                        deleteDirectory(new File(path, "shaderpacks"));
                    } catch (IOException ex) {
                    }
                    downloadAddons();
            }
        }
        return true;
    }

    private void unregisterPack(String name) {
        List<Pack> removeMe = new ArrayList<>();
        packList.stream().filter((p) -> (p.name.equals(name))).forEach((p) -> {
            removeMe.add(p);
        });
        packList.removeAll(removeMe);
    }

    private void addLauncherProfiles(Pack pack) {
        Gson gson = new GsonBuilder().create();
        LauncherProfiles profile;
        try {
            profile = gson.fromJson(new FileReader(new File(pack.directory, "pack_profiles.json")), LauncherProfiles.class);
            profile.profiles.forEach((String t, LauncherProfiles.Profile u) -> {
                u.gameDir = workingDir;
            });
            profiles.profiles.putAll(profile.profiles);
            profiles.selectedProfile = profile.selectedProfile;
        } catch (FileNotFoundException ex) {
            Logger.getLogger(InstallPanel.class.getName()).log(Level.SEVERE, "addLauncherProfile", ex);
            log.println("Couldn't add profiles for " + pack.name + "\nPlease add it manually!");
        }
    }

    private void removeLauncherProfiles(Pack pack) {
        List<String> removeMe = new ArrayList<>();

        if ((profiles == null) || (profiles.profiles == null)) {
            return;
        }

        /* we search it by the path */
        try {
            profiles.profiles.values().stream().filter((LauncherProfiles.Profile p) -> p.gameDir.equals(pack.directory)).forEach((LauncherProfiles.Profile p) -> {
                removeMe.add(p.name);
            });
            removeMe.stream().forEach((String t) -> {
                profiles.profiles.remove(t);
            });
        } catch (NullPointerException ex) {
        }
    }

    public void begin(boolean first) {
        workingDir = dirBox.getText();
        workingPack = (String) modpackChooser.getSelectedItem();
        parentFrame.dropAllProceedListeners();
        new Thread(() -> {
            try {
                JTabbedPane tabPane;
                tabPane = (JTabbedPane) getParent();

                if (force.isSelected()) {
                    log.println("Cleaning up.");
                    cleanUp(new File(workingDir));
                    Manifest mf = manifest.get(workingPack);
                    if (mf != null) {
                        mf.clear();
                    }
                }

                Pack detected = detectPack(new File(workingDir));
                if ((detected == null) && checkDir(new File(workingDir), 2)) {
                    try {
                        EventQueue.invokeAndWait(() -> {
                            JOptionPane.showMessageDialog(parentFrame, "This directory contains an unknown modpack.\nChoose another directory or force reinstall.");
                        });
                    } catch (InterruptedException | InvocationTargetException ex) {
                    }
                    completedListener.actionPerformed(new ActionEvent(this, 2, "Install"));
                } else if (detected == null) {
                    try {
                        EventQueue.invokeAndWait(() -> {
                            tabPane.remove(this);
                        });
                    } catch (InterruptedException | InvocationTargetException ex) {
                    }

                    if (first && !addonsOpenedOnce) {
                        addonsPanel = new AddonsPanel(availablePacks.get(modpackChooser.getSelectedIndex()).addons.toArray(new Addon[0]));
                        tabPane.add("Addons", addonsPanel);
                        tabPane.setSelectedComponent(addonsPanel);
                        addonsOpenedOnce = true;
                        parentFrame.setAbortListener((ActionEvent e) -> {
                            tabPane.remove(addonsPanel);
                            tabPane.add("Install", this);
                            tabPane.setSelectedComponent(this);
                            parentFrame.setProceedListener((ActionEvent ev) -> {
                                begin(false);
                            });
                            parentFrame.setAbortListener((ActionEvent ev) -> {
                                tabPane.remove(this);
                                parentFrame.state = 0;
                                parentFrame.dropAllAbortListeners();
                                parentFrame.dropAllProceedListeners();
                            });
                        });
                        parentFrame.setProceedListener((ActionEvent e) -> {
                            tabPane.remove(addonsPanel);
                            boolean s[];
                            s = addonsPanel.getSelected();
                            for (int n = 0; n < s.length; n++) {
                                availablePacks.get(modpackChooser.getSelectedIndex()).addons.get(n).install = s[n];
                            }
                            begin(false);
                        });
                        return;
                    }

                    if (downloadPack() && downloadAddons() && doPostDownload()) {
                        log.println("Installation completed successful.");
                        log.setStatusText("Ok");
                        completedListener.actionPerformed(new ActionEvent(this, 1, "Install"));
                    } else {
                        log.println("Installation completed with errors.");
                        log.setStatusText("Error");
                        EventQueue.invokeLater(() -> {
                            JOptionPane.showMessageDialog(parentFrame, "Installation failed");
                        });
                        completedListener.actionPerformed(new ActionEvent(this, 0, "Install"));
                    }
                } else if (detected.name.equals(modpackChooser.getSelectedItem())) {
                    if (!isInstalled(detected)) {
                        try {
                            Gson gson = new GsonBuilder().create();
                            tabPane.remove(this);
                            packList.add(detected);

                            String latestVersion = ftpClient.getLatestVersion(detected.name);
                            if (!latestVersion.equals(detected.version)) {
                                updatePack(detected);
                            }

                            try (FileWriter fw = new FileWriter(new File(config.getInstallDir(), "modpacks.json"))) {
                                gson.toJson(packList, fw);
                            }
                            log.println("Installation completed successful.");
                            completedListener.actionPerformed(new ActionEvent(this, 1, "Install"));
                        } catch (IOException ex1) {
                            log.println("Installation failed.");
                            Logger.getLogger(InstallPanel.class.getName()).log(Level.SEVERE, null, ex1);
                        }
                    } else {
                        try {
                            EventQueue.invokeAndWait(() -> {
                                tabPane.remove(this);
                            });
                        } catch (InterruptedException | InvocationTargetException ex) {
                        }
                        if (downloadAddons()) {
                            log.println("Installation completed successful.");
                            log.setStatusText("Ok");
                            log.reset();
                            completedListener.actionPerformed(new ActionEvent(this, 1, "Install"));
                        } else {
                            log.println("Installation failed.");
                            EventQueue.invokeLater(() -> {
                                JOptionPane.showMessageDialog(parentFrame, "Installation failed");
                            });
                        }
                    }
                } else {
                    try {
                        EventQueue.invokeAndWait(() -> {
                            JOptionPane.showMessageDialog(parentFrame, "This directory contains modpack " + detected.name + ".\nSelect the correct pack or force reinstall.");
                        });
                    } catch (InterruptedException | InvocationTargetException ex) {
                    }
                    completedListener.actionPerformed(new ActionEvent(this, 2, "Install"));
                }
                writeFiles();
            } catch (NetworkException ex) {
                Logger.getLogger(InstallPanel.class.getName()).log(Level.SEVERE, null, ex);
                JOptionPane.showMessageDialog(parentFrame, ex.getMessage(), "Network error", JOptionPane.ERROR_MESSAGE);
                ftpClient.close();
                log.println("Timeout. Previous command wasn't received by the server. This is a network error. Please try again later.");
                log.setStatusText("Error");
            }
        }, "Installer").start();
    }

    private boolean isInstalled(Pack pack) {
        return packList.stream().anyMatch((p) -> (p.name.equals(pack.name)));
    }

    private boolean downloadPack() throws NetworkException {
        boolean b;
        PackDescription newPack = availablePacks.get(modpackChooser.getSelectedIndex());

        b = downloadArchive(newPack.archive);
        if (b) {
            Pack p = detectPack(new File(workingDir));
            if (p != null) {
                packList.removeIf((Pack t) -> t.directory.equals(p.directory));
                packList.add(p);
                removeLauncherProfiles(p);
                addLauncherProfiles(p);
            } else {
                log.println("Pack wasn't correctly downloaded.");
                b = false;
            }
        }
        log.reset();
        return b;
    }

    private boolean downloadArchive(String filename) throws NetworkException {
        final Semaphore semaphore1 = new Semaphore(0);
        final Semaphore semaphore2 = new Semaphore(0);
        success = false;
        log.setIndeterminate();
        while (true) {
            result = ftpClient.openDataChannel((ActionEvent e) -> {
                if (e.getID() == FtpClient.FTP_OK) {
                    try {
                        semaphore1.acquire();
                        InputStream is;
                        is = ((Socket) e.getSource()).getInputStream();
                        downloadedFiles = unTar(is, new File(workingDir));
                        success = true;
                    } catch (IOException ex) {
                        Logger.getLogger(InstallPanel.class.getName()).log(Level.SEVERE, "Download", ex);
                        log.println("Faild to save file.");
                        ftpClient.closeDataChannel();
                        success = false;
                    } catch (ArchiveException | InterruptedException ex) {
                        Logger.getLogger(InstallPanel.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            });

            switch (result) {
                case FtpClient.FTP_OK:
                    downloadSize = ftpClient.retr(filename, (ActionEvent e) -> {
                        ftpClient.closeDataChannel();
                        semaphore2.release();
                    });
                    if (downloadSize >= 0) {
                        if (downloadSize > 1048576) {
                            log.println("~" + Integer.toString(downloadSize / 1048576) + " MB");
                        } else if (downloadSize > 1024) {
                            log.println("~" + Integer.toString(downloadSize / 1024) + " kB");
                        }
                        log.reset();
                        log.showPercentage(true);
                        log.setMaximum(downloadSize);
                        semaphore1.release();
                        try {
                            semaphore2.acquire();
                        } catch (InterruptedException ex) {
                            return false;
                        }
                    } else {
                        switch (downloadSize) {
                            case FtpClient.FTP_NODATA:
                                log.println("Oops! Server's complaining about missing data channel, although I've opened it.");
                                ftpClient.abandonDataChannel();
                                return false;
                            default:
                                ftpClient.abandonDataChannel();
                                return false;
                        }
                    }
                    break;
                case FtpClient.FTP_TIMEOUT:
                    if (reconnect()) {
                        continue;
                    } else {
                        return false;
                    }
                default:
                    return false;
            }

            break;
        }
        return success;
    }

    private boolean downloadAddons() throws NetworkException {
        boolean isNew;
        Manifest mf;
        int n;
        ArrayList<Addon> installList = new ArrayList<>();
        ArrayList<String> removeList = new ArrayList<>();
        int modpackIndex = modpackChooser.getSelectedIndex();
        Gson gson = new GsonBuilder().create();

        log.setIndeterminate();
        isNew = !manifest.containsKey(workingPack);
        if (isNew) {
            mf = new Manifest();
            availablePacks.get(modpackIndex).addons.stream().filter((Addon t) -> t.install).forEach(installList::add);
        } else {
            mf = manifest.get(workingPack);
            availablePacks.get(modpackIndex).addons.stream().forEach((Addon a) -> {
                if (a.install && !mf.containsKey(a.name)) {
                    installList.add(a);
                } else if ((mf.containsKey(a.name)) && !a.install) {
                    removeList.add(a.name);
                }
            });
        }

        /* searching it in the list is better than relaying on the dirBox */
        String directory = null;
        for (Pack p : packList) {
            if (p.name.equals(workingPack)) {
                directory = p.directory;
                break;
            }
        }

        for (String t : removeList) {
            ArrayList<String> files = mf.get(t).files;
            files.sort(new PathComparator());
            for (String f : files) {
                new File(directory, f).delete();
            }
            mf.remove(t);
            log.println("Addon " + t + " removed.");
        }

        n = 0;
        for (Addon a : installList) {
            log.setIndeterminate();
            log.setStatusText("Installing Addon " + Integer.toString(++n) + "/" + installList.size());
            AddonManifest amf = new AddonManifest();
            amf.version = a.version;
            if (downloadArchive(workingPack + "/" + a.archive)) {
                amf.files = downloadedFiles;
                mf.put(a.name, amf);
                log.reset();
            } else {
                log.println("Addon " + a.name + " failed.");
                log.setStatusText("Error");
                log.reset();
                return false;
            }
        }

        if (isNew) {
            manifest.put(workingPack, mf);
        }

        try (FileWriter fw = new FileWriter(new File(directory, "manifest.json"))) {
            gson.toJson(mf, fw);
        } catch (IOException ex1) {
            log.println("Writing manifest file failed.");
            Logger.getLogger(InstallPanel.class.getName()).log(Level.SEVERE, null, ex1);
        }
        return true;
    }

    private boolean doPostDownload() throws NetworkException {
        boolean b;
        PrintStream originalOut = System.out;
        log.setIndeterminate();
        log.setStatusText("running post-download");
        System.setOut(new PrintStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                byte[] x = new byte[1];
                x[0] = (byte) b;
                log.print(new String(x));
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                log.print(new String(b, off, len));
            }
        }));
        try {
            URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{new File(workingDir).toURI().toURL()});
            Class postDownloadClass = urlClassLoader.loadClass("PostDownload");
            Constructor constructor = postDownloadClass.getConstructor();
            Object pd = constructor.newInstance();
            Method method = postDownloadClass.getMethod("invoke", new Class[]{String.class, String.class});
            b = (boolean) method.invoke(pd, config.getInstallDir().getAbsolutePath(), workingDir);
            if (b) {
                showReadMe();
            }
            return b;
        } catch (MalformedURLException | ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoClassDefFoundError ex) {
            Logger.getLogger(InstallPanel.class.getName()).log(Level.SEVERE, null, ex);
            log.println("Executing post-download failed.");
        } finally {
            System.setOut(originalOut);
            log.reset();
        }
        return false;
    }

    public boolean updatePack(Pack pack) throws NetworkException {
        int status;
        int n;
        String latestVersion;
        log.setIndeterminate();
        log.setStatusText("Updating " + pack.name);
        List<String> updateInstructions = fetchUpdateInstructions(pack);
        if (updateInstructions == null) {
            return false;
        }
        status = ftpClient.cwd(pack.name);
        switch (status) {
            case FtpClient.FTP_TIMEOUT:
                reconnect();
                return updatePack(pack);
            case FtpClient.FTP_OK:
                break;
            default:
                return false;
        }

        workingDir = pack.directory;
        workingPack = pack.name;
        n = 0;
        try {
            for (String s : updateInstructions) {
                log.setStatusText("Processing instruction " + Integer.toString(++n) + "/" + Integer.toString(updateInstructions.size()));
                log.setIndeterminate();
                if (s.isEmpty()) {
                    continue;
                }

                String filename;
                char operation;
                char filetype;
                boolean bresult;

                operation = s.charAt(0);
                filetype = s.charAt(1);
                filename = s.substring(2);

                File file = new File(pack.directory, filename);

                switch (operation) {
                    case '+':
                        switch (filetype) {
                            case 'f':
                                bresult = downloadFile(filename);
                                log.reset();
                                break;
                            case 'd':
                                bresult = file.exists() ? file.isDirectory() : file.mkdirs();
                                break;
                            default:
                                bresult = false;
                        }
                        break;
                    case '-':
                        log.println("Deleting " + filename);
                        file.delete();
                        bresult = true;
                        break;
                    case '*':
                        log.println("Updating " + filename);
                        bresult = file.exists() ? file.delete() : true;
                        if (!bresult) {
                            break;
                        }
                        switch (filetype) {
                            case 'f':
                                bresult = downloadFile(filename);
                                log.reset();
                                break;
                            case 'd':
                                bresult = file.exists() ? file.isDirectory() : file.mkdirs();
                                break;
                            default:
                                bresult = false;
                        }
                        break;
                    default:
                        bresult = true;
                }
                if (!bresult) {
                    return false;
                }
            }
        } finally {
            ftpClient.cwd("..");
        }

        if (!doPostDownload()) {
            return false;
        }

        try {
            latestVersion = ftpClient.getLatestVersion(pack.name);
        } catch (IOException ex) {
            Logger.getLogger(InstallPanel.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }

        try (FileWriter fw = new FileWriter(new File(pack.directory, "ver"))) {
            fw.write(latestVersion + "\n");
        } catch (IOException ex) {
            log.println("Failed to update version file");
            Logger.getLogger(InstallPanel.class.getName()).log(Level.SEVERE, "Update", ex);
        }

        packList.stream().filter((p) -> (p.name.equals(pack.name))).forEach((p) -> {
            p.version = latestVersion;
        });
        return true;
    }

    private List<String> fetchUpdateInstructions(Pack pack) throws NetworkException {
        final Semaphore semaphore = new Semaphore(0);
        final StringBuffer sb = new StringBuffer();
        while (true) {
            result = ftpClient.openDataChannel((ActionEvent e) -> {
                if (e.getID() == FtpClient.FTP_OK) {
                    try {
                        InputStreamReader isr;
                        int n;
                        char[] buffer = new char[4096];
                        isr = new InputStreamReader(((Socket) e.getSource()).getInputStream());
                        while (true) {
                            n = isr.read(buffer);
                            if (n < 0) {
                                break;
                            }
                            sb.append(buffer, 0, n);
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(InstallPanel.class.getName()).log(Level.SEVERE, "Download", ex);
                        log.println("Faild to save file.");
                        ftpClient.closeDataChannel();
                    }
                }
            });
            switch (result) {
                case FtpClient.FTP_OK:
                    int status = ftpClient.uins(pack, (ActionEvent e) -> {
                        ftpClient.closeDataChannel();
                        semaphore.release();
                    });
                    switch (status) {
                        case FtpClient.FTP_OK:
                            try {
                                semaphore.acquire();
                            } catch (InterruptedException ex) {
                                return null;
                            }
                            break;
                        case FtpClient.FTP_NODATA:
                            log.println("Oops! Server's complaining about missing data channel, although I've opened it.");
                            ftpClient.abandonDataChannel();
                            return null;
                        default:
                            ftpClient.abandonDataChannel();
                            return null;
                    }
                    break;
                case FtpClient.FTP_TIMEOUT:
                    if (reconnect()) {
                        continue;
                    } else {
                        return null;
                    }
                default:
                    return null;
            }
            break;
        }
        return Arrays.asList(sb.toString().split("\n"));
    }

    private boolean checkDir(File dir, int level) {
        switch (level) {
            case 1:
                return new File(dir, "mods").isDirectory() && new File(dir, "config").isDirectory() && new File(dir, "scripts").isDirectory();
            case 2:
                return new File(dir, "mods").isDirectory() || new File(dir, "config").isDirectory() || new File(dir, "scripts").isDirectory();
            default:
                return false;
        }
    }

    private void cleanUp(File dir) {
        try {
            deleteDirectory(new File(dir, "mods"));
        } catch (IOException ex) {
        }

        try {
            deleteDirectory(new File(dir, "config"));
        } catch (IOException ex) {
        }
        try {
            deleteDirectory(new File(dir, "scripts"));
        } catch (IOException ex) {
        }
        try {
            deleteDirectory(new File(dir, "resourcepacks"));
        } catch (IOException ex) {
        }
        try {
            deleteDirectory(new File(dir, "shaderpacks"));
        } catch (IOException ex) {
        }
        try {
            deleteDirectory(new File(dir, "versions"));
        } catch (IOException ex) {
        }
        new File(dir, "ver").delete();
        new File(dir, "modpack").delete();
        new File(dir, "manifest.json").delete();
    }

    private void showReadMe() throws NetworkException {
        StringBuilder sb = new StringBuilder();
        while (true) {
            result = ftpClient.openDataChannel((ActionEvent e) -> {
                if (e.getID() == FtpClient.FTP_OK) {
                    try {
                        InputStreamReader isr;
                        char[] buffer = new char[4096];
                        int n;
                        isr = new InputStreamReader(((Socket) e.getSource()).getInputStream());
                        while (true) {
                            n = isr.read(buffer);
                            if (n < 0) {
                                break;
                            }
                            sb.append(buffer, 0, n);
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(InstallPanel.class.getName()).log(Level.SEVERE, "Readme", ex);
                        log.println("Faild retrieve readme.");
                        ftpClient.closeDataChannel();
                    }
                }
            });
            switch (result) {
                case FtpClient.FTP_OK:
                    downloadSize = ftpClient.retr(workingPack + "/readme", (ActionEvent e) -> {
                        ftpClient.closeDataChannel();
                        EventQueue.invokeLater(() -> {
                            ReadmeBox rb = new ReadmeBox(parentFrame, true, sb.toString());
                            rb.setVisible(true);
                        });
                    });
                    if (downloadSize >= 0) {
                    } else {
                        switch (downloadSize) {
                            case FtpClient.FTP_NODATA:
                                log.println("Oops! Server's complaining about missing data channel, although I've opened it.");
                                ftpClient.abandonDataChannel();
                                return;
                            default:
                                ftpClient.abandonDataChannel();
                        }
                    }
                    break;
                case FtpClient.FTP_TIMEOUT:
                    if (reconnect()) {
                        continue;
                    }
            }
            break;
        }
    }

    public boolean downloadMojangLauncher() {
        URL u;
        HttpURLConnection connection;
        Proxy p;
        InputStream is;
        FileOutputStream fos;

        if (new File(config.getInstallDir(), "Minecraft.jar").isFile()) {
            return true;
        }

        log.println("Connecting to Mojang server...");
        if (config.getHttpProxy().isEmpty()) {
            p = Proxy.NO_PROXY;
        } else {
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
            p = new Proxy(Proxy.Type.HTTP, new ProxyAddress(config.getHttpProxy(), 3128).getSockaddr());
        }
        try {
            u = new URL("https://s3.amazonaws.com/Minecraft.Download/launcher/Minecraft.jar");
            connection = (HttpURLConnection) u.openConnection(p);
            connection.addRequestProperty("User-agent", "Minecraft Bootloader");
            connection.setUseCaches(false);
            connection.setDefaultUseCaches(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.connect();
            log.println("Mojang server returned " + connection.getResponseMessage());
            if (connection.getResponseCode() != 200) {
                connection.disconnect();
                return false;
            }
        } catch (MalformedURLException ex) {
            Logger.getLogger(InstallPanel.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        } catch (IOException ex) {
            Logger.getLogger(InstallPanel.class.getName()).log(Level.SEVERE, null, ex);
            log.println("Connection to Mojang server failed.");
            return false;
        }

        try {
            is = connection.getInputStream();
            fos = new FileOutputStream(new File(config.getInstallDir(), "Minecraft.jar"));
            log.println("Downloading Minecraft.jar");
            byte[] buffer = new byte[4096];
            for (int n = is.read(buffer); n > 0; n = is.read(buffer)) {
                fos.write(buffer, 0, n);
            }
            fos.close();
            is.close();
            connection.disconnect();
            log.println("Done.");
        } catch (IOException ex) {
            Logger.getLogger(InstallPanel.class.getName()).log(Level.SEVERE, "downloadMojangLauncher", ex);
            log.println("Faild to save file.");
            return false;
        }
        return true;
    }

    private boolean downloadFile(String filename) throws NetworkException {
        return downloadFile(filename, filename);
    }

    @SuppressWarnings({"Convert2Lambda"})
    private boolean downloadFile(String filename, String local) throws NetworkException {
        final Semaphore semaphore = new Semaphore(0);
        success = false;
        while (true) {
            result = ftpClient.openDataChannel(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (e.getID() == FtpClient.FTP_OK) {
                        try {
                            InputStream is;
                            FileOutputStream fos;

                            is = ((Socket) e.getSource()).getInputStream();
                            fos = new FileOutputStream(new File(workingDir, local));
                            byte[] buffer = new byte[4096];
                            for (int n = is.read(buffer); n > 0; n = is.read(buffer)) {
                                fos.write(buffer, 0, n);
                                log.advance(n);
                            }
                            fos.close();
                            success = true;
                        } catch (IOException ex) {
                            Logger.getLogger(InstallPanel.class.getName()).log(Level.SEVERE, "Download", ex);
                            log.println("Faild to save file.");
                            success = false;
                        }
                    }
                }
            });
            switch (result) {
                case FtpClient.FTP_OK:
                    int size = ftpClient.retr(filename, (ActionEvent e) -> {
                        ftpClient.closeDataChannel();
                        semaphore.release();
                    });
                    if (size < 0) {
                        ftpClient.abandonDataChannel();
                    } else {
                        log.reset();
                        log.setMaximum(size);
                    }
                    try {
                        semaphore.acquire();
                    } catch (InterruptedException ex) {
                        return false;
                    }
                    break;
                case FtpClient.FTP_TIMEOUT:
                    if (reconnect()) {
                        continue;
                    } else {
                        ftpClient.abandonDataChannel();
                        return false;
                    }
                default:
                    ftpClient.abandonDataChannel();
                    return false;
            }
            break;
        }
        return success;
    }

    public boolean changeJvmParameters() {
        if (packList.isEmpty()) {
            log.println("No packs installed.");
            return false;
        }

        profileChooser.setModel(new DefaultComboBoxModel<>(profiles.profiles.keySet().toArray(new String[0])));
        profileChooserActionPerformed(null);
        parentFrame.setProceedListener((ActionEvent e) -> {
            JTabbedPane tabPane = (JTabbedPane) parameterPanel.getParent();
            tabPane.remove(parameterPanel);
            commitJvmParameters();
            parentFrame.state &= ~1;
            log.setStatusText("Ok");
        });
        parentFrame.setAbortListener((ActionEvent e) -> {
            JTabbedPane tabPane = (JTabbedPane) parameterPanel.getParent();
            tabPane.remove(parameterPanel);
            parentFrame.state &= ~1;
            log.setStatusText("Ok");
        });
        return true;
    }

    private void commitJvmParameters() {
        LauncherProfiles.Profile profile;
        String newParams;
        profile = profiles.profiles.get((String) profileChooser.getSelectedItem());
        if (profile == null) {
            return;
        }
        newParams = parameterBox.getText().replaceAll("\n", " ").trim();
        profile.javaArgs = newParams;
        writeFiles();
    }

    /**
     * Untar an input file into an output file.
     *
     * The output file is created in the output folder, having the same name as
     * the input file, minus the '.tar' extension.
     *
     * @param is the input .tar stream.
     * @param outputDir the output directory file.
     * @throws IOException
     * @throws FileNotFoundException
     *
     * @return The {@link List} of {@link File}s with the untared content.
     * @throws ArchiveException
     */
    @SuppressWarnings("ConvertToTryWithResources")
    private ArrayList<String> unTar(final InputStream is, final File outputDir) throws FileNotFoundException, IOException, ArchiveException {

        final ArrayList<String> untaredFiles = new ArrayList<>();
        final TarArchiveInputStream archiveStream = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream("tar", is);
        TarArchiveEntry entry;
        int bytesRead = 0;
        /*int x = 0;*/
        byte[] buf = new byte[16384];
        /*log.println("|---+----+----+----+----+----+----+----+----+----|");
        log.print("/");*/
        while ((entry = (TarArchiveEntry) archiveStream.getNextEntry()) != null) {
            final File outputFile = new File(outputDir, entry.getName());
            if (entry.isDirectory()) {
                if (!outputFile.exists()) {
                    if (!outputFile.mkdirs()) {
                        throw new IllegalStateException(String.format("Couldn't create directory %s.", outputFile.getAbsolutePath()));
                    }
                }
            } else {
                final FileOutputStream outputFileStream = new FileOutputStream(outputFile);
                /*int incr = Math.floorDiv(downloadSize, 50);*/
                for (int n = archiveStream.read(buf); n > 0; n = archiveStream.read(buf)) {
                    outputFileStream.write(buf, 0, n);
                    log.advance(n);
                    /*bytesRead += n;
                    x++;*/
 /*if (bytesRead >= incr) {
                        log.backspace();
                        log.print("#");
                        switch (Math.floorDiv(x, 100)) {
                            case 0:
                                log.print("/");
                                break;
                            case 1:
                                log.print("-");
                                break;
                            case 2:
                                log.print("\\");
                                break;
                            case 3:
                                log.print("|");
                                break;
                        }
                        bytesRead -= incr;
                    }
                    if (x % 100 == 0) {
                        log.backspace();
                        switch (Math.floorDiv(x, 100)) {
                            case 0:
                            case 4:
                                log.print("/");
                                x = 0;
                                break;
                            case 1:
                                log.print("-");
                                break;
                            case 2:
                                log.print("\\");
                                break;
                            case 3:
                                log.print("|");
                                break;
                        }
                    }*/
                }
                outputFileStream.close();
            }
            untaredFiles.add(entry.getName());
        }
        archiveStream.close();
        /*log.backspace();
        log.println("#");*/

        return untaredFiles;
    }

    private boolean reconnect() {
        try {
            ftpClient.connect();
            return ftpClient.login(config.getFtpCredentials());
        } catch (IOException ex) {
            return false;
        }
    }

    /**
     * these functions where taken from org.apache.commons.io.FileUtils
     */
    /**
     * Deletes a directory recursively.
     *
     * @param directory directory to delete
     * @throws IOException in case deletion is unsuccessful
     */
    private static void deleteDirectory(File directory) throws IOException {
        if (!directory.exists()) {
            return;
        }

        if (!isSymlink(directory)) {
            cleanDirectory(directory);
        }

        if (!directory.delete()) {
            String message
                    = "Unable to delete directory " + directory + ".";
            throw new IOException(message);
        }
    }

    /**
     * Cleans a directory without deleting it.
     *
     * @param directory directory to clean
     * @throws IOException in case cleaning is unsuccessful
     */
    private static void cleanDirectory(File directory) throws IOException {
        if (!directory.exists()) {
            String message = directory + " does not exist";
            throw new IllegalArgumentException(message);
        }

        if (!directory.isDirectory()) {
            String message = directory + " is not a directory";
            throw new IllegalArgumentException(message);
        }

        File[] files = directory.listFiles();
        if (files == null) {  // null if security restricted
            throw new IOException("Failed to list contents of " + directory);
        }

        IOException exception = null;
        for (File file : files) {
            try {
                forceDelete(file);
            } catch (IOException ioe) {
                exception = ioe;
            }
        }

        if (null != exception) {
            throw exception;
        }
    }

    /**
     * Deletes a file. If file is a directory, delete it and all
     * sub-directories.
     * <p>
     * The difference between File.delete() and this method are:
     * <ul>
     * <li>A directory to be deleted does not have to be empty.</li>
     * <li>You get exceptions when a file or directory cannot be deleted.
     * (java.io.File methods returns a boolean)</li>
     * </ul>
     *
     * @param file file or directory to delete, must not be {@code null}
     * @throws NullPointerException if the directory is {@code null}
     * @throws FileNotFoundException if the file was not found
     * @throws IOException in case deletion is unsuccessful
     */
    private static void forceDelete(File file) throws IOException {
        if (file.isDirectory()) {
            deleteDirectory(file);
        } else {
            boolean filePresent = file.exists();
            if (!file.delete()) {
                if (!filePresent) {
                    throw new FileNotFoundException("File does not exist: " + file);
                }
                String message
                        = "Unable to delete file: " + file;
                throw new IOException(message);
            }
        }
    }

    /**
     * Determines whether the specified file is a Symbolic Link rather than an
     * actual file.
     * <p>
     * Will not return true if there is a Symbolic Link anywhere in the path,
     * only if the specific file is.
     * <p>
     * <b>Note:</b> the current implementation always returns {@code false} if
     * the system is detected as Windows using
     * {@link FilenameUtils#isSystemWindows()}
     *
     * @param file the file to check
     * @return true if the file is a Symbolic Link
     * @throws IOException if an IO error occurs while checking the file
     * @since 2.0
     */
    private static boolean isSymlink(File file) throws IOException {
        if (file == null) {
            throw new NullPointerException("File must not be null");
        }

        File fileInCanonicalDir;
        if (file.getParent() == null) {
            fileInCanonicalDir = file;
        } else {
            File canonicalDir = file.getParentFile().getCanonicalFile();
            fileInCanonicalDir = new File(canonicalDir, file.getName());
        }

        return !fileInCanonicalDir.getCanonicalFile().equals(fileInCanonicalDir.getAbsoluteFile());
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        parameterPanel = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        profileChooser = new javax.swing.JComboBox<>();
        jLabel4 = new javax.swing.JLabel();
        ramChooser = new javax.swing.JComboBox<>();
        jLabel5 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        parameterBox = new javax.swing.JTextArea();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        dirBox = new javax.swing.JTextField();
        modpackChooser = new javax.swing.JComboBox<>();
        browse = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        description = new javax.swing.JTextArea();
        addons = new javax.swing.JButton();
        force = new javax.swing.JCheckBox();

        jLabel3.setText("Modpack");

        profileChooser.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                profileChooserActionPerformed(evt);
            }
        });

        jLabel4.setText("Ram");

        ramChooser.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "4 GB", "5 GB", "6 GB", "7 GB", "8 GB", "9 GB", "10 GB", "11 GB", "12 GB", "13 GB", "14 GB", "15 GB", "16 GB" }));
        ramChooser.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ramChooserActionPerformed(evt);
            }
        });

        jLabel5.setText("complete parameter string");

        parameterBox.setColumns(20);
        parameterBox.setLineWrap(true);
        parameterBox.setRows(5);
        jScrollPane1.setViewportView(parameterBox);

        javax.swing.GroupLayout parameterPanelLayout = new javax.swing.GroupLayout(parameterPanel);
        parameterPanel.setLayout(parameterPanelLayout);
        parameterPanelLayout.setHorizontalGroup(
            parameterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(parameterPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(parameterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 376, Short.MAX_VALUE)
                    .addGroup(parameterPanelLayout.createSequentialGroup()
                        .addGroup(parameterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel3)
                            .addComponent(jLabel4))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(parameterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(profileChooser, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(ramChooser, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(parameterPanelLayout.createSequentialGroup()
                        .addComponent(jLabel5)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        parameterPanelLayout.setVerticalGroup(
            parameterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(parameterPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(parameterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(profileChooser, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(parameterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ramChooser, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel5)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 195, Short.MAX_VALUE)
                .addContainerGap())
        );

        jLabel1.setText("Modpack");

        jLabel2.setText("Directory");

        modpackChooser.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                modpackChooserActionPerformed(evt);
            }
        });

        browse.setText("Browse");
        browse.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseActionPerformed(evt);
            }
        });

        description.setColumns(20);
        description.setRows(5);
        jScrollPane2.setViewportView(description);

        addons.setText("Addons");
        addons.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addonsActionPerformed(evt);
            }
        });

        force.setText("Force reinstall");
        force.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                forceActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane2)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel1)
                            .addComponent(jLabel2))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(dirBox, javax.swing.GroupLayout.DEFAULT_SIZE, 187, Short.MAX_VALUE)
                            .addComponent(modpackChooser, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(browse)
                            .addComponent(addons, javax.swing.GroupLayout.Alignment.TRAILING)))
                    .addComponent(force, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(modpackChooser, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(addons))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(browse)
                    .addComponent(dirBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel2))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 104, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(force)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    @SuppressWarnings("null")
    private void modpackChooserActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_modpackChooserActionPerformed
        boolean isInstalled = false;
        Pack installedPack = null;
        for (Pack p : packList) {
            if (p.name.equals(modpackChooser.getSelectedItem())) {
                isInstalled = true;
                installedPack = p;
            }
        }
        dirBox.setEnabled(!isInstalled || force.isSelected());
        browse.setEnabled(!isInstalled || force.isSelected());
        if (isInstalled) {
            description.setText(availablePacks.get(modpackChooser.getSelectedIndex()).description + "\nThis pack is installed.");
            dirBox.setText(installedPack.directory);
            return;
        } else {
            description.setText(availablePacks.get(modpackChooser.getSelectedIndex()).description);
        }
        if (packList.isEmpty()) {
            dirBox.setText(config.getInstallDir().getAbsolutePath());
        } else {
            dirBox.setText(new File(config.getInstallDir(), availablePacks.get(modpackChooser.getSelectedIndex()).name).getAbsolutePath());
        }
    }//GEN-LAST:event_modpackChooserActionPerformed

    private void forceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_forceActionPerformed
        if (force.isSelected()) {
            if (JOptionPane.showConfirmDialog(this, "Forcing a reinstall will clean up the directory resulting in a loss of all settings!", "Reinstall", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.CANCEL_OPTION) {
                force.setSelected(false);
                return;
            }
        }

        boolean isInstalled = false;
        for (Pack p : packList) {
            if (p.name.equals(modpackChooser.getSelectedItem())) {
                isInstalled = true;
            }
        }
        dirBox.setEnabled(!isInstalled || force.isSelected());
        browse.setEnabled(!isInstalled || force.isSelected());
    }//GEN-LAST:event_forceActionPerformed

    private void browseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browseActionPerformed
        JFileChooser fc = new JFileChooser(new File(dirBox.getText()));

        fc.setFileHidingEnabled(false);
        fc.setDialogTitle("Select directory");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            dirBox.setText(fc.getSelectedFile().toString());
        }
    }//GEN-LAST:event_browseActionPerformed

    private void addonsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addonsActionPerformed
        addonsPanel = new AddonsPanel(availablePacks.get(modpackChooser.getSelectedIndex()).addons.toArray(new Addon[0]));
        JTabbedPane tabPane;
        tabPane = (JTabbedPane) getParent();
        tabPane.remove(this);
        tabPane.add("Addons", addonsPanel);
        tabPane.setSelectedComponent(addonsPanel);
        addonsOpenedOnce = true;
        parentFrame.setAbortListener((ActionEvent e) -> {
            tabPane.remove(addonsPanel);
            tabPane.add("Install", this);
            tabPane.setSelectedComponent(this);
        });
        parentFrame.setProceedListener((ActionEvent e) -> {
            boolean s[];
            tabPane.remove(addonsPanel);
            tabPane.add("Install", this);
            tabPane.setSelectedComponent(this);
            s = addonsPanel.getSelected();
            for (int n = 0; n < s.length; n++) {
                availablePacks.get(modpackChooser.getSelectedIndex()).addons.get(n).install = s[n];
            }
        });
    }//GEN-LAST:event_addonsActionPerformed

    private void profileChooserActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_profileChooserActionPerformed
        String params;
        List<String> paramList;
        String xmx = null;
        if (profiles.profiles.isEmpty()) {
            return;
        }

        params = profiles.profiles.get((String) profileChooser.getSelectedItem()).javaArgs.replaceAll(" ", "\n");
        if (params == null) {
            return;
        }
        parameterBox.setText(params);
        paramList = Arrays.asList(params.split("\n"));
        for (String s : paramList) {
            if (s.startsWith("-Xmx")) {
                xmx = s.substring(4);
            }
        }

        if (xmx == null) {
            return;
        }

        try {
            int memory = Integer.parseInt(xmx.substring(0, xmx.length() - 1));
            if (memory <= 4) {
                ramChooser.setSelectedIndex(0);
            } else if ((memory > 4) && (memory < 17)) {
                ramChooser.setSelectedIndex(memory - 4);
            } else {
                ramChooser.setSelectedIndex(12);
            }
        } catch (NumberFormatException ex) {

        }
        parameterBox.setText(params);
    }//GEN-LAST:event_profileChooserActionPerformed

    private void ramChooserActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ramChooserActionPerformed
        String params;
        List<String> paramList;
        StringBuilder newParamList = new StringBuilder();
        newParamList.append("-Xmx").append(Integer.toString(ramChooser.getSelectedIndex() + 4)).append("G\n");
        newParamList.append("-Xms").append((ramChooser.getSelectedIndex() + 1 > 4) ? 4 : Integer.toString(ramChooser.getSelectedIndex() + 1)).append("G");
        params = parameterBox.getText();
        paramList = Arrays.asList(params.split("\n"));
        paramList.stream().filter((s) -> ((!s.startsWith("-Xmx") && (!s.startsWith("-Xms"))))).forEach((s) -> {
            newParamList.append("\n").append(s);
        });
        parameterBox.setText(newParamList.toString());
    }//GEN-LAST:event_ramChooserActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addons;
    private javax.swing.JButton browse;
    private javax.swing.JTextArea description;
    private javax.swing.JTextField dirBox;
    private javax.swing.JCheckBox force;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JComboBox<String> modpackChooser;
    private javax.swing.JTextArea parameterBox;
    private javax.swing.JPanel parameterPanel;
    private javax.swing.JComboBox<String> profileChooser;
    private javax.swing.JComboBox<String> ramChooser;
    // End of variables declaration//GEN-END:variables
}
