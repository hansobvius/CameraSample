package com.example.android.cameravision;

import android.Manifest;
import android.app.Activity;
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
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class CameraActivity extends AppCompatActivity{

    public static final String IMAGE_INTENT_KEY = "image_intent_key";

    private byte[] mByte;

    //Check for state orientation of output image
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private static final int REQUEST_CAMERA_PERMISSION = 100;

    private int captureCount;

    private TextureView mTextureView;
    private Button mClickButton;

    private int mCameraChosen;

    private String mCameraId;
    private Size mImageSize;
    private CameraDevice mCameraDevice;

    private ImageReader mImageReader;

    private File mFile;

    private Image mImage = null;

    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;


    private CaptureRequest.Builder mCaptureRequestBuilder;
    private CameraCaptureSession mCameraCaptureSession;

    //Callback listener is used when a TextureView is available
    TextureView.SurfaceTextureListener mTextureListener = new TextureView.SurfaceTextureListener() {
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
    };

    //Callback that check the camera device state
    CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Toast.makeText(getApplicationContext(), "Camera Opened", Toast.LENGTH_SHORT).show();
            mCameraDevice = camera;
            if(mCameraDevice != null){
                createCameraPreview();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Toast.makeText(getApplicationContext(), "Camera Disconnected", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Toast.makeText(getApplicationContext(), "Error!", Toast.LENGTH_SHORT).show();
        }
    };

    //This method set the texture listeenr to on texture view layout
    private void initCamera(){
        mTextureView.setSurfaceTextureListener(mTextureListener);
    }

    //Using the camera manager we open the camera
    private void openCamera() {

        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try{
            //Grab the id of the choosen camera. By default the value it's 0, that's mean the back camera
            mCameraId = cameraManager.getCameraIdList()[mCameraChosen];
            //Grab the camera characteristics from the chosen camera
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(mCameraId);
            //Grab the available stream configurations that the chosen device supports
            StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert streamConfigurationMap != null;
            //Get a list of sizes compatible with the requested image, in this case the first index value
            mImageSize = streamConfigurationMap.getOutputSizes(SurfaceTexture.class)[0];

            //Check for user permission camera
            if((ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA)) != (PackageManager.PERMISSION_GRANTED)){
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_CAMERA_PERMISSION
                );
            }
            cameraManager.openCamera(mCameraId, mStateCallback, null);

        }catch(CameraAccessException e){
            e.printStackTrace();
        }
    }

    private void createCameraPreview(){

        try{
            //Return the texture surface from texture view
            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
            assert surfaceTexture != null;
            //Get the width and the height from the image dimensions from the view
            surfaceTexture.setDefaultBufferSize(mImageSize.getWidth(), mImageSize.getHeight());
            //A Surface is generally created by or from a consumer of image buffers. here we create a surface form a surface texture
            Surface surface = new Surface(surfaceTexture);
            //The CameraRequest it's an immutable package of settings and outputs needed to capture a single
            //image from the camera device.
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            //Here we add a surface to the list of targets for this request
            mCaptureRequestBuilder.addTarget(surface);

            /**
             * CameraCaptureSession.StateCallback is passed as argument, and is used  for preparing the camera capture session
             * will return CameraCaptureSession which allows us to start the preview.
             */
            mCameraDevice.createCaptureSession(
                    Arrays.asList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if(mCameraDevice == null) return;
                            mCameraCaptureSession = session;
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                        }
                    },
                    null
            );

        }catch(CameraAccessException e){
            e.printStackTrace();
        }
    }

    private void updatePreview(){
        if(mCameraDevice == null) Toast.makeText(getApplicationContext(), "camera error", Toast.LENGTH_SHORT).show();

        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);

        try{

            mCameraCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, mBackgroundHandler);

        }catch(CameraAccessException e){
            e.printStackTrace();
        }
    }

    private void takePicture(){
        //if doesn't have
        if(mCameraDevice == null) return;

        //A system service manager for detecting, characterizing, and connecting to
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            //Query the capabilities of a camera device. These capabilities are immutable for a given camera
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(mCameraDevice.getId());

            //A array to store the width and the height from a image
            Size[] jpegSize = null;

            if (cameraCharacteristics != null) {

                //put the list of sizes compatible with the requested image on the Size[] variable
                jpegSize = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);

                //capture the image with a custom size
                int width = 640;
                int height = 420;
                if(jpegSize != null && jpegSize.length > 0){
                    width = jpegSize[0].getWidth();
                    height = jpegSize[0].getHeight();
                }

                //The image reader allows direct applicatipn acessto a image data rendered into a surface
                mImageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);

                //Here we create a List to insert the surfaces availables. The list will be the lenght equals to 2
                List<Surface> outputSurface = new ArrayList<>(2);
                outputSurface.add(mImageReader.getSurface());
                outputSurface.add(new Surface(mTextureView.getSurfaceTexture()));

                //The capture request contains the configuration for the capture hardware
                final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

                captureBuilder.addTarget(mImageReader.getSurface());
                captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

                //Check the orientation base on device, that the actual totation if window display
                int rotation = getWindowManager().getDefaultDisplay().getRotation();
                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

                //Create a new file location to store the image
                mFile = new File(Environment.getExternalStorageDirectory() + "/" + UUID.randomUUID().toString() + ".jpg");

                /**
                 * ImageReader.OnImageAvailableListener is used to Retrieving image data from
                 * CameraOutputSession through ImageReader
                 */
                ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader reader) {

                        try{
                            mImage = reader.acquireLatestImage();
                            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
                            mByte = new byte[buffer.capacity()];
                            buffer.get(mByte);
                            save(mByte);
                        }catch(FileNotFoundException e){
                            e.printStackTrace();
                        }catch(IOException e){
                            e.printStackTrace();
                        }finally{
                            if (mImage != null) mImage.close();
                        }
                    }

                    private void save(byte[] bytes) throws IOException{
                        OutputStream outputStream = null;
                        try{
                            outputStream = new FileOutputStream(mFile);
                            outputStream.write(bytes);
                        }finally {
                            if(outputStream != null){
                                outputStream.close();
                            }
                        }
                    }
                };

                mImageReader.setOnImageAvailableListener(readerListener, mBackgroundHandler);

                //callback is invoked when a request triggers a capture to start
                final CameraCaptureSession.CaptureCallback mCaptureSessionCalbackListener = new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                        super.onCaptureCompleted(session, request, result);
                        Toast.makeText(getApplicationContext(), "Capture Completed " + Integer.toString(captureCount), Toast.LENGTH_SHORT).show();
                        captureCount++;
                        //createCameraPreview();
                        startNewActivity(mByte);
                    }
                };
                /**
                 * CameraCaptureSession.StateCallback is used  for preparing the camera capture session
                 * will return CameraCaptureSession which allows us to start the preview.
                 */
                mCameraDevice.createCaptureSession(outputSurface, new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        try{
                            session.capture(captureBuilder.build(), mCaptureSessionCalbackListener, mBackgroundHandler);
                        }catch (CameraAccessException e){
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                    }
                }, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startNewActivity(byte[] b){
        Intent intent = new Intent(getApplicationContext(), ImageActivity.class);
        intent.putExtra(IMAGE_INTENT_KEY, b);
        startActivity(intent);
    }

    private void startBackgroundThread(){
        mBackgroundThread = new HandlerThread("camera_background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread(){
        mBackgroundThread.quitSafely();
        try{
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        }catch (InterruptedException e){
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        int id = item.getItemId();
        switch (id){
            case R.id.back_camera:{
                Toast.makeText(getApplicationContext(), "Back Camera", Toast.LENGTH_SHORT).show();
                this.mCameraChosen = CameraCharacteristics.LENS_FACING_BACK;
                mCameraDevice.close();
                openCamera();
                return true;
            }
            case R.id.front_camera:{
                Toast.makeText(getApplicationContext(), "Front Camera", Toast.LENGTH_SHORT).show();
                this.mCameraChosen = CameraCharacteristics.LENS_FACING_FRONT;
                mCameraDevice.close();
                openCamera();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        mTextureView = findViewById(R.id.texture_view);
        mClickButton = findViewById(R.id.button_click);

        mClickButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });

        initCamera();
    }

    @Override
    public void onPause(){
        super.onPause();
        stopBackgroundThread();
    }

    @Override
    public void onResume(){
        super.onResume();
        startBackgroundThread();
        if(mTextureView.isAvailable()){
            openCamera();
        }else{
            initCamera();
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        stopBackgroundThread();
        mCameraDevice.close();
        mImage = null;
        mImageReader = null;
    }
}
