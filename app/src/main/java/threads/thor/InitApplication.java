package threads.thor;

import android.app.Application;

import androidx.annotation.NonNull;

import com.jrummyapps.android.shell.CommandResult;
import com.jrummyapps.android.shell.Shell;

import org.torproject.android.binary.TorResourceInstaller;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.Executors;

import threads.LogUtils;
import threads.thor.core.events.EVENTS;
import threads.thor.ipfs.IPFS;
import threads.thor.utils.AdBlocker;

public class InitApplication extends Application {

    private static final String TAG = InitApplication.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();

        AdBlocker.init(getApplicationContext());

        IPFS ipfs = IPFS.getInstance(getApplicationContext());
        Executors.newSingleThreadExecutor().submit(() -> {
           ipfs.bootstrap(10,10);
        });

        if (LogUtils.isDebug()) {
            IPFS.logCacheDir(getApplicationContext());
            IPFS.logBaseDir(getApplicationContext());
        }


        try {
            TorResourceInstaller torResourceInstaller = new TorResourceInstaller(
                    getApplicationContext(), getFilesDir());

            File fileTorBin = torResourceInstaller.installResources();
            File fileTorRc = torResourceInstaller.getTorrcFile();
            // printFile(fileTorRc);


            boolean success = fileTorBin != null && fileTorBin.canExecute();

            if (!success) {
                EVENTS.getInstance(getApplicationContext()).tor();
                return;
            }


            File fileTorRcCustom = new File(fileTorRc.getAbsolutePath() + ".custom");

            String extraLines = "\n" +
                    "DisableNetwork 0" +
                    "\n" +
                    "RunAsDaemon 0" +
                    "\n" +
                    "SOCKSPort " + Settings.SOCKSPort +
                    "\n";


            updateTorConfigCustom(fileTorRcCustom, extraLines);


            Executors.newSingleThreadExecutor().submit(() -> {

                try {

                    // printFile(fileTorRcCustom);

                    boolean runTorShellCmd = runTorShellCmd(fileTorBin, fileTorRcCustom);

                    if (!runTorShellCmd) {
                        EVENTS.getInstance(getApplicationContext()).tor();
                    }
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                    EVENTS.getInstance(getApplicationContext()).tor();
                }
            });

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            EVENTS.getInstance(getApplicationContext()).tor();
        }
    }


    public void printFile(File fileTorRcCustom) throws IOException {

        List<String> lines = Files.readAllLines(fileTorRcCustom.toPath());
        for (String line : lines) {
            LogUtils.error(TAG, line);
        }
    }

    private void updateTorConfigCustom(@NonNull File fileTorRcCustom,
                                       @NonNull String extraLines) throws IOException {
        FileWriter fos = new FileWriter(fileTorRcCustom, false);
        PrintWriter ps = new PrintWriter(fos);
        ps.print(extraLines);
        ps.flush();
        ps.close();
    }

    private void logNotice(String notice) {
        LogUtils.error(TAG, notice);
    }

    private void logNotice(String notice, Exception e) {
        logNotice(notice);
        LogUtils.error(TAG, e);
    }

    private boolean runTorShellCmd(File fileTor, File fileTorr) throws Exception {
        File appCacheHome = getDir(Settings.DIRECTORY_TOR_DATA, Application.MODE_PRIVATE);

        if (!fileTorr.exists()) {
            logNotice("torr not installed: " + fileTorr.getCanonicalPath());
            return false;
        }

        String torCmdString = fileTor.getCanonicalPath()
                + " DataDirectory " + appCacheHome.getCanonicalPath()
                + " --defaults-torrc " + fileTorr;


        try {
            exec(torCmdString + " --verify-config");
        } catch (Exception e) {
            logNotice("Tor configuration did not verify: " + e.getMessage(), e);
            return false;
        }

        int exitCode;
        try {
            exitCode = exec(torCmdString);
        } catch (Exception e) {
            logNotice("Tor was unable to start: " + e.getMessage(), e);
            return false;
        }

        if (exitCode != 0) {
            logNotice("Tor did not start. Exit:" + exitCode);
            return false;
        }

        return true;
    }

    private int exec(String cmd) throws Exception {
        CommandResult shellResult = Shell.run(cmd);
        if (!shellResult.isSuccessful()) {
            throw new Exception("Error: " + shellResult.exitCode +
                    " ERR=" + shellResult.getStderr() +
                    " OUT=" + shellResult.getStdout());
        }
        return shellResult.exitCode;
    }

}
