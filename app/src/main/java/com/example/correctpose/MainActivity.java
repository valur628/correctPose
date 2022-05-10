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
    private TextView tv3;
    private TextView tv4;
    private TextView tv5;

    class markPoint {
        float x;
        float y;
        float z;
    }
    private markPoint leftLegPoint_23, leftLegPoint_25, leftLegPoint_27, leftLegPoint_29, leftLegPoint_31;
    //왼쪽 하반신(다리) 랜드마크 포인트
    private markPoint rightLegPoint_24, rightLegPoint_26, rightLegPoint_28, rightLegPoint_30, rightLegPoint_32;
    //오른쪽 하반신(다리) 랜드마크 포인트
    private markPoint leftArmPoint_11, leftArmPoint_13, leftArmPoint_15, leftArmPoint_17, leftArmPoint_19, leftArmPoint_21;
    //왼쪽 상반신(팔) 랜드마크 포인트
    private markPoint rightArmPoint_12, rightArmPoint_14, rightArmPoint_16, rightArmPoint_18, rightArmPoint_20, rightArmPoint_22;
    //오른쪽 상반신(팔) 랜드마크 포인트
    private markPoint faceMouthPoint_9, faceMouthPoint_10;
    //얼굴(입) 랜드마크 포인트
    private markPoint faceEarPoint_7, faceEarPoint_8;
    //얼굴(귀) 랜드마크 포인트
    private markPoint faceLeftEyePoint_1, faceLeftEyePoint_2, faceLeftEyePoint_3;
    //얼굴(왼쪽 눈) 랜드마크 포인트
    private markPoint faceLeftEyePoint_4, faceLeftEyePoint_5, faceLeftEyePoint_6;
    //얼굴(오른쪽 눈) 랜드마크 포인트
    private markPoint faceNosePoint_0;
    //얼굴(코) 랜드마크 포인트
    private float ratioPoint_1a, ratioPoint_1b, ratioPoint_2a, ratioPoint_2b;
    //비율 계산에 쓰일 포인트 변수 (왼쪽, 오른쪽)
    private markPoint leftArmRatioMeasurement_11, leftArmRatioMeasurement_13, leftArmRatioMeasurement_15;
    //비율 계산값 변수


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getContentViewLayoutResId());
        tv = findViewById(R.id.tv);
        tv2 = findViewById(R.id.tv2);
        tv3 = findViewById(R.id.tv3);
        tv4 = findViewById(R.id.tv4);
        tv5 = findViewById(R.id.tv5);
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


        // To show verbose logging, run:
        // adb shell setprop log.tag.MainActivity VERBOSE

        if (Log.isLoggable(TAG, Log.WARN)) {
            processor.addPacketCallback(
                    OUTPUT_LANDMARKS_STREAM_NAME,
                    (packet) -> {
                        List<NormalizedLandmarkList> poseLandmarks =
                                PacketGetter.getProtoVector(packet, NormalizedLandmarkList.parser());

                        ratioPoint_1a = poseLandmarks.get(0).getLandmarkList().get(11).getY() * 1920f;
                        ratioPoint_1b = poseLandmarks.get(0).getLandmarkList().get(13).getY() * 1920f;
                        ratioPoint_2a = poseLandmarks.get(0).getLandmarkList().get(12).getY() * 1920f;
                        ratioPoint_2b = poseLandmarks.get(0).getLandmarkList().get(14).getY() * 1920f;

                        leftArmPoint_11.x = poseLandmarks.get(0).getLandmarkList().get(11).getX() * 1080f;
                        leftArmPoint_11.y = poseLandmarks.get(0).getLandmarkList().get(11).getY() * 1080f;
                        leftArmPoint_11.z = poseLandmarks.get(0).getLandmarkList().get(11).getZ() * 1080f;
                        leftArmPoint_13.x = poseLandmarks.get(0).getLandmarkList().get(13).getX() * 1080f;
                        leftArmPoint_13.y = poseLandmarks.get(0).getLandmarkList().get(13).getY() * 1080f;
                        leftArmPoint_13.z = poseLandmarks.get(0).getLandmarkList().get(13).getZ() * 1080f;
                        leftArmPoint_15.x = poseLandmarks.get(0).getLandmarkList().get(15).getX() * 1080f;
                        leftArmPoint_15.y = poseLandmarks.get(0).getLandmarkList().get(15).getY() * 1080f;
                        leftArmPoint_15.z = poseLandmarks.get(0).getLandmarkList().get(15).getZ() * 1080f;
                        leftArmRatioMeasurement_11.x = (leftArmPoint_11.x) / (ratioPoint_1b - ratioPoint_1a);
                        leftArmRatioMeasurement_11.y = (leftArmPoint_11.y) / (ratioPoint_1b - ratioPoint_1a);
                        leftArmRatioMeasurement_11.z = (leftArmPoint_11.z) / (ratioPoint_1b - ratioPoint_1a);
                        leftArmRatioMeasurement_13.x = (leftArmPoint_13.x) / (ratioPoint_1b - ratioPoint_1a);
                        leftArmRatioMeasurement_13.y = (leftArmPoint_13.y) / (ratioPoint_1b - ratioPoint_1a);
                        leftArmRatioMeasurement_13.z = (leftArmPoint_13.z) / (ratioPoint_1b - ratioPoint_1a);
                        leftArmRatioMeasurement_15.x = (leftArmPoint_15.x) / (ratioPoint_1b - ratioPoint_1a);
                        leftArmRatioMeasurement_15.y = (leftArmPoint_15.y) / (ratioPoint_1b - ratioPoint_1a);
                        leftArmRatioMeasurement_15.z = (leftArmPoint_15.z) / (ratioPoint_1b - ratioPoint_1a);
                        tv.setText(leftArmPoint_11.x + " =11X / 11Y= " + leftArmPoint_11.y);
                        tv2.setText(leftArmPoint_13.x + " =13X / 13Y= " + leftArmPoint_13.y);
                        tv3.setText(leftArmPoint_15.x + " =15X / 15Y= " + leftArmPoint_15.y);
                        tv4.setText(leftArmPoint_13.z + " =13Z / 15Z= " + leftArmPoint_15.z);
                        tv5.setText(getLandmarksAngle(leftArmPoint_11, leftArmPoint_13, leftArmPoint_15, 'x', 'y') + " =AngleXY 13 AngleXZ= " + getLandmarksAngle(leftArmPoint_11, leftArmPoint_13, leftArmPoint_15, 'x', 'z'));
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
    public static float getLandmarksAngle(markPoint p1, markPoint p2, markPoint p3, char a, char b) {
        float p1_2 = 0f, p2_3 = 0f, p3_1 = 0f;
        if(a == b) { return 0; }
        else if((a == 'x' || b == 'x') && (a == 'y' || b == 'y')) {
            p1_2 = (float) Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2));
            p2_3 = (float) Math.sqrt(Math.pow(p2.x - p3.x, 2) + Math.pow(p2.y - p3.y, 2));
            p3_1 = (float) Math.sqrt(Math.pow(p3.x - p1.x, 2) + Math.pow(p3.y - p1.y, 2));
        }
        else if((a == 'x' || b == 'x') && (a == 'z' || b == 'z')) {
            p1_2 = (float) Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.z - p2.z, 2));
            p2_3 = (float) Math.sqrt(Math.pow(p2.x - p3.x, 2) + Math.pow(p2.z - p3.z, 2));
            p3_1 = (float) Math.sqrt(Math.pow(p3.x - p1.x, 2) + Math.pow(p3.z - p1.z, 2));
        }
        else if((a == 'y' || b == 'y') && (a == 'z' || b == 'z')) {
            p1_2 = (float) Math.sqrt(Math.pow(p1.y - p2.y, 2) + Math.pow(p1.z - p2.z, 2));
            p2_3 = (float) Math.sqrt(Math.pow(p2.y - p3.y, 2) + Math.pow(p2.z - p3.z, 2));
            p3_1 = (float) Math.sqrt(Math.pow(p3.y - p1.y, 2) + Math.pow(p3.z - p1.z, 2));
        }
        float radian = (float) Math.acos((p1_2 * p1_2 + p2_3 * p2_3 - p3_1 * p3_1) / (2 * p1_2 * p2_3));
        float degree = (float) (radian / Math.PI * 180);
        return degree;
    }

}