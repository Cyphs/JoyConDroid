package com.rdapps.gamepad;

import static android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE;
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
import static android.bluetooth.BluetoothAdapter.STATE_OFF;
import static android.bluetooth.BluetoothAdapter.STATE_ON;
import static android.bluetooth.BluetoothAdapter.STATE_TURNING_OFF;
import static android.bluetooth.BluetoothAdapter.STATE_TURNING_ON;
import static com.rdapps.gamepad.log.JoyConLog.log;
import static com.rdapps.gamepad.protocol.ControllerType.LEFT_JOYCON;
import static com.rdapps.gamepad.protocol.ControllerType.PRO_CONTROLLER;
import static com.rdapps.gamepad.protocol.ControllerType.RIGHT_JOYCON;
import static com.rdapps.gamepad.toast.ToastHelper.missingPermission;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.rdapps.gamepad.led.LedState;
import com.rdapps.gamepad.log.JoyConLog;
import com.rdapps.gamepad.nintendoswitch.ControllerFragment;
import com.rdapps.gamepad.nintendoswitch.CustomFragment;
import com.rdapps.gamepad.nintendoswitch.LeftJoyConFragment;
import com.rdapps.gamepad.nintendoswitch.ProControllerFragment;
import com.rdapps.gamepad.nintendoswitch.RightJoyConFragment;
import com.rdapps.gamepad.protocol.ControllerType;
import com.rdapps.gamepad.protocol.JoyController;
import com.rdapps.gamepad.service.BluetoothBroadcastReceiver;
import com.rdapps.gamepad.service.BluetoothControllerService;
import com.rdapps.gamepad.service.BluetoothControllerService.BluetoothControllerServiceBinder;
import com.rdapps.gamepad.toast.ToastHelper;
import com.rdapps.gamepad.util.EventUtils;
import java.util.Objects;

public class ControllerActivity extends AppCompatActivity {

    public static final String CONTROLLER_FRAGMENT_TAG = "CONTROLLER_FRAGMENT_TAG";
    public static final String CONTROLLER_TYPE = "CONTROLLER_TYPE";
    public static final String CUSTOM_UI = "CUSTOM_UI";
    public static final String CUSTOM_UI_URL = "CUSTOM_UI_URL";

    private static final String TAG = ControllerActivity.class.getName();

    private BluetoothAdapter bluetoothAdapter;

    private BluetoothBroadcastReceiver bluetoothBroadcastReceiver;
    private ControllerFragment controllerFragment;
    private ControllerType controllerType;

    private BluetoothControllerService bluetoothControllerService;

    private boolean askingDiscoverable;

