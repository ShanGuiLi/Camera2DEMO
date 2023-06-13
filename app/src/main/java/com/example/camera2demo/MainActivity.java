package com.example.camera2demo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.icu.text.SimpleDateFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "Phildebug";
    private TextureView textureView;//继承自View，必须在硬件加速窗口中
    private HandlerThread handlerThread;//运行不应该被阻塞UI的任务的附加线程
    private Handler mCameraHandler;//用于在后台运行任务
    private CameraManager cameraManager;//摄像头管理器，用于检测打开摄像头
    private Size previewSize;//最佳的预览尺寸
    private Size mCaptureSize;//最佳的拍照尺寸
    private String mCameraId;//设备ID
    private CameraDevice cameraDevice;//该实例代表摄像头
    private CaptureRequest.Builder captureRequestBuilder;
    private CaptureRequest.Builder previewRequestBuilder;
    private CaptureRequest captureRequest;//捕获请求
    private CaptureRequest previewRequest;//捕获请求
    private CameraCaptureSession mCameraCaptureSession;//预览拍照先通过它创建session
    private CameraCaptureSession mCameraPreviewSession;//预览HDR先通过它创建session
    private Button btn_photo;//拍照按钮
    private ImageReader imageCaptureReader;//用于图像保存
    private ImageReader imagePreviewReader;//用于图像HDRD
    private static final SparseArray ORIENTATION = new SparseArray();//图片旋转方向
    static {
        ORIENTATION.append(Surface.ROTATION_0, 90);
        ORIENTATION.append(Surface.ROTATION_90, 0);
        ORIENTATION.append(Surface.ROTATION_180, 270);
        ORIENTATION.append(Surface.ROTATION_270, 180);
    }
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {//对手机存储权限
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private int exposure = 0;//记录输入HDR函数顺序

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.fragment_camera2_basic);
        textureView = findViewById(R.id.texture);
        btn_photo = findViewById(R.id.picture);
        btn_photo.setOnClickListener(OnClick);
    }

    /***
     * 设置点击事件
     */
    private final View.OnClickListener OnClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            //获取摄像头的请求
            try {
                //构建CaptureRequest
                CaptureRequest.Builder cameraDeviceCaptureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                cameraDeviceCaptureRequest.addTarget(imageCaptureReader.getSurface());

                //获取摄像头的方向
                int rotation = getWindowManager().getDefaultDisplay().getRotation();

                //构建CpatureSession
                CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                        super.onCaptureCompleted(session, request, result);

                        Toast.makeText(MainActivity.this, "拍照结束，相片已保存！", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Capture session success!");
                    }
                };

                //设置拍照方向
                cameraDeviceCaptureRequest.set(CaptureRequest.JPEG_ORIENTATION, (Integer) ORIENTATION.get(rotation));
                mCameraCaptureSession.getInputSurface();

                // 设置自动曝光补偿参数
                cameraDeviceCaptureRequest.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                cameraDeviceCaptureRequest.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);

                // 第一张照片捕获
                mCameraCaptureSession.capture(cameraDeviceCaptureRequest.build(), mCaptureCallback, mCameraHandler);

                // 设置自动曝光补偿参数为-2
                cameraDeviceCaptureRequest.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, -2);

                // 第二张照片捕获
                mCameraCaptureSession.capture(cameraDeviceCaptureRequest.build(), mCaptureCallback, mCameraHandler);

                // 设置自动曝光补偿参数为1
                cameraDeviceCaptureRequest.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 1);

                // 第三张照片捕获
                mCameraCaptureSession.capture(cameraDeviceCaptureRequest.build(), mCaptureCallback, mCameraHandler);

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            //获取图像的缓冲区
            //获取文件的存储权限及操作
        }
    };

    @Override
    protected void onResume() {
        super.onResume();

        //开启相机线程
        startCameraThread();
        //textureView回调
        if (!textureView.isAvailable()) {
            textureView.setSurfaceTextureListener(mTextureListener);
        } else {
            startPreview();//开启预览
        }
    }

    /***
     * TextureView生命周期响应
     */
    TextureView.SurfaceTextureListener mTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            //SurfaceTexture组件可用的时候,设置相机参数，并打开摄像头
            //设置摄像头参数
            setUpCamera(width, height);
            //打开摄像头，默认后置
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
            //尺寸发生变化的时候
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            //组件被销毁的时候
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
            //组件更新的时候
        }
    };

    /***
     * 设置摄像头参数
     */
    private void setUpCamera(int width, int height) {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        //拿到摄像头的id
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                //得到摄像头的参数
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);

                // 检查是否支持手动控制曝光模式
                int[] availableAEModes = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES);
                boolean isManualExposureSupported = false;
                for (int aeMode : availableAEModes) {
                    if (aeMode == CameraCharacteristics.CONTROL_AE_MODE_OFF) {
                        isManualExposureSupported = true;
                        break;
                    }
                }

                // 检查是否支持曝光补偿
                boolean isExposureCompensationSupported = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) != null;

                // 输出支持情况
                Log.d(TAG, "手动曝光支持: " + isManualExposureSupported);
                Log.d(TAG, "曝光补偿支持: " + isExposureCompensationSupported);

                Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map != null) { //找到摄像头能够输出的，最符合我们当前屏幕能显示的最小分辨率
                    previewSize = new Size(1920, 1080);
                    mCaptureSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)), new Comparator<Size>() {
                        @Override
                        public int compare(Size o1, Size o2) {
                            return Long.signum(o1.getWidth() * o1.getHeight() - o2.getWidth() * o2.getHeight());
                        }
                    });
                }
                //建立CaptureReader准备存储照片
                setUpCaptureReader();
                setUpPreviewReader();

                mCameraId = cameraId;
                break;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /***
     * 建立CaptureReader
     */
    private void setUpCaptureReader() {
        imageCaptureReader = ImageReader.newInstance(mCaptureSize.getWidth(), mCaptureSize.getHeight(), ImageFormat.YUV_420_888, 3);
        imageCaptureReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Log.d(TAG, "Saved image size: "+mCaptureSize.getWidth()+" * "+mCaptureSize.getHeight());
                imageSaver(reader.acquireNextImage());
                exposure++;
                if(exposure == 3) {
                    Log.d(TAG, "----------------HDR start----------------");
                    HDRP.ImageHDRProcess();
                    Log.d(TAG, "---------------HDR finished--------------");
                }
            }
        }, mCameraHandler);
    }

    /***
     *  建立PreviewReader
     */
    private void setUpPreviewReader() {
        imagePreviewReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 3);
        imagePreviewReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {

            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = reader.acquireNextImage();
                //imageSender(image);
                image.close();//不用imageSender()函数时释放掉
            }
        }, mCameraHandler);
    }

    /***
     * 打开摄像头
     */
    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA, android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return;
        }
        try {
            cameraManager.openCamera(mCameraId, mStateCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) { //摄像头打开
            cameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) { //摄像头关闭
            cameraDevice.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {//摄像头出现错误
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    /***
     * 创建预览对话
     */
    private void startPreview() {
        //获取texture实例，建立图像缓冲区
        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
        //将缓冲区大小配置为相机预览大小
        surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        //用来预览的输出surface
        Surface surface = new Surface(surfaceTexture);

        try {
            //创建拍照和预览请求器
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(surface);

            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            //设置实时帧数据接收
            previewRequestBuilder.addTarget(imagePreviewReader.getSurface());
            //为相机预览创建CameraCaptureSession，建立通道来支持CaptureRequest和CaptureSession会话
            cameraDevice.createCaptureSession(Arrays.asList(surface, imageCaptureReader.getSurface(), imagePreviewReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    //会话准备就绪之后，开始显示预览
                    //将构建的会话赋给field
                    captureRequest = captureRequestBuilder.build();
                    mCameraCaptureSession = session;

                    try {
                        //设置预览界面
                        mCameraPreviewSession = session;
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
                        mCameraPreviewSession.setRepeatingRequest(previewRequestBuilder.build(), null, mCameraHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                }
            }, mCameraHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /***
     * 传递预览图片，返回HDRD结果
     */
    private void imageSender(Image image) {
        Image.Plane[] planes = image.getPlanes();
        byte[] y_data = new byte[0];
        byte[] uv_data = new byte[0];

        if (image.getFormat() == ImageFormat.YUV_420_888) {
            int imageSize = image.getHeight() * image.getWidth();
            //获取Y分量
            ByteBuffer y_buffer = planes[0].getBuffer();
            y_data = new byte[imageSize];
            y_buffer.get(y_data, 0, y_buffer.capacity());

            //获取VU分量
            ByteBuffer uv_buffer = planes[2].getBuffer();
            uv_data = new byte[imageSize / 2];
            uv_buffer.get(uv_data, 0, uv_buffer.capacity());
        }

        //将data数组通过JNI传递给HDRD
        Log.d(TAG, "-----------HDRD start-----------");
        Log.d(TAG, "The HDRD image size: "+image.getWidth()+" * "+image.getHeight());
        int hdr_mod = HDRD.ImageHDRDetection(image.getWidth(), image.getHeight(), y_data, uv_data);

        //将和DRRD的结果输出在屏幕上
        TextView textView = findViewById(R.id.text2);
        Date currentTime = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat(" HH:mm:ss");
        String formattedTime = sdf.format(currentTime);

        if(hdr_mod == 0) {
            textView.setText("不需要" + formattedTime);
        }
        else if(hdr_mod == 1) {
            textView.setText("需要" + formattedTime);
        }
        else if(hdr_mod == 2) {
            textView.setText("需要LowLightHDR" + formattedTime);
        }
        Log.d(TAG, "---------HDRD finished---------");

        image.close();
    }

    /***
     * 保存图片至指定目录
     */
    private void imageSaver(Image image) {
        String fileName;//存储的文件名
        //建立内存缓冲区，获取图片image，返回为一个像素矩阵
        Image.Plane[] planes = image.getPlanes();
        byte[] y_data = new byte[0];
        byte[] uv_data = new byte[0];

        if (image.getFormat()==ImageFormat.YUV_420_888) {
            int imageSize = image.getHeight() * image.getWidth();
            //获取Y分量
            ByteBuffer y_buffer = planes[0].getBuffer();
            y_data = new byte[imageSize];
            y_buffer.get(y_data, 0, y_buffer.capacity());

            //获取VU分量
            ByteBuffer uv_buffer = planes[2].getBuffer();
            uv_data = new byte[imageSize / 2];
            uv_buffer.get(uv_data, 0, uv_buffer.capacity());
        }

        //判断当前的文件目录是否存在，如果不存在就创建这个文件目录
        String path = Environment.getExternalStorageDirectory().getPath()+"/DCIM/Camera2DEMO/";
        Log.d(TAG, "The path: "+path);
        File file = new File(path);
        if (!file.exists()) {
            file.mkdir();
        }

        @SuppressLint("SimpleDateFormat") String timeStamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS").format(new Date());
        //fileName = path + "/IMG_" + timeStamp + ".nv21";
        fileName = path + "/IMG_4096x3072_ev" + exposure + ".nv21";

        try {
            FileOutputStream fileOutputStream = new FileOutputStream(fileName);
            fileOutputStream.write(y_data);
            fileOutputStream.write(uv_data);
            fileOutputStream.close();

            Log.d(TAG,"Write success!");
        } catch (IOException e) {
            Log.d(TAG, "ERROR!");
            e.printStackTrace();
        }

        image.close();
    }

    /***
     * 开启摄像头线程
     */
    private void startCameraThread() {
        handlerThread = new HandlerThread("myHandlerThread");
        handlerThread.start();
        mCameraHandler = new Handler(handlerThread.getLooper());
    }
}