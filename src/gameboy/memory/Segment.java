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

package gameboy.memory;

/**
 *
 * @author benland100
 */
public abstract class Segment {

    public int readB(int pos) {
        throw new RuntimeException("SegFault: Read on " + Integer.toHexString(pos));
    }

    public void writeB(int pos, int val) {
        throw new RuntimeException("SegFault: Write on " + Integer.toHexString(pos));
    }

}
