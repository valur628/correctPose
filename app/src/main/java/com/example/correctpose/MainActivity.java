package com.example.correctpose;

import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;

import android.view.View;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.correctpose.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.SurfaceTexture;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList;
import com.google.mediapipe.components.CameraHelper;
import com.google.mediapipe.components.CameraXPreviewHelper;
import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.AndroidPacketCreator;
import com.google.mediapipe.framework.PacketGetter;
import com.google.mediapipe.framework.Packet;
import com.google.mediapipe.glutil.EglManager;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main activity of MediaPipe example apps.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String BINARY_GRAPH_NAME = "pose_tracking_gpu.binarypb";
    private static final String INPUT_VIDEO_STREAM_NAME = "input_video";
    private static final String OUTPUT_VIDEO_STREAM_NAME = "output_video";
    private static final String OUTPUT_LANDMARKS_STREAM_NAME = "pose_landmarks";
    private static final int NUM_FACES = 1;
    private static final CameraHelper.CameraFacing CAMERA_FACING = CameraHelper.CameraFacing.FRONT;
    // Flips the camera-preview frames vertically before sending them into FrameProcessor to be
    // processed in a MediaPipe graph, and flips the processed frames back when they are displayed.
    // This is needed because OpenGL represents images assuming the image origin is at the bottom-left
    // corner, whereas MediaPipe in general assumes the image origin is at top-left.
    private static final boolean FLIP_FRAMES_VERTICALLY = true;

    static {
        // Load all native libraries needed by the app.
        System.loadLibrary("mediapipe_jni");
        System.loadLibrary("opencv_java3");
    }

    // {@link SurfaceTexture} where the camera-preview frames can be accessed.
    private SurfaceTexture previewFrameTexture;
    // {@link SurfaceView} that displays the camera-preview frames processed by a MediaPipe graph.
    private SurfaceView previewDisplayView;
    // Creates and manages an {@link EGLContext}.
    private EglManager eglManager;
    // Sends camera-preview frames into a MediaPipe graph for processing, and displays the processed
    // frames onto a {@link Surface}.
    private FrameProcessor processor;
    // Converts the GL_TEXTURE_EXTERNAL_OES texture from Android camera into a regular texture to be
    // consumed by {@link FrameProcessor} and the underlying MediaPipe graph.
    private ExternalTextureConverter converter;
    // ApplicationInfo for retrieving metadata defined in the manifest.
    private ApplicationInfo applicationInfo;
    // Handles camera access via the {@link CameraX} Jetpack support library.
    private CameraXPreviewHelper cameraHelper;

    private TextView tv;
    private TextView tv2;

    private float leftEyePoint_blink_1, leftEyePoint_blink_2;
    //왼쪽 눈 깜빡임용 랜드마크 포인트
    private float rightEyePoint_blink_1, rightEyePoint_blink_2;
    //오른쪽 눈 깜빡임용 랜드마크 포인트
    private float ratioPoint_1a, ratioPoint_1b, ratioPoint_2a, ratioPoint_2b;
    // 눈 깜빡임용 비율 계산에 쓰일 포인트 변수 (왼쪽, 오른쪽)
    private float leftRatioMeasurement, rightRatioMeasurement;
    // 눈 비율 계산값 변수
    private boolean eye_blinked, eye_open;
    //양쪽 눈 감았는지 여부

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getContentViewLayoutResId());
        tv = findViewById(R.id.tv);
        tv2 = findViewById(R.id.tv2);
        //tv.setText("000");
        try {
            applicationInfo =
                    getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Cannot find application info: " + e);
        }

        //tv.setText("111");
        previewDisplayView = new SurfaceView(this);
        setupPreviewDisplayView();
        //tv.setText("222");

        // Initialize asset manager so that MediaPipe native libraries can access the app assets, e.g.,
        // binary graphs.
        AndroidAssetUtil.initializeNativeAssetManager(this);
        eglManager = new EglManager(null);
        //tv.setText("333");
        processor =
                new FrameProcessor(
                        this,
                        eglManager.getNativeContext(),
                        BINARY_GRAPH_NAME,
                        INPUT_VIDEO_STREAM_NAME,
                        OUTPUT_VIDEO_STREAM_NAME);
        processor
                .getVideoSurfaceOutput()
                .setFlipY(FLIP_FRAMES_VERTICALLY);

        //tv.setText("444");
        PermissionHelper.checkAndRequestCameraPermissions(this);
        //tv.setText("555");
        AndroidPacketCreator packetCreator = processor.getPacketCreator();
        //tv.setText("666");
        Map<String, Packet> inputSidePackets = new HashMap<>();
        //tv.setText("888");
        processor.setInputSidePackets(inputSidePackets);
        //tv.setText("999");

        eye_blinked = true;
        eye_open = true;
        //tv.setText("00000");

        // To show verbose logging, run:
        // adb shell setprop log.tag.MainActivity VERBOSE

        if (Log.isLoggable(TAG, Log.WARN)) {
            processor.addPacketCallback(
                    OUTPUT_LANDMARKS_STREAM_NAME,
                    (packet) -> {
                        Log.v(TAG, "Received pose landmarks packet.");
                        try {
                            NormalizedLandmarkList poseLandmarks =
                                    PacketGetter.getProto(packet, NormalizedLandmarkList.class);
                            Log.v(
                                    TAG,
                                    "[TS:"
                                            + packet.getTimestamp()
                                            + "] "
                                            + getPoseLandmarksDebugString(poseLandmarks));
                        } catch (InvalidProtocolBufferException exception) {
                            Log.e(TAG, "Failed to get proto.", exception);
                        }
                        /*
                        //tv.setText("33333");
                        leftEyePoint_blink_1 = multiFaceLandmarks.get(0).getLandmarkList().get(386).getY()*1920f;
                        leftEyePoint_blink_2 = multiFaceLandmarks.get(0).getLandmarkList().get(373).getY()*1920f;
                        rightEyePoint_blink_1 = multiFaceLandmarks.get(0).getLandmarkList().get(159).getY()*1920f;
                        rightEyePoint_blink_2 = multiFaceLandmarks.get(0).getLandmarkList().get(144).getY()*1920f;

                        //tv.setText("44444");
                        ratioPoint_1a = multiFaceLandmarks.get(0).getLandmarkList().get(5).getY()*1920f;
                        ratioPoint_1b = multiFaceLandmarks.get(0).getLandmarkList().get(4).getY()*1920f;

                        //tv.setText("55555");
                        leftRatioMeasurement = (leftEyePoint_blink_2 - leftEyePoint_blink_1) / (ratioPoint_1b - ratioPoint_1a);
                        rightRatioMeasurement = (rightEyePoint_blink_2 - rightEyePoint_blink_1) / (ratioPoint_1b - ratioPoint_1a);

                        tv2.setText("[LEFT=" + leftRatioMeasurement + "] | [" + rightRatioMeasurement + "=RIGHT]");
                        if(leftRatioMeasurement < 0.41 || rightRatioMeasurement < 0.41){
                            if(eye_blinked){
                                tv.setText("Eye is blinked");
                                //imgv.setImageDrawable(this.getResources().getDrawable(R.drawable.eyes_close));
                                eye_blinked = false;
                                eye_open = true;
                            }
                        }
                        else{
                            if(eye_open)
                            {
                                tv.setText("Eye is open");
                                //imgv.setImageDrawable(this.getResources().getDrawable(R.drawable.eyes_open));
                                eye_blinked = true;
                                eye_open = false;
                            }
                        }*/
          /*Log.v(
              TAG,
              "[TS:"
                  + packet.getTimestamp()
                  + "] "
                  + getMultiFaceLandmarksDebugString(multiFaceLandmarks));*/
                    });
        }
    }

    // Used to obtain the content view for this application. If you are extending this class, and
    // have a custom layout, override this method and return the custom layout.
    protected int getContentViewLayoutResId() {
        return R.layout.activity_main;
    }

    @Override
    protected void onResume() {
        super.onResume();
        converter =
                new ExternalTextureConverter(
                        eglManager.getContext(), 2);
        converter.setFlipY(FLIP_FRAMES_VERTICALLY);
        converter.setConsumer(processor);
        if (PermissionHelper.cameraPermissionsGranted(this)) {
            startCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        converter.close();

        // Hide preview display until we re-open the camera again.
        previewDisplayView.setVisibility(View.GONE);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    protected void onCameraStarted(SurfaceTexture surfaceTexture) {
        previewFrameTexture = surfaceTexture;
        // Make the display view visible to start showing the preview. This triggers the
        // SurfaceHolder.Callback added to (the holder of) previewDisplayView.
        previewDisplayView.setVisibility(View.VISIBLE);
    }

    protected Size cameraTargetResolution() {
        return null; // No preference and let the camera (helper) decide.
    }

    public void startCamera() {
        cameraHelper = new CameraXPreviewHelper();
        cameraHelper.setOnCameraStartedListener(
                surfaceTexture -> {
                    onCameraStarted(surfaceTexture);
                });
        CameraHelper.CameraFacing cameraFacing = CameraHelper.CameraFacing.FRONT;
        cameraHelper.startCamera(
                this, cameraFacing, previewFrameTexture, cameraTargetResolution());
    }

    protected Size computeViewSize(int width, int height) {
        return new Size(width, height);
    }

    protected void onPreviewDisplaySurfaceChanged(
            SurfaceHolder holder, int format, int width, int height) {
        // (Re-)Compute the ideal size of the camera-preview display (the area that the
        // camera-preview frames get rendered onto, potentially with scaling and rotation)
        // based on the size of the SurfaceView that contains the display.
        Size viewSize = computeViewSize(width, height);
        Size displaySize = cameraHelper.computeDisplaySizeFromViewSize(viewSize);
        boolean isCameraRotated = cameraHelper.isCameraRotated();

        // Connect the converter to the camera-preview frames as its input (via
        // previewFrameTexture), and configure the output width and height as the computed
        // display size.
        converter.setSurfaceTextureAndAttachToGLContext(
                previewFrameTexture,
                isCameraRotated ? displaySize.getHeight() : displaySize.getWidth(),
                isCameraRotated ? displaySize.getWidth() : displaySize.getHeight());
    }

    private void setupPreviewDisplayView() {
        previewDisplayView.setVisibility(View.GONE);
        ViewGroup viewGroup = findViewById(R.id.preview_display_layout);
        viewGroup.addView(previewDisplayView);

        previewDisplayView
                .getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(holder.getSurface());
                            }

                            @Override
                            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                                onPreviewDisplaySurfaceChanged(holder, format, width, height);
                            }

                            @Override
                            public void surfaceDestroyed(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(null);
                            }
                        });
    }

    private static String getPoseLandmarksDebugString(NormalizedLandmarkList poseLandmarks) {
        String poseLandmarkStr = "Pose landmarks: " + poseLandmarks.getLandmarkCount() + "\n";
        int landmarkIndex = 0;
        for (NormalizedLandmark landmark : poseLandmarks.getLandmarkList()) {
            poseLandmarkStr +=
                    "\tLandmark ["
                            + landmarkIndex
                            + "]: ("
                            + landmark.getX()
                            + ", "
                            + landmark.getY()
                            + ", "
                            + landmark.getZ()
                            + ")\n";
            ++landmarkIndex;
        }
        return poseLandmarkStr;
    }
}