import android.content.res.Configuration;
import android.os.LocaleList;
import java.lang.reflect.Method;

/**
 * Sets emulator-5558's system language as its Settings' language picker does
 * (LocalePicker.updateLocales: the configuration's locales, userSetLocale, then
 * IActivityManager.updatePersistentConfiguration), run as the shell user through app_process:
 *   app_process -cp /data/local/tmp/setlocale.dex / SetLocale ur-PK      (or: SetLocale get)
 * The shell user holds CHANGE_CONFIGURATION. Wear OS 6's own Settings say "The watch language is the
 * same as your phone's", and no phone is paired with this emulator.
 */
public class SetLocale {
    public static void main(String[] args) throws Exception {
        Object am = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        Method get = am.getClass().getMethod("getConfiguration");
        Configuration c = (Configuration) get.invoke(am);
        if (!args[0].equals("get")) {
            c.setLocales(LocaleList.forLanguageTags(args[0]));
            Configuration.class.getField("userSetLocale").setBoolean(c, true);
            Method put = null;
            for (Method m : am.getClass().getMethods()) {
                if (m.getName().equals("updatePersistentConfigurationWithAttribution")) put = m;
            }
            if (put != null) {
                put.invoke(am, c, "com.android.shell", null);
            } else {
                am.getClass().getMethod("updatePersistentConfiguration", Configuration.class).invoke(am, c);
            }
        }
        System.out.println("system locales: " + ((Configuration) get.invoke(am)).getLocales().toLanguageTags());
    }
}
