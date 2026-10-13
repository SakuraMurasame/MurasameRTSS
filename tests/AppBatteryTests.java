import io.perfoverlay.app.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;

public final class AppBatteryTests {
    static int checks;
    static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
    static void close(double actual, double expected, String label) { check(Math.abs(actual-expected)<1e-8, label+"="+actual); }
    static AppBatteryData.Sample sample(long time, String epoch, double computed, double total, double fg, double bg) {
        return new AppBatteryData.Sample(1000000+time, time, epoch, 5000, computed, 4, true,
            Map.of(10123,new AppBatteryData.Usage(total,fg,bg,List.of("example.one","example.shared"))));
    }
    public static void main(String[] args) throws Exception {
        String raw = "Statistics since last charge:\n  Start clock time: 2026-10-12-17-41-34\n"
            + "Estimated power use (mAh):\n Capacity: 5300, Computed drain: 1375, actual drain: 1375\n"
            + " UID u0a123: 347 fg: 147 (1h 8m 1s 482ms) bg: 21.3 (44m 8s) fgs: 16.9 cached: 6.31\n"
            + "  cpu: 80 fg: 50 bg: 30\n UID 1000: 236 bg: 236\n UID u10a9: 2.50 fg: 1.25e-3 bg: 0.020\n"
            + " UID u0a999: 5.0\n UID u0a777: 0\n UID u0a123: 999\n";
        AppBatteryData.Sample parsed=AppBatteryParser.parse(raw,1000000,1000,4);
        check(parsed.epoch.equals("2026-10-12-17-41-34"),"Reset epoch"); close(parsed.capacity,5300,"Capacity");
        check(parsed.apps.size()==5,"Only UID total lines, no duplicates");
        close(parsed.apps.get(10123).total,347,"mAh"); close(parsed.apps.get(10123).foreground,147,"Foreground");
        close(parsed.apps.get(10123).background,44.51,"Background includes foreground service and cached");
        close(parsed.apps.get(1000).foreground,0,"Omitted zero state");
        close(parsed.apps.get(1010009).foreground,0.00125,"Scientific state notation");
        check(Double.isNaN(parsed.apps.get(10999).foreground),"Missing states stay unclassified");
        check(AppBatteryParser.uid("u0a123")==10123,"App UID"); check(AppBatteryParser.uid("u1a23")==110023,"Second user");
        check(AppBatteryParser.uid("u0i4")==99004,"Isolated UID"); check(AppBatteryParser.uid("u10s1000")==1001000,"System UID user");
        check(AppBatteryParser.uid("u999999999a1")==-1,"UID overflow"); check(AppBatteryParser.uid("NaN")==-1,"Bad UID");
        boolean rejected=false; try { AppBatteryParser.parse("Permission Denial",0,0,4); } catch (Exception e) { rejected=true; }
        check(rejected,"Permission error cannot become zero drain");
        AppBatteryData.Sample a=sample(1000,"epoch",100,10,5,2), b=sample(61000,"epoch",103,11,5.4,2.2);
        AppBatteryData.Interval interval=AppBatteryData.between(a,b); AppBatteryData.Drain d=interval.apps.get(10123);
        close(d.total,1,"Delta not cumulative"); close(d.foreground,.4,"Foreground delta"); close(d.background,.2,"Background delta");
        close(d.unknown,.4,"Unattributed state preserved"); close(interval.watts(d.total),.24,"mAh to average W");
        close(AppBatteryData.percent(1,4),25,"Percent denominator"); check(Double.isNaN(AppBatteryData.percent(0,0)),"Zero denominator is unavailable");
        check(!AppBatteryData.between(a,sample(61000,"reset",103,11,5,2)).valid,"Epoch reset breaks chart");
        check(!AppBatteryData.between(a,sample(61000,"epoch",99,11,5,2)).valid,"Global counter reset");
        check(!AppBatteryData.between(a,a).valid,"No division by zero");
        check(AppBatteryData.between(a,sample(61000,"epoch",103,9,5,2)).apps.isEmpty(),"UID correction skipped");
        AppBatteryData.Sample missing=new AppBatteryData.Sample(0,1000,"epoch",5000,100,4,true,Map.of());
        check(AppBatteryData.between(missing,b).apps.isEmpty(),"New UID gets baseline, not old drain");
        AppBatteryData.Sample reused=new AppBatteryData.Sample(0,61000,"epoch",5000,103,4,true,
            Map.of(10123,new AppBatteryData.Usage(11,5.4,2.2,List.of("example.reassigned"))));
        check(AppBatteryData.between(a,reused).apps.isEmpty(),"UID reassignment gets baseline");
        AppBatteryData.Interval unavailable=AppBatteryData.between(a,sample(61000,"epoch",103,11,Double.NaN,Double.NaN));
        close(unavailable.apps.get(10123).unknown,1,"No fabricated state split");
        close(AppBatteryData.between(a,sample(61000,"epoch",103,11,9,4)).apps.get(10123).unknown,1,"Inconsistent state corrections stay unclassified");
        check(!AppBatteryData.between(AppBatteryData.Sample.gap(0,1000),b).valid,"Denied interval breaks chart");
        check(AppBatteryData.summarize(List.of(interval),1000,61000).size()==1,"Shared UID counted once");
        check(AppBatteryData.summarize(List.of(interval),1001,61000).isEmpty(),"Boundary not prorated");
        close(AppBatteryData.summarize(List.of(interval),1000,61000).get(10123).total,1,"Selected range mAh");
        Path folder=Paths.get(args[0],"battery-tests-"+UUID.randomUUID()); Files.createDirectories(folder);
        AppBatteryStore store=new AppBatteryStore(folder.toFile()); String id=store.begin(1000000);
        store.append(id,a); store.append(id,b); List<AppBatteryData.Sample> saved=store.read(id);
        check(saved.size()==2,"Persistence"); check(saved.get(0).apps.get(10123).packages.size()==2,"Shared identities saved");
        close(AppBatteryData.between(saved.get(0),saved.get(1)).apps.get(10123).total,1,"Replay");
        boolean activeRejected=false; try { store.delete(id); } catch (IllegalStateException e) { activeRejected=true; }
        check(activeRejected,"Cannot delete active record"); store.end();
        Files.write(folder.resolve(id),new byte[]{0,0,0,50,1,2,3},StandardOpenOption.APPEND);
        check(new AppBatteryStore(folder.toFile()).read(id).size()==2,"Truncated tail after process death");
        boolean pathRejected=false; try { store.read("../other.mrb"); } catch (IllegalArgumentException e) { pathRejected=true; }
        check(pathRejected,"Record path constrained"); store.delete(id); check(store.list().isEmpty(),"Delete saved record");
        String longId=store.begin(2000000); store.append(longId,a);
        boolean limit=false;
        try { store.append(longId,sample(1000+24*3600_000L+1,"epoch",103,11,5,2)); } catch (IOException e) { limit=true; }
        check(limit,"24-hour record bound"); check(store.read(longId).size()==1,"Limit preserves saved baseline"); store.end(); store.delete(longId);
        AppBatteryData.Sample noVoltage=new AppBatteryData.Sample(b.wall,b.elapsed,b.epoch,b.capacity,b.computed,Double.NaN,true,b.apps);
        check(Double.isNaN(AppBatteryData.between(a,noVoltage).watts(1)),"Missing voltage has no fabricated watts");
        close(AppBatteryData.between(a,noVoltage).apps.get(10123).total,1,"mAh remains available without voltage");
        check(AppBatteryData.between(b,a).apps.isEmpty(),"Time reversal breaks intervals");
        String zeroId=store.begin(3000000); store.append(zeroId,a); store.end();
        try (RandomAccessFile f=new RandomAccessFile(folder.resolve(zeroId).toFile(),"rw")) {
            long last=f.length()-1; f.seek(last); int original=f.read(); f.seek(last); f.write(original^1);
        }
        boolean corrupt=false; try { store.read(zeroId); } catch (IOException e) { corrupt=true; }
        check(corrupt,"Checksummed corruption rejected"); store.delete(zeroId);
        for (int i=0;i<120;i++) {
            double mah=(i+1)*.013;
            AppBatteryData.Interval tested=AppBatteryData.between(sample(1000,"epoch",100,10,5,2),sample(61000,"epoch",101,10+mah,5+mah/3,2+mah/4));
            AppBatteryData.Drain drain=tested.apps.get(10123);
            close(drain.total,mah,"Delta series "+i); close(drain.foreground+drain.background+drain.unknown,mah,"Conservation "+i);
            close(tested.watts(mah),mah*.24,"Power units "+i);
        }
        if (args.length>1) {
            AppBatteryData.Sample device=AppBatteryParser.parse(Files.readString(Paths.get(args[1])),1,1,4);
            check(device.apps.size()>0,"Actual device format"); System.out.println("Device parser: "+device.apps.size()+" UIDs, capacity="+device.capacity);
        }
        System.out.println("PASS: "+checks+" app battery assertions");
    }
}
