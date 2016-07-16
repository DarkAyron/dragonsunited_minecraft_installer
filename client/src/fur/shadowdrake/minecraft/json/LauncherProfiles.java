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
package fur.shadowdrake.minecraft.json;

import java.util.HashMap;
import java.util.ArrayList;

/**
 *
 * @author ayron
 */
public class LauncherProfiles {
    public HashMap<String, Profile> profiles;
    public String selectedProfile;
    public String clientToken;
    public HashMap<String, AuthDB> authenticationDatabase;
    public String selectedUser;
    public LauncherVersion launcherVersion;
    
    public class LauncherVersion {
        String name;
        String format;
    }
    
    public class AuthDB {
        String username;
        String accessToken;
        String userid;
        String uuid;
        String displayName;
    }
    
    public class Profile {
        public String name;
        public String gameDir;
        public String lastVersionId;
        public String javaDir;
        public String javaArgs;
        public boolean useHopperCrashService;
        public String launcherVisibilityOnGameClose;
        public HashMap<String, Integer> resolution;
        public ArrayList<String> allowedReleaseTypes;
    } 
}
