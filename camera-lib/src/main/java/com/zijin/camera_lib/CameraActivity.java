package com.zijin.camera_lib;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Region;
import android.hardware.usb.UsbDevice;
import android.media.FaceDetector;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;

import com.google.gson.Gson;
import com.lgh.uvccamera.UVCCameraProxy;
import com.lgh.uvccamera.bean.PicturePath;
import com.lgh.uvccamera.callback.ConnectCallback;
import com.lgh.uvccamera.callback.PreviewCallback;
import com.lgh.uvccamera.utils.ImageUtil;
import com.zijin.camera_lib.hepler.PictureHelper;
import com.zijin.camera_lib.hepler.ServiceHelper;
import com.zijin.camera_lib.model.dto.FaceResult;
import com.zijin.camera_lib.model.http.FaceService;

import java.util.HashMap;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Response;


public class CameraActivity extends AppCompatActivity {
    // ui
    private TextureView previewView;
    private ConstraintLayout emptyContainer;
    private TextView tvEmptyMsg;
    private TextView tvNotify;
    // logic
    private final Point previewSize = new Point(1920, 1080);
    private final Region faceRegion = new Region(384, 97, 876, 580);
    public final static int REQ_START_CAMERA = 0x0814;
    private final int STATUS_FINDING = 0x0814;
    private final int STATUS_VERIFYING = 0x0815;
    private final int STATUS_VERIFY_SUCCESS = 0x0816;
    private final int STATUS_VERIFY_FAILED = 0x0817;
    private UVCCameraProxy uvcCamera;
    private Context context;


