package io.perfoverlay.app;

import java.net.URI;
import java.util.regex.*;

/** Update rules independent of Android and network state. */
public final class UpdatePolicy {
    public static final String REPO="https://github.com/SakuraMurasame/MurasameRTSS";
    public static final String API="https://api.github.com/repos/SakuraMurasame/MurasameRTSS/releases/latest";
    private static final Pattern VERSION=Pattern.compile("[vV]?([0-9]{1,9})\\.([0-9]{1,9})\\.([0-9]{1,9})");
    public static long[] version(String text) {
        Matcher m=VERSION.matcher(text==null?"":text);
        if(!m.matches())throw new IllegalArgumentException("Invalid stable version");
        return new long[]{Long.parseLong(m.group(1)),Long.parseLong(m.group(2)),Long.parseLong(m.group(3))};
    }
    public static boolean newer(String remote,String installed) {
        long[] a=version(remote),b=version(installed);
        for(int i=0;i<3;i++)if(a[i]!=b[i])return a[i]>b[i];
        return false;
    }
    public static String page(String tag) { version(tag); return REPO+"/releases/tag/"+tag; }
    public static boolean apk(String tag,String name,String url) {
        try {
            version(tag); String numeric=tag.replaceFirst("^[vV]","");
            String expected="MurasameRTSS-"+numeric+".apk";
            return expected.equals(name) && new URI(REPO+"/releases/download/"+tag+"/"+expected).equals(new URI(url));
        } catch(Exception e) { return false; }
    }
    public static boolean due(long now,long last,boolean success) {
        return last<=0 || now<last || now-last>=(success?86400000L:3600000L);
    }
    public static long retryAt(long now,String retryAfter,String reset) {
        long delay=60000;
        try { delay=Math.max(delay,Long.parseLong(retryAfter)*1000); } catch(Exception ignored) { }
        try { delay=Math.max(delay,Long.parseLong(reset)*1000-now); } catch(Exception ignored) { }
        return now+Math.min(86400000L,delay);
    }
    private UpdatePolicy() { }
}
