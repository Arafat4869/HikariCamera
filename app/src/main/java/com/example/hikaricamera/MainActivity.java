package com.example.hikaricamera;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
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
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static android.graphics.Color.argb;

public class MainActivity extends AppCompatActivity {

    //Image rotation handling
    private static final SparseIntArray Orient=new SparseIntArray();
    static{
        Orient.append(Surface.ROTATION_0,90);
        Orient.append(Surface.ROTATION_90,0);
        Orient.append(Surface.ROTATION_180,270);
        Orient.append(Surface.ROTATION_270,180);
    }

   // private FloatActionButton Capture;
    //private Button Gallery;
    private TextureView ttView;
    private String camId,pathofimage;
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
    FloatingActionButton Capture,Gallery,Edit,addRed,addBlue,addGreen,addGrey;
    Animation flbopen,flbclose,rotfor,rotback;
    boolean isOpen=false;


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

        Capture = findViewById(R.id.Capture);
        Gallery = findViewById(R.id.Gallery);
        Edit = findViewById(R.id.Edit);
        addRed = findViewById(R.id.addRed);
        addBlue = findViewById(R.id.addBlue);
        addGreen = findViewById(R.id.addGreen);
        addGrey= findViewById(R.id.addGrey);

        flbopen= AnimationUtils.loadAnimation(this,R.anim.floatb_open);
        flbclose= AnimationUtils.loadAnimation(this,R.anim.floatb_close);

        rotfor= AnimationUtils.loadAnimation(this,R.anim.rotate_for);
        rotback= AnimationUtils.loadAnimation(this,R.anim.rotate_back);

        Capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });
        Gallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent,10);
                Toast.makeText(MainActivity.this,"Gallery Opened",Toast.LENGTH_LONG).show();
            }
        });

        Edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                animateFloat();
                Toast.makeText(MainActivity.this, "Select a Format", Toast.LENGTH_SHORT).show();
            }
        });
        addRed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                animateFloat();
                Intent myfile=new Intent(Intent.ACTION_GET_CONTENT);
                myfile.setType("*/*");
                startActivityForResult(myfile,10);
                Bitmap rscale = BitmapFactory.decodeFile(pathofimage);
                int width = rscale.getWidth();
                int height = rscale.getHeight();

                float bitmapRatio = (float) width / (float) height;
                if (bitmapRatio > 1) {
                    width = 1000;
                    height = (int) (width / bitmapRatio);
                } else {
                    height = 1000;
                    width = (int) (height * bitmapRatio);
                }
                rscale = Bitmap.createScaledBitmap(rscale, width, height, true);
                Bitmap rscalefinal = Bitmap.createBitmap(rscale.getWidth(),rscale.getHeight(),rscale.getConfig());
                int a,r=0,g=0,b=0,i,j,colorpixel;
                height=rscale.getHeight();
                width=rscale.getWidth();
                for(i=0;i<width;i+=2) {
                    for (j = 0; j < height; j+=2) {
                        //Toast.makeText(MainActivity.this, i+" "+j, Toast.LENGTH_SHORT).show();
                        System.out.println(i+ " "+width+" "+j+" "+height);
                        colorpixel = rscale.getPixel(i, j);
                        a = Color.alpha(colorpixel);
                        r = Color.red(colorpixel);
                        g = Color.green(colorpixel);
                        b = Color.blue(colorpixel);
                        r = g = b = (r + g + b) / 3;
                        rscalefinal.setPixel(i, j, argb(a, r, 0, 0));
                    }
                }
                Matrix mat = new Matrix();
                mat.postRotate(90);
                rscalefinal = Bitmap.createBitmap(rscalefinal, 0, 0, rscalefinal.getWidth(), rscalefinal.getHeight(), mat, true);
                File gfile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)+"/Camera/"+currentDateFormat()+"GrayScale.jpg");
                try {
                    FileOutputStream out = new FileOutputStream(gfile);
                    rscalefinal.compress(Bitmap.CompressFormat.JPEG, 90, out);
                    out.flush();
                    out.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://"+gfile)));
                Toast.makeText(MainActivity.this, "Saved "+file, Toast.LENGTH_SHORT).show();
                createCameraPreview();
            }
        });
        addBlue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                animateFloat();
                Intent myfile=new Intent(Intent.ACTION_GET_CONTENT);
                myfile.setType("*/*");
                startActivityForResult(myfile,10);
                Bitmap bscale = BitmapFactory.decodeFile(pathofimage);
                int width = bscale.getWidth();
                int height = bscale.getHeight();

                float bitmapRatio = (float) width / (float) height;
                if (bitmapRatio > 1) {
                    width = 1000;
                    height = (int) (width / bitmapRatio);
                } else {
                    height = 1000;
                    width = (int) (height * bitmapRatio);
                }
                bscale = Bitmap.createScaledBitmap(bscale, width, height, true);
                Bitmap bscalefinal = Bitmap.createBitmap(bscale.getWidth(),bscale.getHeight(),bscale.getConfig());
                int a,r=0,g=0,b=0,i,j,colorpixel;
                height=bscale.getHeight();
                width=bscale.getWidth();
                for(i=0;i<width;i+=2) {
                    for (j = 0; j < height; j+=2) {
                        //Toast.makeText(MainActivity.this, i+" "+j, Toast.LENGTH_SHORT).show();
                        System.out.println(i+ " "+width+" "+j+" "+height);
                        colorpixel = bscale.getPixel(i, j);
                        a = Color.alpha(colorpixel);
                        r = Color.red(colorpixel);
                        g = Color.green(colorpixel);
                        b = Color.blue(colorpixel);
                        r=g=b=(r+g+b)/3;
                        bscalefinal.setPixel(i, j, argb(a, 0, 0, b));
                    }
                }
                Matrix mat = new Matrix();
                mat.postRotate(90);
                bscalefinal = Bitmap.createBitmap(bscalefinal, 0, 0, bscalefinal.getWidth(), bscalefinal.getHeight(), mat, true);
                File gfile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)+"/Camera/"+currentDateFormat()+"GrayScale.jpg");
                try {
                    FileOutputStream out = new FileOutputStream(gfile);
                    bscalefinal.compress(Bitmap.CompressFormat.JPEG, 90, out);
                    out.flush();
                    out.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://"+gfile)));
                Toast.makeText(MainActivity.this, "Saved "+file, Toast.LENGTH_SHORT).show();
                createCameraPreview();
            }
        });
        addGreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                animateFloat();
                Intent myfile=new Intent(Intent.ACTION_GET_CONTENT);
                myfile.setType("*/*");
                startActivityForResult(myfile,10);
                Bitmap grscale = BitmapFactory.decodeFile(pathofimage);
                int width = grscale.getWidth();
                int height = grscale.getHeight();

                float bitmapRatio = (float) width / (float) height;
                if (bitmapRatio > 1) {
                    width = 1000;
                    height = (int) (width / bitmapRatio);
                } else {
                    height = 1000;
                    width = (int) (height * bitmapRatio);
                }
                grscale = Bitmap.createScaledBitmap(grscale, width, height, true);
                Bitmap grscalefinal = Bitmap.createBitmap(grscale.getWidth(),grscale.getHeight(),grscale.getConfig());
                int a,r=0,g=0,b=0,i,j,colorpixel;
                height=grscale.getHeight();
                width=grscale.getWidth();
                for(i=0;i<width;i+=2) {
                    for (j = 0; j < height; j+=2) {
                        //Toast.makeText(MainActivity.this, i+" "+j, Toast.LENGTH_SHORT).show();
                        System.out.println(i+ " "+width+" "+j+" "+height);
                        colorpixel = grscale.getPixel(i, j);
                        a = Color.alpha(colorpixel);
                        r = Color.red(colorpixel);
                        g = Color.green(colorpixel);
                        b = Color.blue(colorpixel);
                        r = g = b = (r + g + b) / 3;
                        grscalefinal.setPixel(i, j, argb(a, r, 0, 0));
                    }
                }
                Matrix mat = new Matrix();
                mat.postRotate(90);
                grscalefinal = Bitmap.createBitmap(grscalefinal, 0, 0, grscalefinal.getWidth(), grscalefinal.getHeight(), mat, true);
                File gfile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)+"/Camera/"+currentDateFormat()+"GrayScale.jpg");
                try {
                    FileOutputStream out = new FileOutputStream(gfile);
                    grscalefinal.compress(Bitmap.CompressFormat.JPEG, 90, out);
                    out.flush();
                    out.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://"+gfile)));
                Toast.makeText(MainActivity.this, "Saved "+file, Toast.LENGTH_SHORT).show();
                createCameraPreview();
            }
        });
        addGrey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                animateFloat();
                Intent myfile=new Intent(Intent.ACTION_GET_CONTENT);
                myfile.setType("*/*");
                startActivityForResult(myfile,10);
                Bitmap gyscale = BitmapFactory.decodeFile(pathofimage);
                int width = gyscale.getWidth();
                int height = gyscale.getHeight();

                float bitmapRatio = (float) width / (float) height;
                if (bitmapRatio > 1) {
                    width = 1000;
                    height = (int) (width / bitmapRatio);
                } else {
                    height = 1000;
                    width = (int) (height * bitmapRatio);
                }
                gyscale = Bitmap.createScaledBitmap(gyscale, width, height, true);
                Bitmap gyscalefinal = Bitmap.createBitmap(gyscale.getWidth(),gyscale.getHeight(),gyscale.getConfig());
                int a,r=0,g=0,b=0,i,j,colorpixel;
                height=gyscale.getHeight();
                width=gyscale.getWidth();
                for(i=0;i<width;i+=2) {
                    for (j = 0; j < height; j+=2) {
                        //Toast.makeText(MainActivity.this, i+" "+j, Toast.LENGTH_SHORT).show();
                        System.out.println(i+ " "+width+" "+j+" "+height);
                        colorpixel = gyscale.getPixel(i, j);
                        a = Color.alpha(colorpixel);
                        r = Color.red(colorpixel);
                        g = Color.green(colorpixel);
                        b = Color.blue(colorpixel);
                        r = g = b = (r + g + b) / 3;
                        gyscalefinal.setPixel(i, j, argb(a, r, 0, 0));
                    }
                }
                Matrix mat = new Matrix();
                mat.postRotate(90);
                gyscalefinal = Bitmap.createBitmap(gyscalefinal, 0, 0, gyscalefinal.getWidth(), gyscalefinal.getHeight(), mat, true);
                File gfile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)+"/Camera/"+currentDateFormat()+"GrayScale.jpg");
                try {
                    FileOutputStream out = new FileOutputStream(gfile);
                    gyscalefinal.compress(Bitmap.CompressFormat.JPEG, 90, out);
                    out.flush();
                    out.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://"+gfile)));
                Toast.makeText(MainActivity.this, "Saved "+file, Toast.LENGTH_SHORT).show();
                createCameraPreview();
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
            int wid=100;
            int hei=100;
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
            texture.setDefaultBufferSize(photosize.getWidth(),photosize.getHeight()-4000);
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

    private void animateFloat()
    {
        if(isOpen){
            Edit.startAnimation(rotfor);
            addRed.startAnimation(flbclose);
            addBlue.startAnimation(flbclose);
            addGreen.startAnimation(flbclose);
            addGrey.startAnimation(flbclose);
            addRed.setClickable(false);
            addBlue.setClickable(false);
            addGreen.setClickable(false);
            addGrey.setClickable(false);
            isOpen= false;

        }
        else
        {
            Edit.startAnimation(rotback);
            addRed.startAnimation(flbopen);
            addBlue.startAnimation(flbopen);
            addGreen.startAnimation(flbopen);
            addGrey.startAnimation(flbopen);
            addRed.setClickable(true);
            addBlue.setClickable(true);
            addGreen.setClickable(true);
            addGrey.setClickable(true);
            isOpen= true;
        }
    }

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
    /*public static Bitmap getResizedBitmap(Bitmap img){
        int width=img.getWidth();
        int height=img.getHeight();
        float scalewidth=(float)()
    }*/

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode){
            case 10:
                if(resultCode==RESULT_OK){
                    pathofimage=data.getData().getPath();
                }
        }
    }
}
