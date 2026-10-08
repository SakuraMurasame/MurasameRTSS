package io.perfoverlay.test;

import io.perfoverlay.app.HardwareProbe;
import io.perfoverlay.app.FpsProbe;

/** ADB shell validation only; excluded from the installed app. */
public final class DeviceProbe {
    public static void main(String[] args) throws Exception {
        System.out.println("uid=" + android.os.Process.myUid());
        System.out.println(new HardwareProbe().sample(3));
        try (FpsProbe fps = new FpsProbe()) {
            for (int i = 0; i < 8; i++) {
                System.out.println(fps.sample().toString());
                Thread.sleep(500);
            }
        }
    }
}
