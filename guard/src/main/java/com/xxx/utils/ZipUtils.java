package com.xxx.utils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created time : 2021/5/21 12:49.
 *
 * @author 10585
 */
public class ZipUtils {

    public static void unzip(InputStream stream, String pathname) {
        File destDir = new File(pathname);
        // create output directory if it doesn't exist
        if (!destDir.exists()) //noinspection ResultOfMethodCallIgnored
            destDir.mkdirs();

        //buffer for read and write data to file
        ZipInputStream zis = new ZipInputStream(stream);
        try {
            extract(zis, destDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void extract(ZipInputStream zip, File target) throws IOException {
        try {
            ZipEntry entry;

            while ((entry = zip.getNextEntry()) != null) {
                File file = new File(target, entry.getName());

                if (!file.toPath().normalize().startsWith(target.toPath())) {
                    throw new IOException("Bad zip entry");
                }

                if (entry.isDirectory()) {
                    file.mkdirs();
                    continue;
                }

                byte[] buffer = new byte[4096];
                file.getParentFile().mkdirs();
                BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file));
                int count;

                while ((count = zip.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                }

                out.close();
            }
        } finally {
            zip.close();
        }
    }

}