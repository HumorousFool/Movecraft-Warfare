package net.countercraft.movecraft.combat.movecraftcombat.localisation;

import net.countercraft.movecraft.warfare.MovecraftWarfare;
import net.countercraft.movecraft.warfare.config.Config;

import java.io.*;
import java.util.Properties;
import java.util.logging.Level;

public class I18nSupport {
    private static Properties langFile;

    public static void init() {
        langFile = new Properties();

        File langDirectory = new File(MovecraftWarfare.getInstance().getDataFolder().getAbsolutePath() + "/localisation");
        if (!langDirectory.exists()) {
            langDirectory.mkdirs();
        }

        InputStream stream = null;

        try {
            stream = new FileInputStream(langDirectory.getAbsolutePath()+"/mcclang_" + Config.Locale + ".properties");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        if (stream == null) {
            MovecraftWarfare.getInstance().getLogger().log(Level.SEVERE, "Critical Error in localisation system!");
            MovecraftWarfare.getInstance().getServer().shutdown();
        }

        try {
            langFile.load(stream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getInternationalisedString(String key) {
        String ret = langFile.getProperty(key);
        if (ret != null) {
            return ret;
        } else {
            return key;
        }
    }
}
