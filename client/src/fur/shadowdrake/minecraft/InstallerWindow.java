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
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import fur.shadowdrake.minecraft.json.Pack;
import fur.shadowdrake.minecraft.json.PackDescription;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

/**
 *
 * @author ayron
 */
public class InstallerWindow extends javax.swing.JFrame implements LogOutput {

    private final Configuration config;
    private final ArrayList<ActionListener> proceedListeners;
    private final ArrayList<ActionListener> abortListeners;
    private final FtpClient ftpClient;
    private final InstallerWindow instWindow;
    private final InstallPanel installPanel;
    private final ConfigPanel configPanel;
    protected int state;
    private List<Pack> needUpdate;
    private PackDescriptionList availablePacks;

    private static final int STATE_OK = 0;
    private static final int STATE_CONFIG = 1;
    private static final int STATE_INSTALL = 2;
    private static final int STATE_WORKING = 4;
    private static final int STATE_ERROR = 8;
    private static final int STATE_FIRST = 16;

    private final ActionListener lafChanged = (ActionEvent e) -> {
        JComboBox<String> laf = (JComboBox<String>) e.getSource();
        LafList lafList = (LafList) laf.getModel();
        try {
            UIManager.setLookAndFeel(lafList.get((String) laf.getSelectedItem()));
            Window[] exc = Window.getWindows();
            int wasVisible = exc.length;

            for (int i$ = 0; i$ < wasVisible; ++i$) {
                Window window = exc[i$];
                SwingUtilities.updateComponentTreeUI(window);
                window.pack();

                if (window instanceof JFrame) {
                    JFrame frame = (JFrame) window;
                    boolean wasDecoratedByOS = !frame.isUndecorated();
                    boolean var8 = UIManager.getLookAndFeel().getSupportsWindowDecorations();
                    if (var8 == wasDecoratedByOS) {
                        frame.setVisible(false);
                        frame.dispose();
                        if (var8) {
                            frame.setUndecorated(true);
                            frame.getRootPane().setWindowDecorationStyle(2);
                        } else {
                            frame.setUndecorated(false);
                            frame.getRootPane().setWindowDecorationStyle(0);
                        }

                        frame.setVisible(true);
                        if (frame instanceof InstallerWindow) {
                            frame.setSize(getPreferredSize());
                        }
                    }
                }
            }

        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
            Logger.getLogger(InstallerWindow.class.getName()).log(Level.WARNING, "Change design", ex);
        }
    };

    private final ActionListener startMinecraftListener = (ActionEvent ev) -> {
        startMinecraft();
    };

