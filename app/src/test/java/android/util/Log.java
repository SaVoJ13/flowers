package android.util;

/**
 * Локальна тестова реалізація android.util.Log для виконання тестів на JVM без необхідності
 * вмикати глобальний isReturnDefaultValues = true, який міг би маскувати виклики Android SDK.
 */
public final class Log {
    private Log() {}

    public static boolean isLoggable(String tag, int level) {
        return false;
    }

    public static int v(String tag, String msg) { return 0; }
    public static int v(String tag, String msg, Throwable tr) { return 0; }

    public static int d(String tag, String msg) { return 0; }
    public static int d(String tag, String msg, Throwable tr) { return 0; }

    public static int i(String tag, String msg) { return 0; }
    public static int i(String tag, String msg, Throwable tr) { return 0; }

    public static int w(String tag, String msg) { return 0; }
    public static int w(String tag, String msg, Throwable tr) { return 0; }
    public static int w(String tag, Throwable tr) { return 0; }

    public static int e(String tag, String msg) { return 0; }
    public static int e(String tag, String msg, Throwable tr) { return 0; }

    public static int wtf(String tag, String msg) { return 0; }
    public static int wtf(String tag, Throwable tr) { return 0; }
    public static int wtf(String tag, String msg, Throwable tr) { return 0; }
}
