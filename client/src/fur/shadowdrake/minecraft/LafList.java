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

import java.util.HashMap;
import javax.swing.ComboBoxModel;
import javax.swing.event.ListDataListener;

/**
 *
 * @author ayron
 */
public class LafList extends HashMap<String, String> implements ComboBoxModel<String> {

    private int selectedIndex;
    private final String[] nameList = new String[]{
        "Autumn",
        "Business",
        "Business Black Steel",
        "Business Blue Steel",
        "Creme",
        "Creme Coffee",
        "Dust",
        "Dust Coffee",
        "Gemini",
        "Mariner",
        "Moderate",
        "Nebula",
        "Nebula Brick Wall",
        "Office Black 2007",
        "Office Silver 2007",
        "Sahara",
        "Office Blue 2007",
        "Magellan",
        "Challenger Deep",
        "Emerald Dusk",
        "Raven",
        "Graphite",
        "Graphite Glass",
        "Graphite Aqua",
        "Twilight",
        "Metal",
        "Motif",
        "Nimbus"
    };
    private final HashMap<String, Integer> nameIndex = new HashMap<>();

    public LafList() {
        super();
        selectedIndex = 0;
        put("Autumn", "org.pushingpixels.substance.api.skin.SubstanceAutumnLookAndFeel");
        put("Business", "org.pushingpixels.substance.api.skin.SubstanceBusinessLookAndFeel");
        put("Business Black Steel", "org.pushingpixels.substance.api.skin.SubstanceBusinessBlackSteelLookAndFeel");
        put("Business Blue Steel", "org.pushingpixels.substance.api.skin.SubstanceBusinessBlueSteelLookAndFeel");
        put("Creme", "org.pushingpixels.substance.api.skin.SubstanceCremeLookAndFeel");
        put("Creme Coffee", "org.pushingpixels.substance.api.skin.SubstanceCremeCoffeeLookAndFeel");
        put("Dust", "org.pushingpixels.substance.api.skin.SubstanceDustLookAndFeel");
        put("Dust Coffee", "org.pushingpixels.substance.api.skin.SubstanceDustCoffeeLookAndFeel");
        put("Gemini", "org.pushingpixels.substance.api.skin.SubstanceGeminiLookAndFeel");
        put("Mariner", "org.pushingpixels.substance.api.skin.SubstanceMarinerLookAndFeel");
        put("Moderate", "org.pushingpixels.substance.api.skin.SubstanceModerateLookAndFeel");
        put("Nebula", "org.pushingpixels.substance.api.skin.SubstanceNebulaLookAndFeel");
        put("Nebula Brick Wall", "org.pushingpixels.substance.api.skin.SubstanceNebulaBrickWallLookAndFeel");
        put("Office Black 2007", "org.pushingpixels.substance.api.skin.SubstanceOfficeBlack2007LookAndFeel");
        put("Office Silver 2007", "org.pushingpixels.substance.api.skin.SubstanceOfficeSilver2007LookAndFeel");
        put("Sahara", "org.pushingpixels.substance.api.skin.SubstanceSaharaLookAndFeel");
        put("Office Blue 2007", "org.pushingpixels.substance.api.skin.SubstanceOfficeBlue2007LookAndFeel");
        put("Magellan", "org.pushingpixels.substance.api.skin.SubstanceMagellanLookAndFeel");
        put("Challenger Deep", "org.pushingpixels.substance.api.skin.SubstanceChallengerDeepLookAndFeel");
        put("Emerald Dusk", "org.pushingpixels.substance.api.skin.SubstanceEmeraldDuskLookAndFeel");
        put("Raven", "org.pushingpixels.substance.api.skin.SubstanceRavenLookAndFeel");
        put("Graphite", "org.pushingpixels.substance.api.skin.SubstanceGraphiteLookAndFeel");
        put("Graphite Glass", "org.pushingpixels.substance.api.skin.SubstanceGraphiteGlassLookAndFeel");
        put("Graphite Aqua", "org.pushingpixels.substance.api.skin.SubstanceGraphiteAquaLookAndFeel");
        put("Twilight", "org.pushingpixels.substance.api.skin.SubstanceTwilightLookAndFeel");
        put("Metal", "javax.swing.plaf.metal.MetalLookAndFeel");
        put("Motif", "com.sun.java.swing.plaf.motif.MotifLookAndFeel");
        put("Nimbus", "com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
        for (int n = 0; n < nameList.length; n++) {
            nameIndex.put(nameList[n], n);
        }
    }

    @Override
    public void setSelectedItem(Object anItem) {
        selectedIndex = nameIndex.get(anItem);
    }

    @Override
    public Object getSelectedItem() {
        return nameList[selectedIndex];
    }

    @Override
    public int getSize() {
        return size();
    }

    @Override
    public String getElementAt(int index) {
        return nameList[index];
    }

    @Override
    public void addListDataListener(ListDataListener l) {
    }

    @Override
    public void removeListDataListener(ListDataListener l) {
    }
}
