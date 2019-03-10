package com.example.hikaricamera;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private Button Capture;
    private TextureView ttView;

    //Image rotation handling
    private static final SparseIntArray Orient=new SparseIntArray();
    static {
        Orient.append(Surface.ROTATION_0,90);
        Orient.append(Surface.ROTATION_90,0);
        Orient.append(Surface.ROTATION_180,270);
        Orient.append(Surface.ROTATION_270,180);
    }

    private String camId;
    private CameraDevice camDev;
    private CameraCaptureSession capSes;
    private CaptureRequest.Builder capReqB;
    private Size photosize;
    private ImageReader imgRead;
    private File file;
    private static final int camReqPer=200;
    private boolean flashchk;
    private Handler backHand;
    private HandlerThread backThread;

    CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            camDev = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camDev, int i) {
            camDev.close();
            camDev=null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ttView=(TextureView)findViewById(R.id.ttView);
        assert ttView != null;

        ttView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                openCamera();

            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });

        Capture = (Button)findViewById(R.id.Capture);
        Capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });
    }

    private void takePicture() {
        if(camDev==null)
            return;
        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics camchar=manager.getCameraCharacteristics(camDev.getId());
            Size[] picSize = null;
            if(camchar!=null) {
                picSize = camchar.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            int wid=1280;
            int hei=720;
            if(picSize!=null  &&  picSize.length>0){
                wid= picSize[0].getWidth();
                hei=picSize[0].getHeight();
            }
            ImageReader reader=ImageReader.newInstance(wid,hei,ImageFormat.JPEG,1);
            List<Surface> opSur=new ArrayList<>(2);
            opSur.add(reader.getSurface());
            opSur.add(new Surface(ttView.getSurfaceTexture()));

            final CaptureRequest.Builder captureBuild= camDev.createCaptureRequest(camDev.TEMPLATE_STILL_CAPTURE);
            captureBuild.addTarget(reader.getSurface());
            captureBuild.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            int rotate= getWindowManager().getDefaultDisplay().getRotation();
            captureBuild.set(CaptureRequest.JPEG_ORIENTATION,Orient.get(rotate));

            file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)+"/Camera/"+currentDateFormat()+".jpg");
            ImageReader.OnImageAvailableListener reListener=new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image=null;
                    try {
                        image=reader.acquireLatestImage();
                        ByteBuffer Bbuff=image.getPlanes()[0].getBuffer();
                        byte[] bytes=new byte[Bbuff.capacity()];
                        Bbuff.get(bytes);
                        save(bytes);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                    finally{
                        if(image !=null)
                            image.close();

                    }
                }
                private void save(byte[] bytes) throws IOException{
                    OutputStream opStream=null;
                    try{
                        opStream =new FileOutputStream(file);
                        opStream.write(bytes);
                    }catch(Exception e) {
                        e.printStackTrace();
                    }finally {
                        if(opStream!=null)
                            opStream.close();
                    }
                }
            };
            reader.setOnImageAvailableListener(reListener,backHand);
            final CameraCaptureSession.CaptureCallback capListen = new CameraCaptureSession.CaptureCallback(){
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://"+file)));
                    Toast.makeText(MainActivity.this, "Saved "+file, Toast.LENGTH_SHORT).show();
                    createCameraPreview();
                }
            };

            camDev.createCaptureSession(opSur, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    try {
                        cameraCaptureSession.capture(captureBuild.build(), capListen, backHand);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                }
            },backHand);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createCameraPreview() {
        try{
            SurfaceTexture texture = ttView.getSurfaceTexture();
            assert  texture != null;
            texture.setDefaultBufferSize(photosize.getWidth(),photosize.getHeight());
            Surface surface = new Surface(texture);
            capReqB = camDev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            capReqB.addTarget(surface);
            camDev.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if(camDev == null)
                        return;
                    capSes = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Changed", Toast.LENGTH_SHORT).show();
                }
            },null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if(camDev == null)
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        capReqB.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
        try{
            capSes.setRepeatingRequest(capReqB.build(),null,backHand);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void openCamera(){
        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try{
            camId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(camId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            photosize = map.getOutputSizes(SurfaceTexture.class)[0];
            if(ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            {
                ActivityCompat.requestPermissions(this,new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },camReqPer);
                return;
            }
            manager.openCamera(camId,stateCallback,null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == camReqPer)
        {
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED)
            {
                Toast.makeText(this, "You can't use camera without permission", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if(ttView.isAvailable())
            openCamera();
        else
            ttView.setSurfaceTextureListener(textureListener);
    }

    @Override
    protected void onPause() {
        stopBackgroundThread();
        super.onPause();
    }

    private void stopBackgroundThread() {
        backThread.quitSafely();
        try{
            backThread.join();
            backThread = null;
            backThread = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread() {
        backThread = new HandlerThread("Camera Background");
        backThread.start();
        backHand= new Handler(backThread.getLooper());
    }
    private String currentDateFormat() {
        SimpleDateFormat myformat=new SimpleDateFormat("YYYY_MM_DD-HH:MM:SS");
        String time=myformat.format(new Date());
        return time;
    }
}
