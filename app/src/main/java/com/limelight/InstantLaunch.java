package com.limelight;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

import com.limelight.computers.ComputerDatabaseManager;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.utils.CacheHelper;
import com.limelight.utils.Dialog;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.SpinnerDialog;

import java.io.StringReader;
import java.util.List;
import java.util.UUID;

public class InstantLaunch extends Activity {

    private String uuidString;
    private ComputerDetails computer;
    private ComputerManagerService.ComputerManagerBinder managerBinder;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            final ComputerManagerService.ComputerManagerBinder localBinder = ((ComputerManagerService.ComputerManagerBinder) binder);

            // Wait in a separate thread to avoid stalling the UI
            new Thread() {

                @Override
                public void run() {
                    // Wait for the binder to be ready
                    localBinder.waitForReady();

                    // Now make the binder visible
                    managerBinder = localBinder;

                    // Get the computer object
                    computer = managerBinder.getComputer(UUID.fromString(uuidString));

                    // Start updates
                    startComputerUpdates();
                }
            }.start();
        }

        public void onServiceDisconnected(ComponentName className) {
            managerBinder = null;
        }
    };

    private void startComputerUpdates() {
        if (managerBinder == null) {
            return;
        }

        managerBinder.startPolling(new ComputerManagerListener() {

            @Override
            public void notifyComputerUpdated(ComputerDetails details) {
                // Don't care about other computers
                if (!details.uuid.toString().equalsIgnoreCase(uuidString)) {
                    return;
                }

                if (details.state == ComputerDetails.State.ONLINE) {
                    try {
                        String lastRawApplist = CacheHelper.readInputStreamToString(CacheHelper.openCacheFileForInput(getCacheDir(), "applist", uuidString));
                        List<NvApp> applist = NvHTTP.getAppListByReader(new StringReader(lastRawApplist));
                        for (NvApp app : applist) {
                            AppView.AppObject appObject = new AppView.AppObject(app);
                            if (appObject.app.getAppName().toLowerCase().contains("windows")) {
                                ServerHelper.doStart(InstantLaunch.this, appObject.app, computer, managerBinder);
                                break;
                            }
                        }
                    }
                    catch (Exception e) {}

                    finish();
                }
                else if (details.state == ComputerDetails.State.OFFLINE) {
                    // The PC is unreachable now
                    InstantLaunch.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Display a toast to the user and quit the activity
                            Toast.makeText(InstantLaunch.this, getResources().getText(R.string.lost_connection), Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });
                }
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ComputerDatabaseManager db = new ComputerDatabaseManager(InstantLaunch.this);
        List<ComputerDetails> computers = db.getAllComputers();
        if (!computers.isEmpty()) {
            uuidString = computers.get(0).uuid.toString();

            // Bind to the computer manager service
            bindService(new Intent(this, ComputerManagerService.class), serviceConnection, Service.BIND_AUTO_CREATE);
        }
        else {
            Toast.makeText(this, "Windows instant launch not yet configured", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        SpinnerDialog.closeDialogs(this);
        Dialog.closeDialogs();

        if (managerBinder != null) {
            managerBinder.stopPolling();
            unbindService(serviceConnection);
        }
    }
}
