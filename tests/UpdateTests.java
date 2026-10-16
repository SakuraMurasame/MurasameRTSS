import io.perfoverlay.app.UpdatePolicy;

public final class UpdateTests {
    private static int assertions;
    private static void check(boolean value,String message) { assertions++; if(!value)throw new AssertionError(message); }
    public static void main(String[] args) {
        for(String[] pair:new String[][]{{"v1.4.0","1.3.2"},{"1.10.0","1.9.9"},{"2.0.0","1.99.99"},{"1.2.11","1.2.9"},{"V1.4.0","1.3.2"}}) {
            check(UpdatePolicy.newer(pair[0],pair[1]),"Numeric version ordering");
            check(!UpdatePolicy.newer(pair[1],pair[0]),"Never downgrade");
        }
        check(!UpdatePolicy.newer("v1.4.0","1.4.0"),"Equal version");
        check(!UpdatePolicy.newer("v1.2.1","1.4.0"),"Published release older than local APK");
        for(String bad:new String[]{"","latest","1.4","1.4.0-rc1","1.4.0+test","v1.4.0/evil"," 1.4.0","1.4.0\n","999999999999.0.0","-1.0.0"}) {
            boolean failed=false;try{UpdatePolicy.version(bad);}catch(IllegalArgumentException e){failed=true;}check(failed,"Reject unsupported tag "+bad);
        }
        String tag="v1.4.0", name="MurasameRTSS-1.4.0.apk", path=UpdatePolicy.REPO+"/releases/download/"+tag+"/"+name;
        check(UpdatePolicy.apk(tag,name,path),"Accept matching APK from configured repository");
        check(UpdatePolicy.page(tag).equals(UpdatePolicy.REPO+"/releases/tag/v1.4.0"),"Construct fixed-source release page");
        for(String invalid:new String[]{path.replace("https:","http:"),path.replace("github.com","github.com.evil.test"),path.replace("github.com","evil@github.com"),path+"?redirect=evil",path+"#evil",path.replace("SakuraMurasame","other"),path.replace("v1.4.0","v1.3.0"),path.replace("github.com/","github.com:443/"),"https://evil.test/"+name,"javascript:alert(1)","file:///tmp/"+name})
            check(!UpdatePolicy.apk(tag,name,invalid),"Reject invalid asset link");
        check(!UpdatePolicy.apk(tag,"app.apk",path),"Reject unrelated artifact");
        check(!UpdatePolicy.apk(tag,"SHA256SUMS-1.4.0.txt",path),"Do not offer checksum as installer");
        long last=100000000L;
        check(UpdatePolicy.due(last,0,true),"First launch");
        check(!UpdatePolicy.due(last+86399999,last,true),"Throttle successful automatic checks");
        check(UpdatePolicy.due(last+86400000,last,true),"Check after 24 hours");
        check(!UpdatePolicy.due(last+3599999,last,false),"Throttle failures");
        check(UpdatePolicy.due(last+3600000,last,false),"Retry after an hour");
        check(UpdatePolicy.due(last-10000,last,true),"Clock rollback cannot disable checking forever");
        check(UpdatePolicy.retryAt(last,null,null)==last+60000,"Rate limit fallback");
        check(UpdatePolicy.retryAt(last,"120",null)==last+120000,"Honor Retry-After");
        check(UpdatePolicy.retryAt(last,null,Long.toString((last+180000)/1000))==last+180000,"Honor rate-limit reset");
        check(UpdatePolicy.retryAt(last,"99999999",null)==last+86400000,"Bound server retry period");
        check(UpdatePolicy.retryAt(last,"bad","bad")==last+60000,"Malformed headers");
        System.out.println("PASS: "+assertions+" update assertions (versions, source restriction, intervals, rate limits)");
    }
}
