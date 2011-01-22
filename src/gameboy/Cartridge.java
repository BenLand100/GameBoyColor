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
