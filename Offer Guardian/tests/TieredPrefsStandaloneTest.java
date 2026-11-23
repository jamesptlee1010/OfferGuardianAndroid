package com.personal.offerguardian;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;

/** Exercises actual Prefs keys, legacy fallback, independent 3-tier persistence. */
public final class TieredPrefsStandaloneTest {
    private static int count=0;
    private static void eq(Object v,Object want,String why){count++;if(!java.util.Objects.equals(want,v))throw new AssertionError(why+" got "+v+" expected "+want);}
    private static final class MemoryPrefs implements SharedPreferences,SharedPreferences.Editor {
        private final Map<String,Object> values=new HashMap<>();
        public boolean getBoolean(String k,boolean d){return values.containsKey(k)?(boolean)values.get(k):d;}
        public float getFloat(String k,float d){return values.containsKey(k)?(float)values.get(k):d;}
        public int getInt(String k,int d){return values.containsKey(k)?(int)values.get(k):d;}
        public long getLong(String k,long d){return values.containsKey(k)?(long)values.get(k):d;}
        public String getString(String k,String d){return values.containsKey(k)?(String)values.get(k):d;}
        public Editor edit(){return this;}
        public Editor putBoolean(String k,boolean v){values.put(k,v);return this;}
        public Editor putFloat(String k,float v){values.put(k,v);return this;}
        public Editor putInt(String k,int v){values.put(k,v);return this;}
        public Editor putLong(String k,long v){values.put(k,v);return this;}
        public Editor putString(String k,String v){values.put(k,v);return this;}
        public void apply(){} public boolean commit(){return true;}
    }
    public static void main(String[] args){
        MemoryPrefs p=new MemoryPrefs();
        eq(Prefs.tieredEnabled(p),false,"upgrade preserves legacy rules until saved");
        p.edit().putBoolean(Prefs.RULE_PRICE,true).putFloat(Prefs.THRESHOLD,40.0f)
                .putBoolean(Prefs.RULE_DELIVERIES,true).putInt(Prefs.RULE_MIN_DELIVERIES,2).commit();
        eq(Prefs.rules(p).minDeliveries,2,"old mandatory gate preserved in legacy config");
        TieredOfferRules before=Prefs.tieredRules(p);
        eq(before.configFor(1).deliveriesOn,false,"tiered config does not inherit global count gate");
        eq(before.configFor(1).minPrice,40.0,"initial values inherited from legacy");
        p.edit().putBoolean(Prefs.TIERED_ACTIVE,true)
                .putBoolean(Prefs.tierKey(1,"price_on"),true)
                .putFloat(Prefs.tierKey(1,"min_price"),25.0f)
                .putBoolean(Prefs.tierKey(1,"hourly_on"),false)
                .putFloat(Prefs.tierKey(2,"min_price"),35.0f)
                .putBoolean(Prefs.tierKey(2,"hourly_on"),true)
                .putFloat(Prefs.tierKey(2,"min_hourly"),45.0f)
                .putString(Prefs.tierKey(2,"mode"),"ALL")
                .putFloat(Prefs.tierKey(3,"min_price"),50.0f)
                .putBoolean(Prefs.tierKey(3,"distance_on"),true)
                .putFloat(Prefs.tierKey(3,"max_distance"),10.0f)
                .putBoolean(Prefs.tierKey(3,"enabled"),false)
                .putLong(Prefs.TIERED_GENERATION,1).commit();
        eq(Prefs.tieredEnabled(p),true,"saved tier active");
        TieredOfferRules after=Prefs.tieredRules(p);
        eq(after.configFor(1).minPrice,25.0,"solo threshold");
        eq(after.configFor(2).minPrice,35.0,"double threshold");
        eq(after.configFor(2).minHourly,45.0,"double hourly threshold");
        eq(after.configFor(2).mode,OfferRuleEngine.Mode.ALL,"double mode");
        eq(after.enabled(3),false,"triple disabled preserved");
        eq(after.configFor(3),null,"disabled tier cannot make a decision");
        eq(Prefs.rules(p).minDeliveries,2,"legacy original still unchanged");
        eq(Prefs.rules(p).minPrice,40.0,"legacy payment unchanged");
        eq(after.evaluate(20,20,2000,1).outcome,OfferRuleEngine.Outcome.DECLINE,"persisted solo decline");
        eq(after.evaluate(50,20,2000,3).outcome,OfferRuleEngine.Outcome.REVIEW,"disabled triple manual review");
        eq(Prefs.ruleSummary(p).contains("4+: manual review"),true,"history/home summary follows tier mode");
        eq(p.getLong(Prefs.TIERED_GENERATION,0),1L,"rule revision maintained");
        p.edit().putBoolean(Prefs.TIERED_ACTIVE,false).putLong(Prefs.TIERED_GENERATION,2L).commit();
        eq(Prefs.tieredEnabled(p),false,"restore previous global conditions");
        eq(Prefs.rules(p).minDeliveries,2,"restored legacy minimum deliveries");
        eq(Prefs.tieredRules(p).configFor(1).minPrice,25.0,"tier drafts survive restoring legacy");
        System.out.println("TieredPrefs: "+count+" assertions passed");
    }
}
