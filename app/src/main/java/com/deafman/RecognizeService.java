package com.deafman;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import javax.net.ssl.HttpsURLConnection;

import javaFlacEncoder.FLAC_FileEncoder;

public class RecognizeService extends Service {

    protected AudioManager mAudioManager;

    private static final String AUDIO_RECORDER_FOLDER = "DeaFolder";
    private static final String AUDIO_RECORDER_FILE = "speech_test.wav";
    private static final String AUDIO_RECORDER_TEMP_FILE = "record_temp.wav";
    private static int frequency = 8000;
    private static String rate = "8000";
    private static int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
    private static int EncodingBitRate = AudioFormat.ENCODING_PCM_16BIT;
    private AudioRecord audioRecord = null;
    private int recBufSize = 0;
    private Thread recordingThread = null;
    private boolean isRecording = false;
    private static final int RECORDER_BPP = 16;

    private Context m_ctx;
    private PendingIntent pi;

    private String urlStr = "https://www.google.com/speech-api/v2/recognize?output=json&lang=ru-RU&key=AIzaSyBOti4mM-6x9WDnZIjIeyEU21OpBXqWBgw&client=chromium&maxresults=6&pfilter=2";

    public void onCreate()
    {
        super.onCreate();
        m_ctx = this;

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        //Log.d("TESTING: SPEECH SERVICE: CALL START", "onCreate()");
        startRecord();
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        //Log.d("TESTING: SPEECH SERVICE:", "MyService onStartCommand");

        pi = intent.getParcelableExtra(com.deafman.ResultActivity.PARAM_PINTENT);

        try {
            pi.send(ResultActivity.MSG_RECOGNIZER_START_LISTENING);
        } catch (PendingIntent.CanceledException e) {
            e.printStackTrace();
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onDestroy()
    {
        stopRecord();
        Log.d("TESTING: SPEECH SERVICE:", "MyService onDestroy");
    }

    private String getFilename(){
        String filepath = Environment.getExternalStorageDirectory().getAbsolutePath();
        File file = new File(filepath,AUDIO_RECORDER_FOLDER);

        if(file.exists()){
            file.delete();
        }

        return (file.getAbsolutePath() + "/" + AUDIO_RECORDER_FILE );
    }

    private String getTempFilename(){
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath,AUDIO_RECORDER_FOLDER);

        if(!file.exists()){
            file.mkdirs();
        }

        File tempFile = new File(filepath,AUDIO_RECORDER_TEMP_FILE);

        if(tempFile.exists())
            tempFile.delete();

        return (file.getAbsolutePath() + "/" + AUDIO_RECORDER_TEMP_FILE);
    }

    private void startRecord(){

        createAudioRecord();
        audioRecord.startRecording();

        isRecording = true;

        recordingThread = new Thread(new Runnable() {
            public void run() {
                writeAudioDataToFile();
            }
        },"AudioRecorder Thread");

        recordingThread.start();
    }

    private void writeAudioDataToFile(){
        byte data[] = new byte[recBufSize];
        String filename = getTempFilename();
        FileOutputStream os = null;

        try {
            os = new FileOutputStream(filename);
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        int read = 0;

        if(null != os){
            while(isRecording){
                read = audioRecord.read(data, 0, recBufSize);

                if(AudioRecord.ERROR_INVALID_OPERATION != read){
                    try {
                        os.write(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void stopRecord(){
        if(null != audioRecord){
            isRecording = false;

            audioRecord.stop();
            audioRecord.release();

            audioRecord = null;
            recordingThread = null;
        }

        copyWaveFile(getTempFilename(),getFilename());

        try {
            new Thread(new Runnable(){
                @Override
                public void run() {
                    try {
                        try {
                            Intent intent = new Intent().putExtra(ResultActivity.PARAM_RESULT,
                                    sendPost(urlStr, AUDIO_RECORDER_FILE, rate));
                            pi.send(RecognizeService.this, ResultActivity.MSG_RECOGNIZER_RESULT, intent);
                        } catch (PendingIntent.CanceledException e) {
                            e.printStackTrace();
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        deleteTempFile();
    }

    private void deleteTempFile() {
        File file = new File(getTempFilename());

        file.delete();
    }

    private void copyWaveFile(String inFilename,String outFilename){
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = frequency;
        int channels = 1;
        long byteRate = RECORDER_BPP * frequency * channels/8;

        byte[] data = new byte[recBufSize];

        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;

            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);

            while(in.read(data) != -1){
                out.write(data);
            }

            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Взял этот блок на форуме для конвертации raw в wav
    private void WriteWaveFileHeader(
            FileOutputStream out, long totalAudioLen,
            long totalDataLen, long longSampleRate, int channels,
            long byteRate) throws IOException {

        byte[] header = new byte[44];

        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (1 * 16 / 8);  // block align
        header[33] = 0;
        header[34] = RECORDER_BPP;  // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
        out.write(header, 0, 44);
    }

    public void createAudioRecord(){
        recBufSize = AudioRecord.getMinBufferSize(frequency,
                channelConfiguration, EncodingBitRate);

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, frequency,
                channelConfiguration, EncodingBitRate, recBufSize);
    }

    private String sendPost(String urlStr, String recFile, String rate) throws Exception {

        String transcript = null;
        String confidence = null;

        DataOutputStream dos = null;

        HttpsURLConnection httpCon = null;
        OutputStream out = null;
        try {
            Log.i("", "Распознавание...");

            FLAC_FileEncoder fileEncoder = new FLAC_FileEncoder();

            File inputfile = new File(Environment.getExternalStorageDirectory()
                    .getPath() + "/" + AUDIO_RECORDER_FOLDER + "/" + recFile);
            File outputfile = new File(Environment
                    .getExternalStorageDirectory().getPath()
                    + "/" + AUDIO_RECORDER_FOLDER + "/" + "speech_test.flac");

            fileEncoder.encode(inputfile, outputfile);

            URL url = new URL(urlStr);
            URLConnection urlCon = url.openConnection();
            if (!(urlCon instanceof HttpsURLConnection)) {
                throw new IOException("URL must be HTTPS");
            }
            httpCon = (HttpsURLConnection) urlCon;
            httpCon.setDefaultUseCaches(true);
            httpCon.setAllowUserInteraction(false);
            httpCon.setInstanceFollowRedirects(true);
            httpCon.setRequestMethod("POST");
            httpCon.setDoOutput(true);
            httpCon.setChunkedStreamingMode(0); //TransferType: chunked
            httpCon.setRequestProperty("Content-Type", "audio/x-flac; " + rate);

            out = httpCon.getOutputStream();

            File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" +
                    AUDIO_RECORDER_FOLDER + "/" + recFile);
            byte[] buffer = new byte[(int) file.length()];
            FileInputStream fis = new FileInputStream(file);
            fis.read(buffer);
            fis.close();
            dos = new DataOutputStream(httpCon.getOutputStream());
            dos.write(buffer);
            dos.close();

            httpCon.connect();
            int responseCode = httpCon.getResponseCode();
            if (responseCode == HttpsURLConnection.HTTP_OK) {
                httpCon.getResponseMessage();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        httpCon.getInputStream()));
                String s;
                Integer q;
                StringBuilder resultContent = new StringBuilder();
                while ((s = br.readLine()) != null) {
                    q = s.length();
                    if (q != 13) {
                        resultContent.append(s + "\n");
                    }
                }
                try {
                    JSONObject jsonResponse = new JSONObject(resultContent.toString());
                    JSONArray jsonResultArray = jsonResponse.optJSONArray("result");
                    Integer lengthJsonArr = jsonResultArray.length();

                    for (int i = 0; i < lengthJsonArr; i++) {
                        JSONObject jsonChildNode = jsonResultArray.getJSONObject(i);
                        JSONArray jsonAlternativeArray = jsonChildNode.getJSONArray("alternative");
                        //String alternative = jsonAlternativeArray.getJSONObject(i).getString("alternative");
                        transcript = jsonAlternativeArray.getJSONObject(i).getString("transcript");
                        confidence = jsonAlternativeArray.getJSONObject(i).getString("confidence");
                    }
                    //Toast.makeText(this, transcript+confidence, Toast.LENGTH_SHORT).show();
                    //output = alternative;
                } catch (Exception e) {
                    e.printStackTrace();

                    Log.e("ERROR IN PARSING JSON:", e.toString());
                }
            }
        } catch (Exception e) {
            //output = "";
            //confidence_level = -1;
            e.printStackTrace();

            Log.e("ERROR IN HTTP CONNECTION:", e.toString());
            if (e.toString().contains("java.net.UnknownHostException:") || e.toString().contains("java.net.SocketException")) {

                //return "NETWORK ISSUE";
            }
        }
        return transcript + confidence;
    }


}
