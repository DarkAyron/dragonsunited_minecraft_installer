
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

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
/**
 *
 * @author ayron
 */
public class PostDownload {

    public boolean invoke(String installDir, String packDir) {

        File forgeDir = new File(installDir, "versions/1.7.10-Forge10.13.4.1614-1.7.10");
        if (forgeDir.isDirectory()) {
            System.out.println("Nothing more to do.");
            return true;
        } else {
            File lp = new File(installDir, "launcher_profiles.json");
            if (!lp.isFile()) {
                try (FileWriter fw = new FileWriter(lp)) {
                    fw.write("{\"profiles\":{}}");
                } catch (IOException ex) {
                    Logger.getLogger(PostDownload.class.getName()).log(Level.SEVERE, "Create launcher_profiles.json", ex);
                    return false;
                }
            }
            return installForge(installDir, packDir);
        }
    }

    private boolean installForge(String installDir, String packDir) {
        try {
            URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{new File(packDir, "forge-1.7.10-10.13.4.1614-1.7.10-installer.jar").toURI().toURL()});
            Class serverClass = urlClassLoader.loadClass("net.minecraftforge.installer.ServerInstall");
            Field headless = serverClass.getField("headless");
            headless.setBoolean(serverClass, true);
            Class installerClass = urlClassLoader.loadClass("net.minecraftforge.installer.ClientInstall");
            Constructor constructor = installerClass.getConstructor();
            Object installer = constructor.newInstance();
            Method method = installerClass.getMethod("run", new Class[]{File.class});
            System.out.println("Please wait while forge is being installed...");
            return (boolean) method.invoke(installer, new File(installDir));
        } catch (MalformedURLException | ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            Logger.getLogger(PostDownload.class.getName()).log(Level.SEVERE, "PostDownload", ex);
            System.out.println("Executing forge installer failed.");
            return false;
        } catch (NoSuchFieldException ex) {
            Logger.getLogger(PostDownload.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }
}
