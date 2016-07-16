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

/**
 *
 * @author ayron
 */
public interface LogOutput {
    public void print(String text);
    public void println(String text);
    public void backspace();
    public void setStatusText(String text);
    public void setMaximum(int value);
    public void setValue(int value);
    public void advance(int value);
    public void setIndeterminate();
    public void reset();
    public void showPercentage(boolean b);
}
