/**
 *  Copyright 2010 by Benjamin J. Land (a.k.a. BenLand100)
 *
 *  This file is part of GameBoyColor.
 *
 *  GameBoyColor is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  GameBoyColor is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with GameBoyColor. If not, see <http://www.gnu.org/licenses/>.
 */

package gameboy;

import gameboy.memory.Segment;

/**
 *
 * @author benland100
 */
public class InterruptReg extends Segment {

    public static final int V_BLANK = 0x01;
    public static final int LCD_STAT = 0x02;
    public static final int TIMER = 0x4;
    public static final int SERIAL = 0x8;
    public static final int JOYPAD = 0x10;

    int reg;

    public InterruptReg() {
        reg = 0;
    }

    public boolean test(int mask) {
        return 0 != (reg & mask);
    }

    public void set(int mask) {
        reg |= mask;
    }

    public void reset(int mask) {
        reg &= ~mask;
    }

    public int readB(int pos) {
        return reg;
    }

    public void writeB(int pos, int val) {
        reg = val;
    }

}
