import timber.log.Timber;

public class LeUtil {

    public static void initTimber() {
      Timber.plant(new Timber.DebugTree());
    }
}