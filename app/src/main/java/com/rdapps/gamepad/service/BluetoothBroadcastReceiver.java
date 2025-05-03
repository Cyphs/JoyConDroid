package com.rdapps.gamepad.service;

import static android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_FINISHED;
import static android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_STARTED;
import static android.bluetooth.BluetoothAdapter.ACTION_SCAN_MODE_CHANGED;
import static android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED;
import static android.bluetooth.BluetoothAdapter.EXTRA_SCAN_MODE;
import static android.bluetooth.BluetoothAdapter.EXTRA_STATE;
import static android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED;
import static android.bluetooth.BluetoothDevice.ACTION_FOUND;
import static android.bluetooth.BluetoothDevice.ACTION_PAIRING_REQUEST;
import static com.rdapps.gamepad.log.JoyConLog.log;
import static com.rdapps.gamepad.service.BluetoothControllerService.NINTENDO_SWITCH;
import static com.rdapps.gamepad.toast.ToastHelper.missingPermission;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BluetoothBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = BluetoothBroadcastReceiver.class.getName();

    private final BbrListener listener;

    public BluetoothBroadcastReceiver(BbrListener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (listener == null) {
            return;
        }
        String action = intent.getAction();
        log(TAG, "Intent action: " + action);
        BluetoothDevice device;
        switch (action) {
            case ACTION_DISCOVERY_STARTED:
                listener.discoveryStarted();
                break;
            case ACTION_DISCOVERY_FINISHED:
                listener.discoveryFinished();
                break;
            case ACTION_FOUND:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice.class);
                } else {
                    @SuppressWarnings("deprecation")
                    BluetoothDevice deprecatedDevice = intent
                            .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    device = deprecatedDevice;
                }
                listener.deviceFound(device);
                break;
            case ACTION_STATE_CHANGED:
                int state = intent.getIntExtra(EXTRA_STATE, -1);
                listener.stateChangedTo(state);
                break;
            case ACTION_SCAN_MODE_CHANGED:
                int scanMode = intent.getIntExtra(EXTRA_SCAN_MODE, -1);
                listener.scanModeChanged(scanMode);
                break;
            case ACTION_PAIRING_REQUEST:
                break;
            case ACTION_BOND_STATE_CHANGED:
                try {
                    int bondState = intent.getExtras().getInt(BluetoothDevice.EXTRA_BOND_STATE);
                    log(TAG, "Bond State: " + bondState);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,
                                BluetoothDevice.class);
                    } else {
                        @SuppressWarnings("deprecation")
                        BluetoothDevice deprecatedDevice = intent
                                .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        device = deprecatedDevice;
                    }
                    log(TAG, "Device: " + device);

                    if (NINTENDO_SWITCH.equalsIgnoreCase(device.getName())
                            && bondState == BluetoothDevice.BOND_BONDED) {
                        listener.onBonded(device);
                    }
                } catch (SecurityException ex) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        missingPermission(context, Manifest.permission.BLUETOOTH_CONNECT);
                        log(TAG, "Missing permission", ex);
                    }
                }
                break;
            default:
                listener.unknownAction(context, intent);
                break;
        }
    }

    public abstract static class BbrListener {
        public void discoveryStarted() {
            log(TAG, "Discovery Started");
        }

        public void discoveryFinished() {
            log(TAG, "Discovery Finished");
        }

        public void deviceFound(BluetoothDevice device) {
            try {
                log(TAG, "Device Found"
                        + " - Address: " + device.getAddress() + " Name:" + device.getName());
            } catch (SecurityException e) {
                log(TAG, "Missing permission", e);
            }
        }

        public void unknownAction(Context context, Intent intent) {
            log(TAG, "Unknown Action");
        }

        public void stateChangedTo(int state) {
            log(TAG, "State Changed To :" + state);
        }

        public void scanModeChanged(int scanMode) {
            log(TAG, "Scan Mode Changed: " + scanMode);
        }

        public void onPairingRequest(BluetoothDevice pairingDevice, int variant) {
            try {
                log(TAG, "Pairing device :" + pairingDevice.getName() + " Variant: " + variant);
            } catch (SecurityException ex) {
                log(TAG, "Missing permission", ex);
            }
        }

        public void onBonded(BluetoothDevice device) {
            try {
                log(TAG, "Bonded device :" + device.getName());
            } catch (SecurityException e) {
                log(TAG, "Missing permission", e);
            }
        }
    }
}