    /**
     * Creates new form InstallerWindow
     *
     * @param config
     */
    @SuppressWarnings({"OverridableMethodCallInConstructor", "LeakingThisInConstructor"})
    public InstallerWindow(Configuration config) {
        this.config = config;
        instWindow = this;
        proceedListeners = new ArrayList<>();
        abortListeners = new ArrayList<>();
        initComponents();
        jMenuBar1.add(javax.swing.Box.createHorizontalGlue(), 2);
        this.setLocationRelativeTo(null);
        setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("du.png")));
        configPanel = new ConfigPanel(config);
        configPanel.setLafChangeListener(lafChanged);
        ftpClient = new FtpClient(config, this);
        installPanel = new InstallPanel(this, config, ftpClient, this);
        installPanel.setCompletedListener((ActionEvent e) -> {
            switch (e.getID()) {
                case 0:
                case 1:
                    setProceedListener(startMinecraftListener);
                    state &= ~STATE_INSTALL;
                    if ((state & STATE_FIRST) > 0) {
                        paramsMenuActionPerformed(null);
                    }
                    break;
                case 2:
                    setAbortListener((ActionEvent evt) -> {
                        tabPane.remove(installPanel);
                        state &= ~(STATE_INSTALL | STATE_WORKING);
                    });
                    setProceedListener((ActionEvent evt) -> {
                        installPanel.begin((state & STATE_FIRST) > 0);
                    });
                    break;
            }
        });
        state = STATE_WORKING;
        setIndeterminate();
        setStatusText("Connecting");
        beginWithFTP();
    }

    public void setProceedListener(ActionListener proceedListener) {
        this.proceedListeners.add(proceedListener);
        proceedButton.setEnabled(true);
        if (proceedListeners.get(proceedListeners.size() - 1) == startMinecraftListener) {
            proceedButton.setText("Start Launcher");
        } else {
            proceedButton.setText("Proceed");
        }
    }

    public void setAbortListener(ActionListener abortListener) {
        this.abortListeners.add(abortListener);
        abortButton.setEnabled(true);
    }

    public void dropProceedListener(ActionListener proceedListener) {
        this.proceedListeners.remove(proceedListener);
        proceedButton.setEnabled(!proceedListeners.isEmpty());
        if ((!proceedListeners.isEmpty()) && (proceedListeners.get(proceedListeners.size() - 1) == startMinecraftListener)) {
            proceedButton.setText("Start Launcher");
        } else {
            proceedButton.setText("Proceed");
        }
    }

    public void dropAllProceedListeners() {
        proceedListeners.clear();
        proceedButton.setEnabled(false);
        proceedButton.setText("Proceed");
    }

    public void dropAbortListener(ActionListener abortListener) {
        this.abortListeners.remove(abortListener);
        abortButton.setEnabled(!abortListeners.isEmpty());
    }

    public void dropAllAbortListeners() {
        abortListeners.clear();
        abortButton.setEnabled(false);
    }

    @Override
    public void println(String text) {
        print(text + "\n");
    }

    @Override
    public void print(String text) {
        Document document = this.logBox.getDocument();
        final JScrollBar scrollBar = this.jScrollPane1.getVerticalScrollBar();

        try {
            document.insertString(document.getLength(), text, (AttributeSet) null);
        } catch (BadLocationException ex) {
        }
        java.awt.EventQueue.invokeLater(() -> {
            scrollBar.setValue(Integer.MAX_VALUE);
        });
    }

    @Override
    public void backspace() {
        Document document = this.logBox.getDocument();
        final JScrollBar scrollBar = this.jScrollPane1.getVerticalScrollBar();

        try {
            document.remove(document.getLength() - 1, 1);
        } catch (BadLocationException ex) {
        }
        java.awt.EventQueue.invokeLater(() -> {
            scrollBar.setValue(Integer.MAX_VALUE);
        });
    }

    @Override
    public void setStatusText(String text) {
        status.setText(text);
    }

    @Override
    public void setMaximum(int value) {
        EventQueue.invokeLater(() -> {
            jProgressBar1.setMaximum(value);
        });
    }

    @Override
    public void setValue(int value) {
        EventQueue.invokeLater(() -> {
            jProgressBar1.setValue(value);
        });
    }

    @Override
    public void advance(int value) {
        EventQueue.invokeLater(() -> {
            int n = jProgressBar1.getValue();
            jProgressBar1.setValue(n + value > jProgressBar1.getMaximum() ? jProgressBar1.getMaximum() : n + value);
        });
    }

    @Override
    public void setIndeterminate() {
        EventQueue.invokeLater(() -> {
            jProgressBar1.setIndeterminate(true);
        });
    }

    @Override
    public void reset() {
        int n = jProgressBar1.getValue();
        try {
            EventQueue.invokeAndWait(() -> {
                if (jProgressBar1.isIndeterminate()) {
                    jProgressBar1.setIndeterminate(false);
                }
                jProgressBar1.setValue(0);
                jProgressBar1.setString("");
            });
        } catch (InterruptedException | InvocationTargetException ex) {
            Logger.getLogger(InstallerWindow.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (n > 0) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
                Logger.getLogger(InstallerWindow.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public void showPercentage(boolean b) {
        EventQueue.invokeLater(() -> {
            if (b) {
                jProgressBar1.setString(null);
            } else {
                jProgressBar1.setString("");
            }
        });
    }

    //<editor-fold defaultstate="collapsed" desc="FTP">
    private void askFtpCredentialsAndLogin() {
        EventQueue.invokeLater(() -> {
            setIndeterminate();
            PasswordAuthentication credentials = new PasswordAuthentication("", new char[0]);
            CredentialDialog cd = new CredentialDialog(instWindow, true, credentials, "Enter FTP credentials");
            cd.setVisible(true);
            if (cd.getReturnStatus() && !cd.getAuthentication().getUserName().isEmpty()) {
                credentials = cd.getAuthentication();
                config.setFtpCredentials(credentials);
                LoginAndCheckUpdate();
            } else {
                new Thread(() -> {
                    reset();
                    println("##############################################");
                    println("# If you don't have credentials, please      #");
                    println("# write to minecraft@dragonsunited.eu        #");
                    println("# If you've allready registered, please      #");
                    println("# check your inbox and spam directory first! #");
                    println("#                                            #");
                    println("# Wenn du keine Zugangsdaten hast, schreibe  #");
                    println("# bitte an minecraft@dragonsunited.eu        #");
                    println("# Wenn du bereits registriert bist, prüfe    #");
                    println("# bitte zuerst dein E-Mail-Postfach und das  #");
                    println("# Spam-Verzeichnis!                          #");
                    println("#                                            #");
                    println("# weitere Infos unter / more infos at        #");
                    println("# http://dragonsunited.eu/minecraft          #");
                    println("##############################################");
                }, "holler").start();
                instWindow.setProceedListener((ActionEvent e) -> {
                    askFtpCredentialsAndLogin();
                });
            }
        });
    }

    private void LoginAndCheckUpdate() {
        new Thread(() -> {
            try {
                setStatusText("Logging in");
                if (ftpClient.login(config.getFtpCredentials())) {
                    installPanel.initialize();
                    if (!installPanel.downloadMojangLauncher()) {
                        println("Please download Minecraft.jar manually into " + config.getInstallDir());
                        JOptionPane.showMessageDialog(this, "Please download Minecraft.jar manually into " + config.getInstallDir());
                        state = STATE_ERROR;
                        reset();
                        setStatusText("Error");
                        return;
                    }
                    if (installPanel.getInstalledPacks().isEmpty()) {
                        /* first run */
                        println("No modpacks installed. Please install one!");
                        state |= STATE_FIRST;
                        beginWithInstallation();
                    } else if (!(needUpdate = installPanel.checkUpdate()).isEmpty()) {
                        println("Following packs are out of date:");
                        needUpdate.stream().forEach((p) -> {
                            println(p.name);
                        });
                        println("Click proceed to begin update.");
                        setProceedListener((ActionEvent e) -> {
                            new Thread(() -> {
                                try {
                                    state = STATE_WORKING;
                                    for (Pack p : needUpdate) {
                                        boolean updateResult = installPanel.updatePack(p);
                                        if (!updateResult) {
                                            println("Modpack " + p.name + " failed to update. Please reinstall!");
                                            JOptionPane.showMessageDialog(this, "Modpack " + p.name + " failed to update. Please reinstall!");
                                        }
                                    }
                                    installPanel.writeFiles();
                                    state = STATE_OK;
                                    setStatusText("Ok");
                                    println("Please start the launcher.");
                                    setProceedListener(startMinecraftListener);
                                } catch (NetworkException ex) {
                                    Logger.getLogger(InstallerWindow.class.getName()).log(Level.SEVERE, null, ex);
                                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Network error", JOptionPane.ERROR_MESSAGE);
                                    ftpClient.close();
                                    println("Timeout. Previous command wasn't received by the server. This is a network error. Please try again later.");
                                    state = ERROR;
                                    setStatusText("Error");
                                }
                            }, "Updater").start();
                        });
                        reset();
                        setStatusText("Ok");
                        state = STATE_OK;
                        installPanel.writeFiles();
                    } else {
                        state = STATE_OK;
                        installPanel.writeFiles();
                        println("Everything fine. Please start the launcher.");
                        reset();
                        setStatusText("Ok");
                        setProceedListener(startMinecraftListener);
                    }
                } else {
                    config.setFtpCredentials(new PasswordAuthentication("", new char[0]));
                    askFtpCredentialsAndLogin();
                }
            } catch (IOException ex) {
                if (ex.getMessage().equals("Timeout")) {
                    beginWithFTP();
                } else {
                    state = STATE_ERROR;
                }
            } catch (NetworkException ex) {
                Logger.getLogger(InstallerWindow.class.getName()).log(Level.SEVERE, null, ex);
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Network error", JOptionPane.ERROR_MESSAGE);
                ftpClient.close();
                println("Timeout. Previous command wasn't received by the server. This is a network error. Please try again later.");
                state = STATE_ERROR;
                setStatusText("Error");
            }
        }, "Login").start();
    }

    private void beginWithFTP() {
        new Thread(() -> {
            try {
                ftpClient.connect();

            } catch (IOException ex) {
                Logger.getLogger(InstallerWindow.class
                        .getName()).log(Level.SEVERE, "beginWithFTP", ex);
                instWindow.println("FTP connection failed. Please check configuration.");
                reset();
                setStatusText("Error");
                state = STATE_ERROR;
            }
            if (ftpClient instanceof FtpClient) {
                if (config.getFtpCredentials().getUserName().isEmpty()) {
                    askFtpCredentialsAndLogin();
                } else {
                    LoginAndCheckUpdate();
                }
            }
        }, "Open FTP").start();
    }

    private boolean reconnect() {
        try {
            ftpClient.connect();
            return ftpClient.login(config.getFtpCredentials());
        } catch (IOException ex) {
            return false;
        }
    }//</editor-fold>

    private void beginWithInstallation() {
        state |= STATE_INSTALL;
        setStatusText("Install");
        new Thread(() -> {
            while (true) {
                try {
                    int result;
                    result = ftpClient.openDataChannel((ActionEvent e) -> {
                        if (e.getID() == FtpClient.FTP_OK) {
                            try {
                                Gson gson = new GsonBuilder().create();
                                availablePacks = null;
                                availablePacks = gson.fromJson(new InputStreamReader(((Socket) e.getSource()).getInputStream()),
                                        PackDescriptionList.class);
                            } catch (IOException | JsonSyntaxException | JsonIOException ex) {
                                println("Error loading Modpack data.");
                                Logger.getLogger(InstallerWindow.class.getName()).log(Level.SEVERE, "Load Modpack data", ex);
                            }
                        }
                    });
                    switch (result) {
                        case FtpClient.FTP_OK:
                            ftpClient.retr("modpacks.json", (ActionEvent e) -> {
                                ftpClient.closeDataChannel();
                                installPanel.prepareInstall(availablePacks);
                                tabPane.add("Install", installPanel);
                                tabPane.setSelectedComponent(installPanel);
                                state &= ~STATE_WORKING;
                                setAbortListener((ActionEvent evt) -> {
                                    tabPane.remove(installPanel);
                                    state &= ~(STATE_INSTALL | STATE_WORKING);
                                    setStatusText("Ok");
                                });
                                EventQueue.invokeLater(() -> {
                                    setProceedListener((ActionEvent evt) -> {
                                        installPanel.begin((state & STATE_FIRST) > 0);
                                    });
                                });
                                reset();
                            });
                            break;
                        case FtpClient.FTP_TIMEOUT:
                            if (reconnect()) {
                                continue;
                            } else {
                                break;
                            }
                    }
                    break;
                } catch (NetworkException ex) {
                    Logger.getLogger(InstallerWindow.class.getName()).log(Level.SEVERE, null, ex);
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Network error", JOptionPane.ERROR_MESSAGE);
                    ftpClient.close();
                    println("Timeout. Previous command wasn't received by the server. This is a network error. Please try again later.");
                    setStatusText("Error");
                    state = STATE_ERROR;
                }
            }
        }, "Installer Start").start();
    }

    @SuppressWarnings({"UseSpecificCatch", "BroadCatchBlock", "TooBroadCatch"})
    private void startMinecraft() {
        new Thread(() -> {
            try {
                ftpClient.close();
            } catch (NullPointerException ex) {
            }
            EventQueue.invokeLater(() -> {
                System.setProperty("swing.systemlaf", new LafList().get(config.getLaf()));
                dispose();
                try {
                    Proxy p;
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
                    URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{new File(config.getInstallDir(), "Minecraft.jar").toURI().toURL()});
                    Class Bootstrap = urlClassLoader.loadClass("net.minecraft.bootstrap.Bootstrap");
                    Constructor constructor = Bootstrap.getConstructor(new Class[]{File.class, Proxy.class, PasswordAuthentication.class, String[].class});
                    Object bootstrap = constructor.newInstance(config.getInstallDir(), p, config.getHttpProxyCredentials(), new String[0]);
                    Method execute = Bootstrap.getMethod("execute", new Class[]{boolean.class});
                    new Thread(() -> {
                        try {
                            execute.invoke(bootstrap, false);
                            System.out.close();
                        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                            Logger.getLogger(InstallerWindow.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }, "Minecraft Launcher").start();
                } catch (MalformedURLException | ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    setVisible(true);
                    Logger.getLogger(InstallPanel.class.getName()).log(Level.SEVERE, "Start Minecraft", ex);
                    println("Executing Minecraft-launcher failed.");
                } catch (Throwable t) {
                    setVisible(true);
                    println("Something went fatally wrong:");
                    println(t.toString());
                }
            });
        }, "Minecraft").start();
    }

    public static void main(String args[]) {
        String laf;
        System.setProperty("insubstantial.logEDT", "false");
        Configuration config = new Configuration();
        laf = config.getLaf();

        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);

        EventQueue.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(new LafList().get(laf));

            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
                Logger.getLogger(InstallerWindow.class
                        .getName()).log(Level.WARNING, null, e);
            }
            new InstallerWindow(config).setVisible(true);
        });
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        tabPane = new javax.swing.JTabbedPane();
        jScrollPane1 = new javax.swing.JScrollPane();
        logBox = new javax.swing.JTextArea();
        proceedButton = new javax.swing.JButton();
        abortButton = new javax.swing.JButton();
        jPanel1 = new javax.swing.JPanel();
        jProgressBar1 = new javax.swing.JProgressBar();
        status = new javax.swing.JLabel();
        jMenuBar1 = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        installMenu = new javax.swing.JMenuItem();
        testMenu = new javax.swing.JMenuItem();
        quitMenu = new javax.swing.JMenuItem();
        editMenu = new javax.swing.JMenu();
        paramsMenu = new javax.swing.JMenuItem();
        prefMenu = new javax.swing.JMenuItem();
        helpMenu = new javax.swing.JMenu();
        aboutMenu = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("DragonsUnited Minecraft Installer");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        jScrollPane1.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        logBox.setEditable(false);
        logBox.setColumns(20);
        logBox.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
        logBox.setLineWrap(true);
        logBox.setRows(5);
        logBox.setWrapStyleWord(true);
        jScrollPane1.setViewportView(logBox);

        tabPane.addTab("Output", jScrollPane1);

        proceedButton.setText("Proceed");
        proceedButton.setEnabled(false);
        proceedButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                proceedButtonActionPerformed(evt);
            }
        });

        abortButton.setText("Abort");
        abortButton.setEnabled(false);
        abortButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                abortButtonActionPerformed(evt);
            }
        });

        jPanel1.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        jProgressBar1.setString("");
        jProgressBar1.setStringPainted(true);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(status, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jProgressBar1, javax.swing.GroupLayout.PREFERRED_SIZE, 202, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jProgressBar1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(status)))
        );

        fileMenu.setText("File");

        installMenu.setText("Install modpack");
        installMenu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                installMenuActionPerformed(evt);
            }
        });
        fileMenu.add(installMenu);

        testMenu.setText("Test FTP");
        testMenu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                testMenuActionPerformed(evt);
            }
        });
        fileMenu.add(testMenu);

        quitMenu.setText("Quit");
        quitMenu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                quitMenuActionPerformed(evt);
            }
        });
        fileMenu.add(quitMenu);

        jMenuBar1.add(fileMenu);

        editMenu.setText("Edit");

        paramsMenu.setText("Java parameters");
        paramsMenu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                paramsMenuActionPerformed(evt);
            }
        });
        editMenu.add(paramsMenu);

        prefMenu.setText("Preferences");
        prefMenu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                prefMenuActionPerformed(evt);
            }
        });
        editMenu.add(prefMenu);

        jMenuBar1.add(editMenu);

        helpMenu.setText("Help");

        aboutMenu.setText("About");
        aboutMenu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                aboutMenuActionPerformed(evt);
            }
        });
        helpMenu.add(aboutMenu);

        jMenuBar1.add(helpMenu);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(tabPane, javax.swing.GroupLayout.DEFAULT_SIZE, 637, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(proceedButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(abortButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(tabPane, javax.swing.GroupLayout.DEFAULT_SIZE, 327, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(abortButton)
                    .addComponent(proceedButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void proceedButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_proceedButtonActionPerformed
        if ((state != STATE_OK) && (tabPane.getSelectedIndex() == 0) && (tabPane.getComponentCount() > 1)) {
            tabPane.setSelectedIndex(1);
        } else {
            ActionListener listener;
            listener = proceedListeners.get(proceedListeners.size() - 1);
            proceedListeners.remove(listener);
            proceedButton.setEnabled(!proceedListeners.isEmpty());
            if (!abortListeners.isEmpty()) {
                dropAbortListener(abortListeners.get(abortListeners.size() - 1));
            }
            if ((!proceedListeners.isEmpty()) && (proceedListeners.get(proceedListeners.size() - 1) == startMinecraftListener)) {
                proceedButton.setText("Start Launcher");
            } else {
                proceedButton.setText("Proceed");
            }
            listener.actionPerformed(evt);
        }
    }//GEN-LAST:event_proceedButtonActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        new Thread(() -> {
            try {
                ftpClient.close();
            } catch (NullPointerException ex) {
            }
            System.exit(0);
        }, "Quit").start();
    }//GEN-LAST:event_formWindowClosing

    private void testMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_testMenuActionPerformed
        if ((state & ~(STATE_FIRST | STATE_ERROR)) > 0) {
            return;
        }
        new Thread(() -> {
            int oldState = state;
            String oldStatusText = status.getText();
            state = STATE_WORKING;
            setStatusText("Testing");
            setIndeterminate();
            while (true) {
                int result;
                result = ftpClient.openDataChannel((ActionEvent e) -> {
                    byte[] buffer = new byte[4096];
                    InputStream is;
                    ArrayList<Byte> ab = new ArrayList<>();
                    int n;
                    if (e.getID() == FtpClient.FTP_OK) {
                        try {
                            is = ((Socket) e.getSource()).getInputStream();
                            while (true) {
                                n = is.read(buffer);
                                if (n < 0) {
                                    break;
                                }
                                advance(n);
                                for (int m = 0; m < n; ab.add(buffer[m++]));
                            }

                            EventQueue.invokeLater(() -> {
                                byte[] pic = new byte[ab.size()];
                                int m = 0;
                                for (Byte b : ab) {
                                    pic[m++] = b;
                                }
                                new Test(pic).setVisible(true);
                            });
                        } catch (IOException ex) {
                            Logger.getLogger(InstallerWindow.class.getName()).log(Level.SEVERE, "FTP test", ex);
                        } finally {
                            state = oldState;
                            setStatusText(oldStatusText);
                            reset();
                        }
                    }
                });
                switch (result) {
                    case FtpClient.FTP_OK: {
                        try {
                            result = ftpClient.retr("test/zoroark.png", (ActionEvent e) -> {
                                ftpClient.closeDataChannel();
                            });
                        } catch (NetworkException ex) {
                            Logger.getLogger(InstallerWindow.class.getName()).log(Level.SEVERE, null, ex);
                            ftpClient.close();
                            println("Timeout. Previous command wasn't received by the server. This is a network error. Please try again later.");
                            state = STATE_ERROR;
                            setStatusText("Error");
                        }
                    }
                    Logger.getLogger(InstallerWindow.class.getName()).log(Level.INFO, String.valueOf(result));
                    reset();
                    if (result >= 0) {
                        setMaximum(result);
                    } else {
                        ftpClient.abandonDataChannel();
                    }
                    break;
                    case FtpClient.FTP_TIMEOUT:
                        if (reconnect()) {
                            continue;
                        } else {
                            break;
                        }
                    default:
                        ftpClient.abandonDataChannel();
                }
                break;
            }
        }, "FTP Test").start();
    }//GEN-LAST:event_testMenuActionPerformed

    private void quitMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_quitMenuActionPerformed
        new Thread(() -> {
            try {
                ftpClient.close();
            } catch (NullPointerException ex) {
            }
            System.exit(0);
        }, "Quit").start();
    }//GEN-LAST:event_quitMenuActionPerformed

    private void prefMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_prefMenuActionPerformed
        switch (state & ~STATE_FIRST) {
            case STATE_OK:
            case STATE_ERROR:
                state |= STATE_CONFIG;
                tabPane.add("Configuration", configPanel);
                tabPane.setSelectedComponent(configPanel);
                setProceedListener((ActionEvent e) -> {
                    if (configPanel.commit()) {
                        state &= ~STATE_CONFIG;
                        try {
                            ftpClient.close();
                        } catch (NullPointerException ex) {
                        }
                        dropAllProceedListeners();
                        beginWithFTP();
                        tabPane.remove(configPanel);
                    }
                });
                setAbortListener((ActionEvent e) -> {
                    configPanel.revert();
                    tabPane.remove(configPanel);
                    state &= ~STATE_CONFIG;
                });
                break;
            case STATE_CONFIG:
                tabPane.setSelectedComponent(configPanel);
                break;
        }
    }//GEN-LAST:event_prefMenuActionPerformed

    private void installMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_installMenuActionPerformed
        if ((state & ~STATE_FIRST) == STATE_OK) {
            beginWithInstallation();
        }
    }//GEN-LAST:event_installMenuActionPerformed

    private void abortButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_abortButtonActionPerformed
        ActionListener listener;
        listener = abortListeners.get(abortListeners.size() - 1);
        abortListeners.remove(listener);
        abortButton.setEnabled(!abortListeners.isEmpty());
        if (!proceedListeners.isEmpty()) {
            dropProceedListener(proceedListeners.get(proceedListeners.size() - 1));
        }
        if ((!proceedListeners.isEmpty()) && (proceedListeners.get(proceedListeners.size() - 1) == startMinecraftListener)) {
            proceedButton.setText("Start Launcher");
        } else {
            proceedButton.setText("Proceed");
        }
        listener.actionPerformed(evt);
    }//GEN-LAST:event_abortButtonActionPerformed

    private void paramsMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_paramsMenuActionPerformed
        if ((state & ~STATE_FIRST) == STATE_OK) {
            if (!installPanel.changeJvmParameters()) {
                return;
            }
            state |= STATE_CONFIG;
            setStatusText("Config");
            tabPane.add(installPanel.getParameterPanel(), "JVM parameters");
            tabPane.setSelectedIndex(1);
        }
    }//GEN-LAST:event_paramsMenuActionPerformed

    private void aboutMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_aboutMenuActionPerformed
        new AboutBox(this, true).setVisible(true);
    }//GEN-LAST:event_aboutMenuActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton abortButton;
    private javax.swing.JMenuItem aboutMenu;
    private javax.swing.JMenu editMenu;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JMenuItem installMenu;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JProgressBar jProgressBar1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextArea logBox;
    private javax.swing.JMenuItem paramsMenu;
    private javax.swing.JMenuItem prefMenu;
    private javax.swing.JButton proceedButton;
    private javax.swing.JMenuItem quitMenu;
    private javax.swing.JLabel status;
    private javax.swing.JTabbedPane tabPane;
    private javax.swing.JMenuItem testMenu;
    // End of variables declaration//GEN-END:variables
    private static class PackDescriptionList extends ArrayList<PackDescription> {
    }
}