    private final Handler messageHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == STATUS_FINDING) {
                // 人脸检测中
                tvNotify.setText("人脸检测中，请将人脸放入识别区域");
                tvNotify.setTextColor(Color.WHITE);
            } else if (msg.what == STATUS_VERIFYING) {
                // 检测到人脸，人脸识别中
                tvNotify.setText("检测到人脸，识别中");
                tvNotify.setTextColor(Color.WHITE);
            } else if (msg.what == STATUS_VERIFY_SUCCESS) {
                tvNotify.setText("人脸识别成功");
                tvNotify.setTextColor(Color.GREEN);
                // 人脸校验成功
                String response = new Gson().toJson(msg.obj);
                Intent intent = new Intent();
                intent.putExtra("response",  response);
                setResult(Activity.RESULT_OK, intent);
                finish();
            } else if (msg.what == STATUS_VERIFY_FAILED) {
                tvNotify.setText("人脸识别失败");
                tvNotify.setTextColor(Color.RED);
            }
            return true;
        }
    });
    private FaceService faceService;

    public static void start(Activity context, String size, String baseUrl) {
        Intent intent = new Intent(context, CameraActivity.class);
        intent.putExtra("size", size);
        intent.putExtra("base_url", baseUrl);
        context.startActivityForResult(intent, REQ_START_CAMERA);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.new_activity_camera);
        this.context = this;
        initWidget();
        initData();
        initCamera();
        initEvent();
    }

    private void initData() {
        Intent intent = getIntent();
        // init camera preview size
        String size = intent.getStringExtra("size");
        String[] items = size.split("_");
        previewSize.x = Integer.parseInt(items[0]);
        previewSize.y = Integer.parseInt(items[1]);
        String baseUrl = intent.getStringExtra("base_url");
        faceService = ServiceHelper.getFaceServiceInstance(baseUrl);
    }

    private void initWidget() {
        previewView = findViewById(R.id.previewView);
        emptyContainer = findViewById(R.id.emptyContainer);
        tvEmptyMsg = findViewById(R.id.tvEmptyMsg);
        tvNotify = findViewById(R.id.tv_notify);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        if (getActionBar() != null) {
            getActionBar().hide();
        }
    }

    private void initCamera() {
        uvcCamera = new UVCCameraProxy(context);
        uvcCamera.getConfig()
                .isDebug(true)
                .setPicturePath(PicturePath.APPCACHE);
        uvcCamera.setPreviewTexture(previewView);
        uvcCamera.setConnectCallback(new ConnectCallback() {
            @Override
            public void onAttached(UsbDevice usbDevice) {
                uvcCamera.requestPermission(usbDevice);
            }

            @Override
            public void onGranted(UsbDevice usbDevice, boolean granted) {
                if (granted) {
                    uvcCamera.connectDevice(usbDevice);
                } else {
                    showEmptyLayout();
                    tvEmptyMsg.setText(getString(R.string.usb_permission_failed));
                }
            }

            @Override
            public void onConnected(UsbDevice usbDevice) {
                showEmptyLayout();
                tvEmptyMsg.setText("正在启动相机，请稍后");
                uvcCamera.openCamera();
            }

            @Override
            public void onCameraOpened() {
                // support resolution 1920 * 1080  1280 * 720 640 * 480 320 * 240
                resizePreview(previewView, previewSize);
                uvcCamera.setPreviewSize(previewSize.x, previewSize.y);
                uvcCamera.startPreview();
                hideEmptyLayout();
            }

            @Override
            public void onDetached(UsbDevice usbDevice) {
                showEmptyLayout();
                tvEmptyMsg.setText(getString(R.string.cannot_find_usb));
            }
        });
    }

    private void showEmptyLayout() {
        emptyContainer.setVisibility(View.VISIBLE);
    }

    private void hideEmptyLayout() {
        emptyContainer.setVisibility(View.INVISIBLE);
    }

    /**
     * 根据摄像头所设置成的预览尺寸来调整预览控件的尺寸
     *
     * @param previewView
     * @param size
     */
    private void resizePreview(TextureView previewView, Point size) {
        Point screenSize = new Point();
        this.getWindowManager().getDefaultDisplay().getSize(screenSize);
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) previewView.getLayoutParams();
        params.width = screenSize.x;
        params.height = (int) (screenSize.x / (size.x / (size.y * 1.0f)));
        previewView.setLayoutParams(params);
    }

    private void initEvent() {
        uvcCamera.setPreviewCallback(new PreviewCallback() {
            @Override
            public void onPreviewFrame(final byte[] yuv) {
                Bitmap fameBitmap = null;
                Bitmap faceBitmap = null;
                Bitmap faceBitmap565 = null;

                try {
                    // 该方法被阻塞不会造成帧的堆积
                    fameBitmap = ImageUtil.yuv2Bitmap(yuv, previewSize.x, previewSize.y);
                    faceBitmap = Bitmap.createBitmap(fameBitmap, faceRegion.getBounds().left, faceRegion.getBounds().top, faceRegion.getBounds().width(), faceRegion.getBounds().height());
                    faceBitmap565 = faceBitmap.copy(Bitmap.Config.RGB_565, true);
                    FaceDetector faceDetector = new FaceDetector(faceBitmap.getWidth(), faceBitmap.getHeight(), 1);
                    FaceDetector.Face[] faces = new FaceDetector.Face[1];
                    int faceNum = faceDetector.findFaces(faceBitmap565, faces);
                    if (faceNum == 0) {
                        // 正在检测人脸
                        messageHandler.sendEmptyMessage(STATUS_FINDING);
                    } else {
                        // 检测到人脸，人脸校验中...
                        messageHandler.sendEmptyMessage(STATUS_VERIFYING);
                        String faceBase64 = PictureHelper.processPicture(faceBitmap565, PictureHelper.JPEG);
                        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json;charset=UTF-8"), getParams(faceBase64));
                        Call<FaceResult> call = faceService.verifyFace(requestBody);
                        Response<FaceResult> response = call.execute();
                        FaceResult faceResult = response.body();
                        if (faceResult == null || !faceResult.isVerifySuccess()) {
                            // 人脸校验失败
                            messageHandler.sendEmptyMessage(STATUS_VERIFY_FAILED);
                            Thread.sleep(500);
                        } else {
                            // 人脸校验成功
                            Message message = Message.obtain();
                            message.obj = faceResult;
                            message.what = STATUS_VERIFY_SUCCESS;
                            messageHandler.sendMessage(message);
                            uvcCamera.setPreviewCallback(null);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    messageHandler.sendEmptyMessage(STATUS_VERIFY_FAILED);
                } finally {
                    recycleBitmaps(faceBitmap, faceBitmap565, fameBitmap);
                }
                recycleBitmaps(faceBitmap, faceBitmap565, fameBitmap);
            }
        });
    }

    private String getParams(String faceBase64) {
        Gson gson = new Gson();
        HashMap<String, String> paramsMap = new HashMap<>();
        paramsMap.put("faceBase64", faceBase64);
        return gson.toJson(paramsMap);
    }

    /**
     * 回收 bitmap 占用的资源
     *
     * @param bitmaps
     */
    private void recycleBitmaps(Bitmap... bitmaps) {
        for (Bitmap bitmap : bitmaps) {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }

    @Override
    public void onBackPressed() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}