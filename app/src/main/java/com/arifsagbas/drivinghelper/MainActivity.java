package com.arifsagbas.drivinghelper;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Timestamp;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** @noinspection ALL*/
public class MainActivity extends AppCompatActivity implements SensorEventListener{

    private static final int SAMPLE_RATE = 44100; // Ses örnekleme hızı
    private static final int BUFFER_SIZE = 4096;  // Buffer boyutu
    private AudioRecord audioRecord;
    private short[] buffer = new short[BUFFER_SIZE];
    private Handler handler;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS = 2;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private MediaRecorder mediaRecorder;
    String mediaOutputFilePath;
    InputStream inputStream;
    OutputStream outputStream;
    BufferedWriter writer;
    String rpmHex = "", loadHex = "", speedHex = "", coolantTempHex = "", intakeAirTempHex = "",
            mafHex = "", mapHex = "", temperatureHex = "", voltageHex = "";
    int rpm = 0, speed = 0, load = 0, coolantTemp = 0, intakeAirTemp = 0, map = 0,temperature = 0, mafRaw = 0;
    double maf = 0.0;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // UID of OBD device
    private static final String OBDII_DEVICE_NAME = "OBDII"; // Name of OBD device
    SensorManager sensorManager;
    LocationManager locationManager;
    LocationListener locationListener;
    double latitude = 0.0, longitude = 0.0, altitude = 0.0, gpsSpeed = 0.0;

