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

import gameboy.memory.RomPage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Vector;

/**
 *
 * @author benland100
 */
public class Cartridge {

    Vector<RomPage> pages = new Vector<RomPage>();

    public Cartridge(String path) {
        try {
            File f = new File(path);
            long size = f.length();
            InputStream in = new FileInputStream(f);
            byte[] buff = new byte[0x4000];
            for (long idx = 0; idx < size; idx += 0x4000) {
                int sum = 0, len;
                do {
                    if ((len = in.read(buff, sum, 0x4000 - sum)) == -1) break;
                } while ((sum += len) < 0x4000);
                pages.add(new RomPage(buff));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int pages() {
        return pages.size();
    }

    public RomPage page(int i) {
        return pages.get(i);
    }

}
