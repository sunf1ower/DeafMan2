package com.deafman;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;


public class ResultActivity extends Activity{

    final String LOG_TAG = "RecSvc";

    static final int MSG_RECOGNIZER_START_LISTENING = 1;
    static final int MSG_RECOGNIZER_RESULT = 2;
    static final int MSG_RECOGNIZER_CANCEL = 3;

    final int RESULT_CODE = 1;

    public boolean isService = true;

    public final static String PARAM_PINTENT = "pendingIntent";
    public final static String PARAM_RESULT = "result";

    TextView tvDialog;
    TextView callTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        final Button endCallBtn = (Button) findViewById(R.id.endCallBtn);
        final Button startServiceBtn = (Button) findViewById(R.id.startServiceBtn);

        callTime = (TextView) findViewById(R.id.callTime);
        tvDialog = (TextView) findViewById(R.id.dialog);
        tvDialog.setMovementMethod(new ScrollingMovementMethod());
    }

    public void onClickStart(View v) {
        PendingIntent pi;
        Intent intent;

        Intent nullIntent = new Intent();
        pi = createPendingResult(1, nullIntent, 0);
        intent = new Intent(this, RecognizeService.class).putExtra(PARAM_PINTENT, pi);

        if (isService == true){
            Toast.makeText(this, "Сервис запущен", Toast.LENGTH_SHORT).show();
            DateFormat date = new SimpleDateFormat("dd-MM-yy HH:mm:ss");
            String dateStr = date.format(Calendar.getInstance().getTime());
            tvDialog.append(dateStr);

            startService(intent);
            isService = false;
        }
        else {
            Toast.makeText(this, "Сервис остановлен", Toast.LENGTH_SHORT).show();

            stopService(intent);
            isService = true;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(LOG_TAG, "requestCode = " + requestCode + ", resultCode = "
                + resultCode);

        if (resultCode == MSG_RECOGNIZER_START_LISTENING) {
            Toast.makeText(this, "Старт распознавания", Toast.LENGTH_SHORT).show();
        }
        // Ловим сообщения о результатах
        if (resultCode == MSG_RECOGNIZER_RESULT) {

            String result = data.getStringExtra(PARAM_RESULT);
            //tvDialog.setText(result);
            String s = System.getProperty("line.separator");
            DateFormat time = new SimpleDateFormat("HH:mm");
            String timeStr = time.format(Calendar.getInstance().getTime());
            tvDialog.append(s);
            callTime.setText(timeStr);
            timeStr = "#Caller: " + timeStr + " - " + result;
            tvDialog.append(timeStr);
            tvDialog.append(s);
            tvDialog.append(s);
            timeStr.trim();
        }

        // Ловим сообщения об отмене распознавания
        if (resultCode == MSG_RECOGNIZER_CANCEL) {

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.result, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        Intent Settings = new Intent(this, SettingsActivity.class);
        startActivityForResult(Settings, 1);

        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