    double rms = 0.0, zcr = 0.0, decibel = 0.0, avgAmplitude = 0.0, signalEnergy = 0.0, dominantFrequency = 0.0, spectralCentroid = 0.0,
            spectralSpread = 0.0, spectralFlatness = 0.0, spectralEntropy = 0.0, skewness = 0.0, kurtosis = 0.0, bandwidth = 0.0, rollOff = 0.0;
    String strGpsSpeed = "";
    float accX = 0,accY = 0,accZ = 0,gravX = 0,gravY = 0,gravZ = 0,linearX = 0,linearY = 0,linearZ = 0,
            light_0 = 0,prox = 0,gyrX = 0,gyrY = 0,gyrZ =0, magX = 0,magY = 0,magZ = 0;
    String int_ref = "NoData", ref = "NoData", lanes = "NoData", maxSpeed = "NoData", name = "NoData",
            surface = "NoData", minSpeed = "NoData", highway = "NoData", toll = "NoData", oneWay = "NoData";
        boolean isReceivedInt_ref = false, isReceivedRef = false, isReceivedLanes = false, isReceivedMaxSpeed = false, isReceivedName = false,
                isReceivedSurface = false, isReceivedMinSpeed = false, isReceivedHighway = false, isReceivedToll = false, isReceivedOneWay = false;
    boolean connected = false;
    TextView tvOverpassAPI, tv, tvSpeed, tvLoad, tvRpm, tvCoolantTemp, tvIntakeAirTemp, tvMaf, tvMap, tvTemperature, tvVoltage;
    ImageView imgViewSpeedLimit;
    ObdTask obdTask;
    String fileSaveName = "", csvFileName = "";
    int counterIncludeIdle = 0, counterExcludeIdle = 0, counterMedia = 0;
    StringBuilder res, dataIncludeIdle, dataExcludeIdle;
    Timestamp timestamp;
    File root, directoryIncludeIdle, file, directoryExcludeIdle, directoryMediaRecord;
    Button connectButton, disconnectButton;
    EditText etCsvFileName;
    ExecutorService audioExecutor;

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); //alwaysOn

        tvOverpassAPI = findViewById(R.id.overpassAPITv);
        tv = findViewById(R.id.tv);
        tvSpeed = findViewById(R.id.speedTv);
        tvRpm = findViewById(R.id.rpmTv);
        tvLoad = findViewById(R.id.loadTv);
        tvCoolantTemp = findViewById(R.id.coolantTempTv);
        tvMaf = findViewById(R.id.mafTv);
        tvMap = findViewById(R.id.mapTv);
        tvTemperature = findViewById(R.id.temperatureTv);
        tvIntakeAirTemp = findViewById(R.id.intakeAirTempTv);
        tvVoltage = findViewById(R.id.voltageTv);
        connectButton = findViewById(R.id.connectButton);
        disconnectButton = findViewById(R.id.disconnectButton);
        etCsvFileName = findViewById(R.id.csvFileNameEt);
        imgViewSpeedLimit = findViewById(R.id.speedLimitImgView);

        audioExecutor = Executors.newSingleThreadExecutor();
        handler = new Handler();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        res = new StringBuilder();
        dataExcludeIdle = new StringBuilder();
        dataIncludeIdle = new StringBuilder();

        root = Environment.getExternalStorageDirectory();
        directoryExcludeIdle = new File(root.getAbsolutePath()+"/ECU_Data/excludeIdle");
        if (!directoryExcludeIdle.exists())
            directoryExcludeIdle.mkdirs();
        directoryIncludeIdle = new File(root.getAbsolutePath()+"/ECU_Data/includeIdle");
        if (!directoryIncludeIdle.exists())
            directoryIncludeIdle.mkdirs();
        /*directoryMediaRecord = new File(root.getAbsolutePath()+"/ECU_Data/mediaRecord");
        if (!directoryMediaRecord.exists())
            directoryMediaRecord.mkdirs();*/

        counterIncludeIdle = 0;
        counterExcludeIdle = 0;
        counterMedia = 0;

        imgViewSpeedLimit.setImageResource(R.drawable.sign_no_data);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        startRecording();

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                counterExcludeIdle = 0;
                counterIncludeIdle = 0;
                counterMedia = 0;
                dataExcludeIdle.setLength(0);
                dataIncludeIdle.setLength(0);
                if (bluetoothAdapter == null) {
                    Toast.makeText(MainActivity.this, "Bluetooth not supported.", Toast.LENGTH_SHORT).show();
                }
                if (!bluetoothAdapter.isEnabled()) {
                    startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT);
                } else {
                    csvFileName = etCsvFileName.getText().toString();
                    checkPermissionsAndConnect();
                }
            }
        });//connectButton
        disconnectButton.setEnabled(false);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectButton.setEnabled(true);
                disconnectButton.setEnabled(false);
                etCsvFileName.setEnabled(true);
                connected = false;
                obdTask.cancel(true);
                sensorManager.unregisterListener(MainActivity.this,sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
                sensorManager.unregisterListener(MainActivity.this,sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY));
                sensorManager.unregisterListener(MainActivity.this,sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION));
                sensorManager.unregisterListener(MainActivity.this,sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE));
                sensorManager.unregisterListener(MainActivity.this,sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));
                sensorManager.unregisterListener(MainActivity.this,sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY));
                sensorManager.unregisterListener(MainActivity.this,sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT));
                locationManager.removeUpdates(locationListener);
                /*if (mediaRecorder != null) {
                    mediaRecorder.stop();
                    mediaRecorder.release();
                    mediaRecorder = null;
                }*/
            }
        });//disconnectButton
    }//onCreate

    private void checkPermissionsAndConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){

                ActivityCompat.requestPermissions(this, new String[]{
                        android.Manifest.permission.BLUETOOTH_CONNECT,
                        android.Manifest.permission.BLUETOOTH_SCAN,
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.RECORD_AUDIO
                }, REQUEST_PERMISSIONS);
            } else {
                connectToOBDII();
            }
        } else {
            connectToOBDII();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                connectToOBDII();
            } else {
                Toast.makeText(this, "Necessary permissions were not granted.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void connectToOBDII() {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        BluetoothDevice device = null;
        for (BluetoothDevice pairedDevice : pairedDevices) {
            if (OBDII_DEVICE_NAME.equals(pairedDevice.getName())) {
                device = pairedDevice;
                break;
            }
        }
        if (device == null) {
            Toast.makeText(this, "OBDII device not found.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            bluetoothSocket.connect();
            inputStream = bluetoothSocket.getInputStream();
            outputStream = bluetoothSocket.getOutputStream();
            Toast.makeText(this, "Connection successful.", Toast.LENGTH_SHORT).show();
            connected = true;

            connectButton.setEnabled(false);
            etCsvFileName.setEnabled(false);
            disconnectButton.setEnabled(true);

            //activate GPS with 1-second interval
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            locationListener = new MyLocationListener();
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 10, locationListener);

            //activate motion sensors. this setting acquires 20 data in 1-second //saniyede 50 veri alıyorum? 0.o
            sensorManager.registerListener(this,sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION),50000);
            sensorManager.registerListener(this,sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY),50000);
            sensorManager.registerListener(this,sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),50000);
            sensorManager.registerListener(this,sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),50000);
            sensorManager.registerListener(this,sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT),50000);
            sensorManager.registerListener(this,sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY),50000);
            sensorManager.registerListener(this,sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) , 50000);

            //active recording sound
            /*timestamp = new Timestamp(System.currentTimeMillis());
            mediaOutputFilePath = directoryMediaRecord.toString() + "/" + csvFileName+"_"+timestamp.toString().split("\\.")[0].replaceAll(" ","_").replaceAll("-","").replaceAll(":","") + ".wav";
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioChannels(2); // Stereo
            mediaRecorder.setAudioSamplingRate(44100); // 44.1 kHz
            mediaRecorder.setAudioEncodingBitRate(192000); // Yüksek kalite
            mediaRecorder.setOutputFile(mediaOutputFilePath);
            mediaRecorder.prepare();
            mediaRecorder.start();*/

            obdTask = new ObdTask();
            obdTask.execute();

        } catch (IOException e) {
            Log.e("MainActivity", "Connection error", e);
            Toast.makeText(this, "Connection error.", Toast.LENGTH_SHORT).show();
            connected = false;
        }
    }

    public int convertHexCode(String code, int last){
        String ret = "";
        String codeX = code.replaceAll(" ","");//clear spaces
        for (int i = codeX.length(); i > (codeX.length() - last); i--){
            ret = codeX.charAt(i-1) + ret;
        }
        return Integer.parseInt(ret,16);
    }
    public String getObdResult(String command) throws IOException {
        command += "\r";
        outputStream.write(command.getBytes());
        outputStream.flush();
        res.setLength(0);
        int b;
        while ((b = inputStream.read()) != -1) {
            char c = (char) b;
            if (c == '>') break; // End of response
            res.append(c);
        }
        return res.toString().trim();
    }//getObdResult

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
        } catch (IOException e) {
            Log.e("MainActivity", "Socket close error.", e);
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_GYROSCOPE:
                gyrX = event.values[0];
                gyrY = event.values[1];
                gyrZ = event.values[2];
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                magX = event.values[0];
                magY = event.values[1];
                magZ = event.values[2];
                break;
            case Sensor.TYPE_LINEAR_ACCELERATION:
                linearX = event.values[0];
                linearY = event.values[1];
                linearZ = event.values[2];
                break;
            case Sensor.TYPE_LIGHT:
                light_0 = event.values[0];
                break;
            case Sensor.TYPE_PROXIMITY:
                prox = event.values[0];
                break;
            case Sensor.TYPE_GRAVITY:
                gravX = event.values[0];
                gravY = event.values[1];
                gravZ = event.values[2];
                break;
            case Sensor.TYPE_ACCELEROMETER:
                accX = event.values[0];
                accY = event.values[1];
                accZ = event.values[2];

                //tv.setText(String.format("AccX: %s\tAccY: %s\tAccZ: %s", accX, accY, accZ));

                if (counterIncludeIdle == 0){//Burası 5 saniyede bir güncellense yeterlidir. Continuous değerler gelmeyecek.
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                URL url = new URL("https://overpass-api.de/api/interpreter?data=[out:json];way[\"highway\"](around:10," + latitude + "," + longitude + ");out body;");
                                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                                try {
                                    BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                                    String inputLine;
                                    StringBuilder response = new StringBuilder();
                                    while ((inputLine = in.readLine()) != null) {
                                        response.append(inputLine);
                                    }
                                    in.close();

                                    JSONObject jsonObject = new JSONObject(response.toString());
                                    JSONArray elements = jsonObject.getJSONArray("elements");

                                    for (int i = 0; i < elements.length(); i++) {
                                        JSONObject element = elements.getJSONObject(i);

                                        if (element.has("tags")) {
                                            JSONObject tags = element.getJSONObject("tags");

                                            tags.keys().forEachRemaining(key -> {
                                                try {
                                                    if (key.equals("maxspeed")) {
                                                        maxSpeed = tags.getString(key);
                                                        isReceivedMaxSpeed = true;
                                                    }
                                                    if (key.equals("int_ref")){
                                                        int_ref = tags.getString(key);
                                                        isReceivedInt_ref = true;
                                                    }
                                                    if (key.equals("ref")){
                                                        ref = tags.getString(key);
                                                        isReceivedRef = true;
                                                    }

                                                    if (key.equals("lanes")){
                                                        lanes = tags.getString(key);
                                                        isReceivedLanes = true;
                                                    }
                                                    if (key.equals("name")){
                                                        name = tags.getString(key);
                                                        isReceivedName = true;
                                                    }
                                                    if (key.equals("minspeed")){
                                                        minSpeed = tags.getString(key);
                                                        isReceivedMinSpeed = true;
                                                    }
                                                    if (key.equals("highway")){
                                                        highway = tags.getString(key);
                                                        isReceivedHighway = true;
                                                    }
                                                    if (key.equals("oneway")){
                                                        oneWay = tags.getString(key);
                                                        isReceivedOneWay = true;
                                                    }
                                                    if (key.equals("toll")){
                                                        toll = tags.getString(key);
                                                        isReceivedToll = true;
                                                    }
                                                    if (key.equals("surface")){
                                                        surface = tags.getString(key);
                                                        isReceivedSurface = true;
                                                    }
                                                } catch (JSONException e) {
                                                    throw new RuntimeException(e);
                                                }
                                            });
                                        }
                                        break;
                                    }
                                    if(!isReceivedSurface) surface = "NoData";
                                    if(!isReceivedMinSpeed) minSpeed = "NoData";
                                    if(!isReceivedToll) toll = "NoData";
                                    if(!isReceivedHighway) highway = "NoData";
                                    if(!isReceivedName) name = "NoData";
                                    if(!isReceivedOneWay) oneWay = "NoData";
                                    if(!isReceivedLanes) lanes = "NoData";
                                    if(!isReceivedMaxSpeed) maxSpeed = "NoData";
                                    if(!isReceivedRef) ref = "NoData";
                                    if(!isReceivedInt_ref) int_ref = "NoData";
                                    isReceivedInt_ref = false;
                                    isReceivedRef = false;
                                    isReceivedSurface = false;
                                    isReceivedLanes = false;
                                    isReceivedMaxSpeed = false;
                                    isReceivedName = false;
                                    isReceivedHighway = false;
                                    isReceivedToll = false;
                                    isReceivedMinSpeed = false;
                                    isReceivedOneWay = false;
                                    //maxSpeed rules
                                    if (maxSpeed.equals("NoData")){
                                        if (highway.equals("primary")) maxSpeed = "90";
                                        else if (highway.equals("secondary")) maxSpeed = "50";
                                        else if (highway.equals("tertiary")) maxSpeed = "30";
                                        else if (highway.equals("residential")) maxSpeed = "20";
                                        else if (highway.equals("service")) maxSpeed = "10";
                                    }
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            switch (maxSpeed){
                                                case "10":
                                                    imgViewSpeedLimit.setImageResource(R.drawable.sign_10);
                                                    break;
                                                case "20":
                                                    imgViewSpeedLimit.setImageResource(R.drawable.sign_20);
                                                    break;
                                                case "30":
                                                    imgViewSpeedLimit.setImageResource(R.drawable.sign_30);
                                                    break;
                                                case "40":
                                                    imgViewSpeedLimit.setImageResource(R.drawable.sign_40);
                                                    break;
                                                case "50":
                                                    imgViewSpeedLimit.setImageResource(R.drawable.sign_50);
                                                    break;
                                                case "60":
                                                    imgViewSpeedLimit.setImageResource(R.drawable.sign_60);
                                                    break;
                                                case "70":
                                                    imgViewSpeedLimit.setImageResource(R.drawable.sign_70);
                                                    break;
                                                case "80":
                                                    imgViewSpeedLimit.setImageResource(R.drawable.sign_80);
                                                    break;
                                                case "90":
                                                    imgViewSpeedLimit.setImageResource(R.drawable.sign_90);
                                                    break;
                                                case "100":
                                                    imgViewSpeedLimit.setImageResource(R.drawable.sign_100);
                                                    break;
                                                case "110":
                                                    imgViewSpeedLimit.setImageResource(R.drawable.sign_110);
                                                    break;
                                                case "120":
                                                    imgViewSpeedLimit.setImageResource(R.drawable.sign_120);
                                                    break;
                                                case "130":
                                                    imgViewSpeedLimit.setImageResource(R.drawable.sign_130);
                                                    break;
                                                case "140":
                                                    imgViewSpeedLimit.setImageResource(R.drawable.sign_140);
                                                    break;
                                                case "82":
                                                    imgViewSpeedLimit.setImageResource(R.drawable.sign_82);
                                                    break;
                                                default:
                                                    imgViewSpeedLimit.setImageResource(R.drawable.sign_no_data);
                                                    tvSpeed.setBackgroundResource(R.drawable.background_with_border);
                                                    break;

                                            }//switch maxSpeed
                                            tv.setText("Surface: " + surface + "\tOneway: " + oneWay);
                                            tvOverpassAPI.setText("Highway info: " + highway + "\n" + name);
                                        }
                                    });
                                } finally {
                                    urlConnection.disconnect();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                     });// executor execute
                }// if counterIncludeIdle == 0

                /*counterMedia++;
                if (counterMedia == 500){
                    //save sounds approximately 5 sec window
                    if (mediaRecorder != null) {
                        mediaRecorder.stop();
                        mediaRecorder.release();
                        mediaRecorder = null;
                    }
                    mediaOutputFilePath = directoryMediaRecord.toString() + "/" + csvFileName+"_"+timestamp.toString().split("\\.")[0].replaceAll(" ","_").replaceAll("-","").replaceAll(":","") + ".wav";
                    mediaRecorder = new MediaRecorder();
                    mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                    mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
                    mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                    mediaRecorder.setAudioChannels(2); // Stereo
                    mediaRecorder.setAudioSamplingRate(44100); // 44.1 kHz
                    mediaRecorder.setAudioEncodingBitRate(192000); // Yüksek kalite
                    mediaRecorder.setOutputFile(mediaOutputFilePath);
                    try {
                        mediaRecorder.prepare();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    mediaRecorder.start();
                    counterMedia = 0;
                }*/

                timestamp = new Timestamp(System.currentTimeMillis());

                dataIncludeIdle.append(accX).append(";").append(accY).append(";").append(accZ).append(";");
                dataIncludeIdle.append(gravX).append(";").append(gravY).append(";").append(gravZ).append(";").append(linearX).append(";").append(linearY).append(";").append(linearZ).append(";");
                dataIncludeIdle.append(gyrX).append(";").append(gyrY).append(";").append(gyrZ).append(";").append(magX).append(";").append(magY).append(";").append(magZ).append(";");
                dataIncludeIdle.append(light_0).append(";").append(prox).append(";");
                dataIncludeIdle.append(latitude).append(";").append(longitude).append(";").append(altitude).append(";").append(strGpsSpeed).append(";");
                dataIncludeIdle.append(rpm).append(";").append(load).append(";").append(speed).append(";").append(coolantTemp).append(";").append(intakeAirTemp).append(";");
                dataIncludeIdle.append(maf).append(";").append(map).append(";").append(temperature).append(";").append(voltageHex).append(";").append(timestamp.toString()).append(";");
                dataIncludeIdle.append(maxSpeed).append(";").append(highway).append(";").append(name).append(";").append(oneWay).append(";").append(ref).append(";");
                dataIncludeIdle.append(int_ref).append(";").append(lanes).append(";").append(minSpeed).append(";").append(surface).append(";").append(toll).append(";");
                dataIncludeIdle.append(rms).append(";").append(zcr).append(";").append(decibel).append(";").append(avgAmplitude).append(";").append(signalEnergy).append(";");
                dataIncludeIdle.append(dominantFrequency).append(";").append(spectralCentroid).append(";").append(spectralSpread).append(";").append(spectralFlatness).append(";");
                dataIncludeIdle.append(spectralEntropy).append(";").append(skewness).append(";").append(kurtosis).append(";").append(bandwidth).append(";").append(rollOff).append("\n");
                counterIncludeIdle++;

                if (speed != 0){
                    dataExcludeIdle.append(accX).append(";").append(accY).append(";").append(accZ).append(";");
                    dataExcludeIdle.append(gravX).append(";").append(gravY).append(";").append(gravZ).append(";").append(linearX).append(";").append(linearY).append(";").append(linearZ).append(";");
                    dataExcludeIdle.append(gyrX).append(";").append(gyrY).append(";").append(gyrZ).append(";").append(magX).append(";").append(magY).append(";").append(magZ).append(";");
                    dataExcludeIdle.append(light_0).append(";").append(prox).append(";");
                    dataExcludeIdle.append(latitude).append(";").append(longitude).append(";").append(altitude).append(";").append(strGpsSpeed).append(";");
                    dataExcludeIdle.append(rpm).append(";").append(load).append(";").append(speed).append(";").append(coolantTemp).append(";").append(intakeAirTemp).append(";");
                    dataExcludeIdle.append(maf).append(";").append(map).append(";").append(temperature).append(";").append(voltageHex).append(";").append(timestamp.toString()).append(";");
                    dataExcludeIdle.append(maxSpeed).append(";").append(highway).append(";").append(name).append(";").append(oneWay).append(";").append(ref).append(";");
                    dataExcludeIdle.append(int_ref).append(";").append(lanes).append(";").append(minSpeed).append(";").append(surface).append(";").append(toll).append(";");
                    dataExcludeIdle.append(rms).append(";").append(zcr).append(";").append(decibel).append(";").append(avgAmplitude).append(";").append(signalEnergy).append(";");
                    dataExcludeIdle.append(dominantFrequency).append(";").append(spectralCentroid).append(";").append(spectralSpread).append(";").append(spectralFlatness).append(";");
                    dataExcludeIdle.append(spectralEntropy).append(";").append(skewness).append(";").append(kurtosis).append(";").append(bandwidth).append(";").append(rollOff).append("\n");
                    counterExcludeIdle++;
                }// speed

                if (counterIncludeIdle % 50 == 0){//Önceden 30'du yani 1.5 saniyede bir. Fakat saniyede 50 geliyor.
                    if (!maxSpeed.equals("NoData")){
                        if (speed <= Integer.parseInt(maxSpeed))
                            tvSpeed.setBackgroundResource(R.drawable.background_green);
                        else if ((speed > Integer.parseInt(maxSpeed)) && (speed <= (Integer.parseInt(maxSpeed)*1.1)))
                            tvSpeed.setBackgroundResource(R.drawable.backgorund_yellow);
                        else if (speed > (Integer.parseInt(maxSpeed)*1.1)){
                            tvSpeed.setBackgroundResource(R.drawable.background_red);
                            ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC,100);
                            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP,800);
                        }
                        else tvSpeed.setBackgroundColor(Color.CYAN);
                    }
                    // rpm
                    if (rpm < 2250)
                        tvRpm.setBackgroundResource(R.drawable.background_green);
                    else if (rpm < 3000)
                        tvRpm.setBackgroundResource(R.drawable.backgorund_yellow);
                    else tvRpm.setBackgroundResource(R.drawable.background_red);
                    // engine load
                    if (load < 50)
                        tvLoad.setBackgroundResource(R.drawable.background_green);
                    else if (load < 75)
                        tvLoad.setBackgroundResource(R.drawable.backgorund_yellow);
                    else tvLoad.setBackgroundResource(R.drawable.background_red);
                    // maf
                    if (maf <= 30)
                        tvMaf.setBackgroundResource(R.drawable.background_green);
                    else if (maf <= 55)
                        tvMaf.setBackgroundResource(R.drawable.backgorund_yellow);
                    else tvMaf.setBackgroundResource(R.drawable.background_red);
                    // map
                    if (map < 130)
                        tvMap.setBackgroundResource(R.drawable.background_green);
                    else if (map < 190)
                        tvMap.setBackgroundResource(R.drawable.backgorund_yellow);
                    else tvMap.setBackgroundResource(R.drawable.background_red);
                }// counterIncludeIdle % 30 == 0

                if (counterExcludeIdle == 250){//5 sec window (100 data) //Saniyede 50 veri geliyor. 250 olarak güncellendi.
                    fileSaveName = csvFileName+"_"+timestamp.toString().split("\\.")[0].replaceAll(" ","_").replaceAll("-","").replaceAll(":","");
                    file = new File(directoryExcludeIdle,fileSaveName+".csv");
                    try {
                        writer = new BufferedWriter(new FileWriter(file));
                        writer.write(dataExcludeIdle.toString());
                        writer.flush();
                        writer.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    dataExcludeIdle.setLength(0);
                    counterExcludeIdle = 0;
                }

                if (counterIncludeIdle == 250){//5 sec window (100 data) //Saniyede 50 veri geliyor. 250 olarak güncellendi.
                    fileSaveName = csvFileName+"_"+timestamp.toString().split("\\.")[0].replaceAll(" ","_").replaceAll("-","").replaceAll(":","");
                    file = new File(directoryIncludeIdle,fileSaveName+".csv");
                    try {
                        writer = new BufferedWriter(new FileWriter(file));
                        writer.write(dataIncludeIdle.toString());
                        writer.flush();
                        writer.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    dataIncludeIdle.setLength(0);
                    counterIncludeIdle = 0;
                }//if counterIncludeIdle == 100
                break;
        }//switch
    }//onSensorChanged

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private class MyLocationListener implements LocationListener {
        @SuppressLint({"DefaultLocale", "SetTextI18n"})
        @Override
        public void onLocationChanged(Location loc) {
            latitude = loc.getLatitude();
            longitude = loc.getLongitude();
            altitude = loc.getAltitude();
            gpsSpeed = loc.getSpeed() * 3.6; //convert km/h
            strGpsSpeed = String.format("%.2f",gpsSpeed);
        }//onLocationChange
    }//MyLocationListener

    private class ObdTask extends AsyncTask<Void, String, Void> {
        @SuppressLint("SetTextI18n")
        @Override
        protected Void doInBackground(Void... voids) {
            try {
                getObdResult("ATWS"); //Soft reset
                //getObdResult("ATZ"); //Hard reset
                getObdResult("ATE0"); //Echo Off
                getObdResult("ATL0"); //Linefeed Off
                getObdResult("ATS0"); //Spaces Off
                getObdResult("ATH0"); //Headers Off
                getObdResult("ATSP0"); //Set Protocol to Automatic
                while (true) {
                    if((counterIncludeIdle % 50 == 0) && connected){// 1 sec interval (20 data) //Fakat saniyede 50 veri geliyor. (O_o)
                        try {
                            rpmHex = getObdResult("010C");
                            rpm = convertHexCode(rpmHex,4)/4;
                            tvRpm.setText("RPM: "+rpm);
                            loadHex = getObdResult("0104");
                            load = convertHexCode(loadHex,2)*100/255;
                            tvLoad.setText("LOAD: "+load+" %");
                            speedHex = getObdResult("010D");
                            speed = convertHexCode(speedHex,2);
                            tvSpeed.setText("SPEED: "+speed+" km/h");
                            coolantTempHex = getObdResult("0105");
                            coolantTemp = convertHexCode(coolantTempHex,2)-40;
                            tvCoolantTemp.setText("COOLANT TEMP: "+coolantTemp+" °C");
                            intakeAirTempHex = getObdResult("010F");
                            intakeAirTemp = convertHexCode(intakeAirTempHex,2)-40;
                            tvIntakeAirTemp.setText("INTAKE AIR TEMP: "+intakeAirTemp+" °C");
                            mafHex = getObdResult("0110");
                            mafRaw = convertHexCode(mafHex,4);
                            maf = mafRaw / 100.0;
                            tvMaf.setText("MAF: "+maf+" g/s");
                            mapHex = getObdResult("010B");
                            map = convertHexCode(mapHex,2);
                            tvMap.setText("MAP: "+map+" kPa");
                            temperatureHex = getObdResult("0146");
                            temperature = convertHexCode(temperatureHex,2)-40;
                            tvTemperature.setText("AMBIENT TEMPERATURE: "+temperature+" °C");
                            voltageHex = getObdResult("ATRV");
                            tvVoltage.setText("VOLTAGE: "+voltageHex);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }//if (counter%20 && connected)
                }//while true
            } catch (Exception e) {
                Log.e("MainActivity", "Error", e);
            }
            return null;
        }
        @Override
        protected void onProgressUpdate(String... values) {

        }
        public int convertHexCode(String code, int last){
            String ret = "";
            String codeX = code.replaceAll(" ","");//clear spaces if available
            for (int i = codeX.length(); i > (codeX.length() - last); i--){
                ret = codeX.charAt(i-1) + ret;
            }
            return Integer.parseInt(ret,16);
        }
        public String getObdResult(String command) throws IOException {
            command += "\r";
            outputStream.write(command.getBytes());
            outputStream.flush();
            res.setLength(0);
            int b;
            while ((b = inputStream.read()) != -1) {
                char c = (char) b;
                if (c == '>') break; // End of response
                res.append(c);
            }
            return res.toString().trim();
        }//getObdResult
    }//class ObdTask

    /*private void startRecording() {
        audioRecord = new AudioRecord(android.media.MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE);

        audioRecord.startRecording();

        //Calculate features with 2 Hz
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                audioRecord.read(buffer, 0, BUFFER_SIZE);
                double[] features = calculateFeatures(buffer, BUFFER_SIZE);
                rms = features[0];
                zcr = features[1];
                decibel = features[2];
                avgAmplitude = features[3];
                signalEnergy = features[4];
                dominantFrequency = features[5];
                spectralCentroid = features[6];
                spectralSpread = features[7];
                spectralFlatness = features[8];
                spectralEntropy = features[9];
                skewness = features[10];
                kurtosis = features[11];
                bandwidth = features[12];
                rollOff = features[13];
                //updateUI(features);
                handler.postDelayed(this, 100); // 10 Hz, her 100 ms'de bir
            }
        }, 0);
    }//startRecording */

    /*private void updateUI(double[] features) {
        String result = String.format(
                "RMS: %f\nZCR: %f\nDesibel: %f dB\nOrtalama Amplitüd: %f\nSinyal Enerjisi: %f\n" +
                        "Dominant Frekans: %f Hz\nSpektral Centroid: %f Hz\nSpektral Spread: %f Hz\n" +
                        "Spektral Flatness: %f\nSpektral Entropi: %f\nSkewness: %f\nKurtosis: %f\n" +
                        "Bandwidth: %f Hz\nRoll-Off: %f Hz",
                features[0], features[1], features[2], features[3], features[4],
                features[5], features[6], features[7], features[8], features[9],
                features[10], features[11], features[12], features[13]
        );
        tv.setText(result);
    }*/

    private void startRecording() {
        audioRecord = new AudioRecord(android.media.MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE);

        audioRecord.startRecording();

        audioExecutor.execute(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                short[] buffer = new short[BUFFER_SIZE];
                int read = audioRecord.read(buffer, 0, BUFFER_SIZE);
                double[] features = calculateFeatures(buffer, BUFFER_SIZE);
                updateFeatures(features);
                try {
                    Thread.sleep(10); // value=100 for 10 Hz
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }//while
        });//audioExecutor.execute()
    }//startRecording

    private void updateFeatures(double[] features) {
        rms = features[0];
        zcr = features[1];
        decibel = features[2];
        avgAmplitude = features[3];
        signalEnergy = features[4];
        dominantFrequency = features[5];
        spectralCentroid = features[6];
        spectralSpread = features[7];
        spectralFlatness = features[8];
        spectralEntropy = features[9];
        skewness = features[10];
        kurtosis = features[11];
        bandwidth = features[12];
        rollOff = features[13];
    }

    private double[] calculateFeatures(short[] buffer, int length) {
        // Pre-compute values to avoid redundant calculations
        double rms = 0, amplitudeSum = 0, energy = 0;
        for (short value : buffer) {
            double absValue = Math.abs(value);
            rms += value * value;
            amplitudeSum += absValue;
            energy += value * value;
        }

        // Compute RMS and amplitude metrics
        rms = Math.sqrt(rms / length);
        amplitudeSum /= length;
        double decibel = (rms > 0) ? 20 * Math.log10(rms) : 0;

        // Calculate Zero-Crossing Rate (ZCR)
        int zeroCrossings = 0;
        for (int i = 1; i < length; i++) {
            if ((buffer[i - 1] > 0 && buffer[i] < 0) || (buffer[i - 1] < 0 && buffer[i] > 0)) {
                zeroCrossings++;
            }
        }
        double zcr = (double) zeroCrossings / length;

        // Spectral Features using FFT
        double[] fftMagnitudes = calculateFFT(buffer, length);
        double dominantFrequency = getDominantFrequency(fftMagnitudes, SAMPLE_RATE);
        double spectralCentroid = getSpectralCentroid(fftMagnitudes);
        double spectralSpread = getSpectralSpread(fftMagnitudes, spectralCentroid);
        double spectralFlatness = calculateSpectralFlatness(fftMagnitudes);
        double spectralEntropy = calculateSpectralEntropy(fftMagnitudes);

        // Higher-order features
        double skewness = calculateSkewness(buffer, length);
        double kurtosis = calculateKurtosis(buffer, length);
        double bandwidth = calculateSpectralBandwidth(fftMagnitudes, spectralCentroid);
        double rollOff = calculateSpectralRollOff(fftMagnitudes, 0.85);

        // Consolidate and return features
        return new double[]{
                rms, zcr, decibel, amplitudeSum, energy,
                dominantFrequency, spectralCentroid, spectralSpread,
                spectralFlatness, spectralEntropy, skewness, kurtosis, bandwidth, rollOff
        };
    }

    private double[] calculateFFT(short[] buffer, int length) {
        int n = length;
        double[] real = new double[n];
        double[] imag = new double[n];

        for (int i = 0; i < n; i++) {
            real[i] = buffer[i];
            imag[i] = 0;  // Initialize imaginary part to zero
        }

        fft(real, imag, n);

        // Magnitude calculation optimized to avoid redundant computations
        double[] magnitudes = new double[n / 2];
        for (int i = 0; i < n / 2; i++) {
            magnitudes[i] = Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
        }

        return magnitudes;
    }

    private void fft(double[] real, double[] imag, int n) {
        int j = 0;
        for (int i = 1; i < n - 1; i++) {
            int bit = n >> 1;
            while (j >= bit) {
                j -= bit;
                bit >>= 1;
            }
            j += bit;
            if (i < j) {
                double tempReal = real[i];
                real[i] = real[j];
                real[j] = tempReal;

                double tempImag = imag[i];
                imag[i] = imag[j];
                imag[j] = tempImag;
            }
        }

        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2 * Math.PI / len;
            double wReal = Math.cos(angle);
            double wImag = Math.sin(angle);

            for (int m = 0; m < n; m += len) {
                double uReal = 1;
                double uImag = 0;

                for (int k = 0; k < len / 2; k++) {
                    int evenIndex = m + k;
                    int oddIndex = m + k + len / 2;

                    double tempReal = uReal * real[oddIndex] - uImag * imag[oddIndex];
                    double tempImag = uReal * imag[oddIndex] + uImag * real[oddIndex];

                    real[oddIndex] = real[evenIndex] - tempReal;
                    imag[oddIndex] = imag[evenIndex] - tempImag;

                    real[evenIndex] += tempReal;
                    imag[evenIndex] += tempImag;

                    double tempUReal = uReal;
                    uReal = tempUReal * wReal - uImag * wImag;
                    uImag = tempUReal * wImag + uImag * wReal;
                }
            }
        }
    }

    private double getDominantFrequency(double[] magnitudes, int sampleRate) {
        int maxIndex = 0;
        for (int i = 1; i < magnitudes.length; i++) {
            if (magnitudes[i] > magnitudes[maxIndex]) {
                maxIndex = i;
            }
        }
        return maxIndex * sampleRate / magnitudes.length;
    }

    private double getSpectralCentroid(double[] magnitudes) {
        double centroid = 0;
        double total = 0;
        for (int i = 0; i < magnitudes.length; i++) {
            centroid += i * magnitudes[i];
            total += magnitudes[i];
        }
        return centroid / total;
    }

    private double getSpectralSpread(double[] magnitudes, double centroid) {
        double spread = 0;
        double total = 0;
        for (int i = 0; i < magnitudes.length; i++) {
            spread += Math.pow(i - centroid, 2) * magnitudes[i];
            total += magnitudes[i];
        }
        return spread / total;
    }

    private double calculateSpectralFlatness(double[] magnitudes) {
        double arithmeticMean = 0;
        double geometricMean = 1;
        for (double mag : magnitudes) {
            arithmeticMean += mag;
            geometricMean *= mag;
        }
        arithmeticMean /= magnitudes.length;
        geometricMean = Math.pow(geometricMean, 1.0 / magnitudes.length);
        if (arithmeticMean == 0) return 0;
        else return geometricMean/arithmeticMean;
    }

    private double calculateSpectralEntropy(double[] magnitudes) {
        double sum = 0;
        for (double mag : magnitudes) {
            sum += mag;
        }
        double entropy = 0;
        for (double mag : magnitudes) {
            double probability = mag / sum;
            if (probability > 0) {
                entropy -= probability * Math.log(probability) / Math.log(2);
            }
        }
        return entropy;
    }

    private double calculateSkewness(short[] buffer, int length) {
        double mean = 0, variance = 0, skewness = 0;
        for (int i = 0; i < length; i++) {
            mean += buffer[i];
        }
        mean /= length;
        for (int i = 0; i < length; i++) {
            double diff = buffer[i] - mean;
            variance += diff * diff;
            skewness += diff * diff * diff;
        }
        variance /= length;
        skewness /= length;
        return variance == 0 ? 0 : skewness / Math.pow(variance, 1.5);
    }

    private double calculateKurtosis(short[] buffer, int length) {
        double mean = 0, variance = 0, kurtosis = 0;
        for (int i = 0; i < length; i++) {
            mean += buffer[i];
        }
        mean /= length;
        for (int i = 0; i < length; i++) {
            double diff = buffer[i] - mean;
            variance += diff * diff;
            kurtosis += diff * diff * diff * diff;
        }
        variance /= length;
        kurtosis /= length;
        return variance == 0 ? 0 : (kurtosis / (variance * variance)) - 3;
    }

    private double calculateSpectralBandwidth(double[] magnitudes, double centroid) {
        double bandwidth = 0;
        double total = 0;
        for (int i = 0; i < magnitudes.length; i++) {
            bandwidth += Math.pow(i - centroid, 2) * magnitudes[i];
            total += magnitudes[i];
        }
        return bandwidth / total;
    }

    private double calculateSpectralRollOff(double[] magnitudes, double threshold) {
        double totalEnergy = 0;
        for (double mag : magnitudes) {
            totalEnergy += mag;
        }
        double rollOffEnergy = threshold * totalEnergy;
        double cumulativeEnergy = 0;
        for (int i = 0; i < magnitudes.length; i++) {
            cumulativeEnergy += magnitudes[i];
            if (cumulativeEnergy >= rollOffEnergy) {
                return i;
            }
        }
        return magnitudes.length - 1;
    }
}//MainActivity