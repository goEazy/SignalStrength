package com.achilles.signalstrength;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity{

    private static final String UPLOAD_URL = "http://eklavya.pe.hu/test.php";

    private static final String TAG = "Main Activity";
    private static final int MY_PERMISSIONS_REQUEST_ACCOUNTS = 1;
    TelephonyManager telephonyManager;
    MyPhoneStateListener psListener;
    LocationManager locationManager;
    String mprovider;
    String gpsValue = "";
    String carrierName;
    String IMEINumber;
    String nType;
    int signalStrengthValue;
    String formattedDate;
    int cid;
    int lac;
    File gpxfile;
    File root;
    TextView tvSignal;
    TextView tvDate;
    TextView tvCarrier;
    TextView tvNT;
    TextView tvDID;
    TextView tvLoc;
    TextView tvTowID;
    boolean permissionGrant = false;
    private Button bUpload;
    Context context;
    String fileName;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeElements();
        Log.w(TAG, "onCreate: before checkAndRequest()");
        checkAndRequestPermissions();
        writeGPS();
        Log.w(TAG, "onCreate: after checkArequest()");
        runThis();
        Log.w(TAG, "onCreate: after runthis()" );
        context = getApplicationContext();
        bUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uploadWork();
            }
        });
    }
    private void uploadWork() {
        try {
            String data = getContent();
            sendData(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void sendData(final String data) {
        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Uploading...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.show();
        StringRequest stringRequest = new StringRequest(Request.Method.POST, UPLOAD_URL,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            Log.w(TAG, "onResponse: " + response );
                            JSONObject jsonResponse = new JSONObject(response);
                            boolean success = jsonResponse.getBoolean("success");
                            progressDialog.dismiss();
                            if(success) {
                                Log.d(TAG, "onResponse cleaning");
                                clearFile();
                            }
                            else{
                                AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
                                alert.setTitle("Error");
                                alert.setMessage("Some Error Occurred. Try Later!!!");
                                alert.setPositiveButton("OK",null);
                                alert.show();
                            }
                        }
                        catch (JSONException e) {
                            progressDialog.dismiss();
                            Log.w(TAG, "onResponse: JSONException: \n\n" + e.getMessage().toString() );
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.w(TAG, "onErrorResponse: " + error.getMessage().toString() );
                        progressDialog.dismiss();
                        AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
                        alert.setTitle("Error");
                        alert.setMessage(error.getMessage().toString());
                        alert.setPositiveButton("OK",null);
                        alert.show();
                    }
                }){
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String,String> map = new HashMap<String,String>();
                Log.w(TAG, "getParams:");
                map.put("data", data);
                return map;
            }
        };
        RequestQueue requestQueue = Volley.newRequestQueue(this);
        requestQueue.add(stringRequest);
    }
    private void clearFile() throws IOException {
        PrintWriter writer = new PrintWriter(gpxfile);
        writer.print("");
        writer.close();
    }
    private void initializeElements() {
        bUpload = (Button)findViewById(R.id.bUpload);
        tvCarrier = (TextView) findViewById(R.id.tvCarrierV);
        tvDate = (TextView) findViewById(R.id.tvDate);
        tvDID = (TextView) findViewById(R.id.tvDIDV);
        tvLoc = (TextView) findViewById(R.id.tvLocV);
        tvNT = (TextView) findViewById(R.id.tvNTV);
        tvSignal = (TextView) findViewById(R.id.tvSignal);
        tvTowID = (TextView) findViewById(R.id.tvTowIDV);
    }
    public void runThis(){
        Log.w(TAG, "runThis: inside runThis()"  );
        if (permissionGrant) {
            telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            IMEINumber = telephonyManager.getDeviceId();
            tvDID.setText(IMEINumber);
            Log.w(TAG, "runThis: before create file");
            createFile();
            psListener = new MyPhoneStateListener();
            telephonyManager.listen(psListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        }else{
            Toast.makeText(getApplicationContext(),"permission grant false",
                    Toast.LENGTH_SHORT).show();
        }
    }
    private boolean checkAndRequestPermissions() {
        int readPhonePermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_PHONE_STATE);
        int storageReadPermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE);
        int storageWritePermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int locationCoarsePermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION);
        int locationFinePermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);

        List<String> listPermissionsNeeded = new ArrayList<>();
        if (readPhonePermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.READ_PHONE_STATE);
        }if (storageReadPermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }if (storageWritePermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }if (locationCoarsePermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }if (locationFinePermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), MY_PERMISSIONS_REQUEST_ACCOUNTS);
            permissionGrant=false;
            return false;
        }else{
            Log.w(TAG, "checkAndRequestPermissions: inside checkAndRequest()" );
            permissionGrant=true;
            return true;
        }
    }
    @Override    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCOUNTS:
                Log.d(TAG, "onRequestPermissionsResult: "+grantResults.toString());
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    permissionGrant = true;
                    runThis();
                } else {
                    Toast.makeText(getApplicationContext(),"All permissions not given",
                            Toast.LENGTH_SHORT).show();
                    permissionGrant = false;
                }
                break;
        }
    }
    public void doMyStuffs() throws IOException {
        Calendar c = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        formattedDate = df.format(c.getTime());
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        GsmCellLocation cellLocation = (GsmCellLocation) telephonyManager.getCellLocation();
        NetworkInfo info = cm.getActiveNetworkInfo();
        carrierName = telephonyManager.getNetworkOperatorName();

        nType = "";

        if (info == null || !info.isConnected())
            nType = "-";
        else if (info.getType() == ConnectivityManager.TYPE_WIFI)
            nType = "WIFI";
        else if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
            int networkType = info.getSubtype();
            switch (networkType) {
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                case TelephonyManager.NETWORK_TYPE_IDEN:
                    nType = "2G";
                    break;
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:
                case TelephonyManager.NETWORK_TYPE_EHRPD:
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                    nType = "3G";
                    break;
                case TelephonyManager.NETWORK_TYPE_LTE:
                    nType = "4G";
                    break;
                default:
                    nType = "?";
            }
        }

        cid = cellLocation.getCid() & 0xffff;
        lac = cellLocation.getLac() & 0xffff;

        tvDate.setText("" + formattedDate);
        tvNT.setText(nType);
        tvCarrier.setText(carrierName.toString());
        tvTowID.setText(cid + " : " + lac);
        tvLoc.setText(gpsValue);
        if (!gpsValue.equalsIgnoreCase("")) {
            writeFile(carrierName, nType, signalStrengthValue, formattedDate, cid, lac, gpsValue);
        }

    }
    public String getContent() throws IOException {
        Log.d(TAG, "getContent: "+fileName);
        BufferedReader bufferedReader = new BufferedReader(new FileReader(gpxfile));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            sb.append(line+"\n");
        };
        return sb.toString();
    }
    public class MyPhoneStateListener extends PhoneStateListener{
        private static final String TAG = "ddd";
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            super.onSignalStrengthsChanged(signalStrength);
            if (signalStrength.isGsm()) {
                if (signalStrength.getGsmSignalStrength() != 99) {
                    signalStrengthValue = signalStrength.getGsmSignalStrength() * 2 - 113;
                }
                else
                    signalStrengthValue = signalStrength.getGsmSignalStrength();
            } else {
                signalStrengthValue = signalStrength.getCdmaDbm();
            }
            Log.w(TAG, "onSignalStrengthsChanged: " + signalStrength);
            tvSignal.setText(signalStrengthValue+"");

            try {
                doMyStuffs();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    public void createFile(){
        fileName = IMEINumber + ".txt";
        try
        {
            root = new File(Environment.getExternalStorageDirectory()+File.separator, "SignalStrength");
            if (!root.exists())
            {
                root.mkdirs();
            }
            gpxfile = new File(root, fileName);
            FileWriter writer = new FileWriter(gpxfile,true);
            writer.append("");
            writer.flush();
            writer.close();
        }
        catch(IOException e)
        {
            e.printStackTrace();

        }
    }
    public void writeFile(String carrierName,String nType,int signalStrengthValue,String formattedDate,int cid,int lac,String gpsValue) throws IOException {
        FileWriter writer = new FileWriter(gpxfile,true);
        writer.append(IMEINumber+","+carrierName+","+nType+","+signalStrengthValue+","+formattedDate+","+cid+":"+lac+","+gpsValue+"\n");
        writer.flush();
        writer.close();
    }
    private void writeGPS() {
        Log.d(TAG, "writeGPS: entered into write GPS");
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        LocationListener mlocListener = new MyLocationListener();
        criteria.setAccuracy(Criteria.ACCURACY_COARSE);
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        mprovider = locationManager.getBestProvider(criteria, true);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            checkAndRequestPermissions();
            Log.d(TAG, "writeGPS: gadbaaddddd");
            return;
        }

        locationManager.requestLocationUpdates("gps", 1000, 1,
                mlocListener);
    }
    public class MyLocationListener implements LocationListener {
        @Override
        public void onLocationChanged(Location loc) {
            String lat = loc.getLatitude() + "";
            String lon = loc.getLongitude() + "";
            gpsValue = lat+":"+lon;
        }

        @Override
        public void onProviderDisabled(String provider) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    }
}