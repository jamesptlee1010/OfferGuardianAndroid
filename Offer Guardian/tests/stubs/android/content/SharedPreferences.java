package android.content;
/** Host-only compile stub for preference migration tests. */
public interface SharedPreferences {
    boolean getBoolean(String k,boolean defaultValue);
    float getFloat(String k,float defaultValue);
    int getInt(String k,int defaultValue);
    long getLong(String k,long defaultValue);
    String getString(String k,String defaultValue);
    Editor edit();
    interface Editor {
        Editor putBoolean(String k,boolean v);
        Editor putFloat(String k,float v);
        Editor putInt(String k,int v);
        Editor putLong(String k,long v);
        Editor putString(String k,String v);
        void apply();
        boolean commit();
    }
}