    private final ActivityResultLauncher<Intent> requestBtEnableResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_CANCELED) {
                            Toast.makeText(getApplicationContext(), R.string.bt_enable_canceled,
                                    Toast.LENGTH_LONG).show();
                        }
                    });

    private final ActivityResultLauncher<Intent> requestBtDiscoveryResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_CANCELED) {
                            Toast.makeText(getApplicationContext(), R.string.bt_discovery_canceled,
                                    Toast.LENGTH_LONG).show();
                        }
                    });

    public ControllerActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!BuildConfig.VM) {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            enableFullscreen();
        }

        setContentView(R.layout.activity_left_joycon);

        Intent intent = getIntent();
        // Get Controller Type From Extras
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            controllerType = intent.getSerializableExtra(CONTROLLER_TYPE, ControllerType.class);
        } else {
            @SuppressWarnings("deprecation")
            ControllerType deprecatedType =
                    (ControllerType) intent.getSerializableExtra(CONTROLLER_TYPE);
            controllerType = deprecatedType;
        }
        if (controllerType == null) {
            controllerType = PRO_CONTROLLER;
        }

        boolean customUi = intent.getBooleanExtra(CUSTOM_UI, false);

        //Initialize Bluetooth Adapter
        initializeBluetooth();
        if (Objects.isNull(bluetoothAdapter)) {
            ToastHelper.bluetoothNotAvailable(getApplicationContext());
            finish();
            return;
        }

        FragmentManager supportFragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = supportFragmentManager.beginTransaction();

        if (customUi) {
            String url = intent.getStringExtra(CUSTOM_UI_URL);
            controllerFragment = CustomFragment.getInstance(url);
        } else {
            if (controllerType == LEFT_JOYCON) {
                controllerFragment = new LeftJoyConFragment();
            } else if (controllerType == RIGHT_JOYCON) {
                controllerFragment = new RightJoyConFragment();
            } else {
                controllerFragment = new ProControllerFragment();
            }
        }

        bluetoothControllerService = null;
        transaction.replace(R.id.content_fragment, controllerFragment, CONTROLLER_FRAGMENT_TAG);
        transaction.commit();

        askingDiscoverable = false;
    }

    private void initializeBluetooth() {
        this.bluetoothAdapter = getApplicationContext().getSystemService(BluetoothManager.class)
                .getAdapter();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        bluetoothBroadcastReceiver =
                new BluetoothBroadcastReceiver(new BluetoothBroadcastReceiverListener());
        registerReceiver(bluetoothBroadcastReceiver, intentFilter);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        log(TAG, "Config Changed");
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Objects.isNull(bluetoothAdapter)) {
            return;
        }
        pair();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (isBound) {
            try {
                unbindService(serviceConnection);
            } catch (Exception e) {
                JoyConLog.log(TAG, "Unbound crash: ", e);
            }
            isBound = false;
        }

        if (Objects.isNull(bluetoothAdapter)) {
            return;
        }

        if (Objects.nonNull(bluetoothBroadcastReceiver)) {
            unregisterReceiver(bluetoothBroadcastReceiver);
        }
    }

    private void pair() {
        if (!bluetoothAdapter.isEnabled()) {
            try {
                Intent enableIntent = new Intent(ACTION_REQUEST_ENABLE);
                requestBtEnableResultLauncher.launch(enableIntent);
            } catch (SecurityException ex) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    missingPermission(getApplicationContext(),
                            Manifest.permission.BLUETOOTH_CONNECT);
                    log(TAG, "Missing permission", ex);
                }
            }
        } else {
            setupHidService();
        }
    }

    private boolean isBound = false;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            log(TAG, name.getClassName());
            bluetoothControllerService = ((BluetoothControllerServiceBinder) service).getService();
            completeBind();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            log(TAG, name.getClassName());
            bluetoothControllerService = null;
        }
    };

    private void setupHidService() {
        Intent controllerIntent =
                new Intent(getApplicationContext(), BluetoothControllerService.class);
        controllerIntent.putExtra(BluetoothControllerService.DEVICE_TYPE, controllerType);
        startService(controllerIntent);
        bindService(controllerIntent, serviceConnection, BIND_AUTO_CREATE);
    }


    private void completeBind() {
        bluetoothControllerService.setControllerActivity(this);
        JoyController device = bluetoothControllerService.getDevice();
        controllerFragment.setDevice(device);
        controllerFragment.registerAccelerometerListener();
        controllerFragment.registerGyroscopeListener();
    }


    public void startHidDeviceDiscovery() {
        try {
            if (bluetoothAdapter.getScanMode() != SCAN_MODE_CONNECTABLE_DISCOVERABLE
                    && !askingDiscoverable) {
                startDiscoverable(60);
            }
        } catch (SecurityException ex) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                missingPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_SCAN);
                log(TAG, "Missing permission", ex);
            }
        }
    }

    public void stopHidDeviceDiscovery() {
        try {
            if (bluetoothAdapter.getScanMode() == SCAN_MODE_CONNECTABLE_DISCOVERABLE
                    && askingDiscoverable) {
                startDiscoverable(1);
                askingDiscoverable = false;
            }
        } catch (SecurityException ex) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                missingPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_SCAN);
                log(TAG, "Missing permission", ex);
            }
        }
    }

    private void startDiscoverable(int duration) {
        try {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, duration);
            requestBtDiscoveryResultLauncher.launch(discoverableIntent);
            askingDiscoverable = true;
        } catch (SecurityException ex) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                missingPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_ADVERTISE);
                log(TAG, "Missing permission", ex);
            }
        }
    }

    public void sync() {
        startHidDeviceDiscovery();
    }

    public void showAmiiboPicker() {
        if (Objects.nonNull(controllerFragment)) {
            runOnUiThread(() -> controllerFragment.showAmiiboPicker());
        }
    }

    public void setPlayerLights(LedState led1, LedState led2, LedState led3, LedState led4) {
        controllerFragment.setPlayerLights(led1, led2, led3, led4);
    }


    private class BluetoothBroadcastReceiverListener
            extends BluetoothBroadcastReceiver.BbrListener {

        @Override
        public void stateChangedTo(int state) {
            switch (state) {
                case STATE_TURNING_ON:
                    log(TAG, "Bluetooth turning on");
                    break;
                case STATE_ON:
                    log(TAG, "Bluetooth on");
                    setupHidService();
                    break;
                case STATE_TURNING_OFF:
                    log(TAG, "Bluetooth turning off");
                    break;
                case STATE_OFF:
                    log(TAG, "Bluetooth off");
                    break;
                default:
            }
        }

        @Override
        public void scanModeChanged(int mode) {
            askingDiscoverable = false;
            if (mode != SCAN_MODE_CONNECTABLE_DISCOVERABLE && bluetoothControllerService != null
                    && !bluetoothControllerService.isConnected()) {
                startHidDeviceDiscovery();
            }
        }

        @Override
        public void onBonded(BluetoothDevice device) {
            if (bluetoothControllerService != null && !bluetoothControllerService.isConnected()) {
                bluetoothControllerService.connect(device);
            }
        }
    }

    public void onDeviceNotCompatible() {
        runOnUiThread(() -> new AlertDialog.Builder(ControllerActivity.this)
                .setMessage(R.string.device_is_not_compatible)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    //TODO Report phone model
                })
                .show());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (EventUtils.isGamePadSource(event.getSource())) {
            if (controllerFragment != null && controllerFragment.handleKey(keyCode, event)) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (EventUtils.isGamePadSource(event.getSource())) {
            if (controllerFragment != null && controllerFragment.handleKey(keyCode, event)) {
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }


    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (EventUtils.isGamePadSource(event.getSource())) {
            if (controllerFragment != null && controllerFragment.handleGenericMotionEvent(event)) {
                return true;
            }
        }
        return super.onGenericMotionEvent(event);
    }

    private void enableFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            final Window window = getWindow();
            WindowCompat.setDecorFitsSystemWindows(window, false);

            WindowInsetsControllerCompat controller =
                    WindowCompat.getInsetsController(window, window.getDecorView());

            controller.hide(WindowInsetsCompat.Type.statusBars()
                    | WindowInsetsCompat.Type.navigationBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        } else {
            enableFullscreenLegacy();
        }
    }

    @SuppressWarnings("deprecation")
    private void enableFullscreenLegacy() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }
}
