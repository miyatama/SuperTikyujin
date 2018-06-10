package com.example.miyatama.supertikyujin;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.hardware.camera2.CameraCharacteristics;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import static okhttp3.MultipartBody.FORM;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = MainActivity.class.getSimpleName();
    private final String EMPATH_ENDPOINT = "https://api.webempath.net/v2/analyzeWav";
    private final String EMPATH_APIKEY = "setting empath api key here";
    private final String filePath = Environment.getExternalStorageDirectory() + "/empath.wav";

    private Handler handler = null;
    private final int MSG_STOP_RECORDING = 1000;
    private final int MSG_GET_EMOTION = 1010;
    private final int MSG_FAIL_GET_EMOTION = 1020;
    private final int MSG_SHOW_SUPER_TIKYUJIN = 1030;
    private final int MSG_HIDE_SUPER_TIKYUJIN = 1040;

    private Button btnDetectEmotion = null;
    private AutoFitTextureView mTextureView;
    private CameraStateMachine mCamera;
    private Bitmap mCorrectRotateBitmap;
    private ImageView imgSuperTikykujin = null;
    private FaceDetector mFaceDetector;

    private AudioRecord audioRecord;
    private int bufSize;
    private short[] shortData;
    private WavFile wav = new WavFile();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnDetectEmotion = this.findViewById(R.id.btnDetectEmotion);
        btnDetectEmotion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startRecording();
            }
        });

        handler = new Handler(new Handler.Callback(){
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public boolean handleMessage(Message message) {
                switch (message.what){
                    case MSG_STOP_RECORDING:
                        stopRecoding();
                        break;
                    case MSG_GET_EMOTION:
                        createSuperTikyuJin((JSONObject)message.obj);
                        break;
                    case MSG_FAIL_GET_EMOTION:
                        Toast.makeText(getApplicationContext(), "", Toast.LENGTH_LONG).show();
                        break;
                    case MSG_SHOW_SUPER_TIKYUJIN:
                        mTextureView.setVisibility(View.INVISIBLE);
                        imgSuperTikykujin.setVisibility(View.VISIBLE);
                        break;
                    case MSG_HIDE_SUPER_TIKYUJIN:
                        mTextureView.setVisibility(View.VISIBLE);
                        imgSuperTikykujin.setVisibility(View.INVISIBLE);
                        break;
                }
                return false;
            }
        });
        mTextureView = findViewById(R.id.textureView);
        mCamera = new CameraStateMachine();
        mFaceDetector = new FaceDetector.Builder(this)
                .setProminentFaceOnly(true)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .setMode(FaceDetector.FAST_MODE)
                .setTrackingEnabled(false)
                .build();

        wav.createFile(filePath);
        bufSize = android.media.AudioRecord.getMinBufferSize(SoundDefine.SAMPLING_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                SoundDefine.SAMPLING_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize);
        shortData = new short[bufSize / 2];
        audioRecord.setRecordPositionUpdateListener(new AudioRecord.OnRecordPositionUpdateListener() {
            @Override
            public void onPeriodicNotification(AudioRecord recorder) {
                audioRecord.read(shortData, 0, bufSize / 2); // 読み込む
                wav.addBigEndianData(shortData); // ファイルに書き出す
            }

            @Override
            public void onMarkerReached(AudioRecord recorder) { }
        });
        audioRecord.setPositionNotificationPeriod(bufSize / 2);
        imgSuperTikykujin = this.findViewById(R.id.imgSuperTikykujin);
        imgSuperTikykujin.setVisibility(View.INVISIBLE);
    }

    @Override
    protected void onResume(){
        super.onResume();
        mCamera.open(this, mTextureView, CameraCharacteristics.LENS_FACING_FRONT);
    }

    @Override
    protected void onPause(){
        mCamera.close();
        super.onPause();
    }

    @Override
    protected void onStop(){
        super.onStop();
        if (mCorrectRotateBitmap != null) {
            mCorrectRotateBitmap.recycle();
            mCorrectRotateBitmap = null;
        }
    }

    private void startRecording() {
        wav.createFile(filePath);
        audioRecord.startRecording();
        audioRecord.read(shortData, 0, bufSize/2);

        Message msg = Message.obtain();
        msg.what = MSG_STOP_RECORDING;
        handler.sendMessageDelayed(msg, 3000);
        btnDetectEmotion.setEnabled(false);
    }

    private void stopRecoding() {
        btnDetectEmotion.setEnabled(true);
        try {
            audioRecord.stop();
            new GetEmotion().execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void createSuperTikyuJin(JSONObject emotion)  {
        try{
            int anger = emotion.getInt("anger");
            if(anger >= 10){
                boolean takePictureResult;
                if (mCamera.takePicture(new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader imageReader) {
                        Log.d(TAG, "onImageAvailable");
                        Matrix matrix = new Matrix();
                        matrix.preScale(0.3f, -0.3f);
                        final Image image = imageReader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] buff = new byte[buffer.remaining()];
                        buffer.get(buff);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(buff, 0, buff.length);
                        image.close();

                        mCorrectRotateBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);

                        Frame frame = new Frame.Builder()
                                .setBitmap(mCorrectRotateBitmap)
                                .build();
                        SparseArray detectedFaces = mFaceDetector.detect(frame);
                        if (detectedFaces != null && detectedFaces.size() > 0) {
                            float maxSize = 0;
                            int idx = 0;
                            for (int i = 0; i < detectedFaces.size(); i++) {
                                Face face = (Face) detectedFaces.valueAt(i);
                                if (maxSize < (face.getWidth() * face.getHeight())) {
                                    maxSize = (face.getWidth() * face.getHeight());
                                    idx = i;
                                }
                            }
                            Face face = (Face) detectedFaces.valueAt(idx);
                            Bitmap kinpatsu = BitmapFactory.decodeResource(
                                    getResources(),
                                    R.drawable.super_hair);
                            float scaleWidth = face.getWidth() / (float) kinpatsu.getWidth();
                            float scaleHeight = face.getWidth() / (float) kinpatsu.getWidth();
                            Matrix kinpatsuScale = new Matrix();
                            kinpatsuScale.postScale(scaleWidth, scaleHeight);
                            Bitmap resizedKinpatsu = Bitmap.createBitmap(
                                    kinpatsu,
                                    0,
                                    0,
                                    kinpatsu.getWidth(),
                                    kinpatsu.getHeight(),
                                    kinpatsuScale,
                                    false);

                            Canvas canvas = new Canvas(mCorrectRotateBitmap);
                            canvas.drawBitmap(
                                    resizedKinpatsu,
                                    face.getPosition().x,
                                    face.getPosition().y - ((face.getHeight() / 2.0f)),
                                    null);

                            imgSuperTikykujin.setImageBitmap(mCorrectRotateBitmap);


                            resizedKinpatsu.recycle();
                            kinpatsu.recycle();

                            Message msgShowImage = Message.obtain();
                            msgShowImage.what = MSG_SHOW_SUPER_TIKYUJIN;
                            handler.sendMessage(msgShowImage);

                            Message msgHideImage = Message.obtain();
                            msgShowImage.what = MSG_HIDE_SUPER_TIKYUJIN;
                            handler.sendMessageDelayed(msgHideImage, 10000);
                        } else {
                            Toast.makeText(getApplicationContext(), "face not exists", Toast.LENGTH_SHORT).show();
                        }

                        bitmap.recycle();
                        bitmap = null;
                    }
                })) takePictureResult = true;
                else takePictureResult = false;
            } else {
                // not angry(like a hotoke)
                Toast.makeText(this, "怒りが足りない", Toast.LENGTH_LONG).show();
            }
        }catch(JSONException ex){
            Toast.makeText(this, "怒りが感じられない(おかしい)", Toast.LENGTH_LONG).show();
        }
    }

    private class GetEmotion extends AsyncTask<String,Void,Long> {
        private String result = null;

        @Override
        protected Long doInBackground(String... strings) {
            try{
                OkHttpClient client = new OkHttpClient();
                File file = new File(filePath);
                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(FORM)
                        .addFormDataPart("apikey",EMPATH_APIKEY)
                        .addFormDataPart("wav", "empath.wav", RequestBody.create(MediaType.parse("audio/wav"), file))
                        .build();
                Request request = new Request.Builder().url(EMPATH_ENDPOINT).post(requestBody).build();
                Response response = client.newCall(request).execute();
                this.result = response.body().string();
                return 0L;
            }catch(Exception e){
                result = "error";
                return 1L;
            }
        }

        @Override
        protected void onPostExecute(Long result) {
            super.onPostExecute(result);
            if (result == 0L){
                try {
                    Log.d(TAG, "json: " + this.result);
                    JSONObject json = new JSONObject(this.result);
                    Message msg = Message.obtain();
                    msg.what = MSG_GET_EMOTION;
                    msg.obj = json;
                    handler.sendMessage(msg);
                } catch (JSONException e) {
                    Message msg = Message.obtain();
                    msg.what = MSG_FAIL_GET_EMOTION;
                    handler.sendMessage(msg);
                    e.printStackTrace();
                }

            }
        }
    }
}
