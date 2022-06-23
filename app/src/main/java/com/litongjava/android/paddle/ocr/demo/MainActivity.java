package com.litongjava.android.paddle.ocr.demo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;

import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.litongjava.android.paddle.ocr.OCRPredictorNative;
import com.litongjava.android.paddle.ocr.PaddleOcrUtils;
import com.litongjava.android.paddle.ocr.Predictor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
  // 1.放在自己的onCreate()前
  private static final String TAG = "MainActivity";
  private static final int TAKE_PHOTO_REQUEST_CODE = 1;

  //providerName
  String appFileprovider = "com.litongjava.android.paddle.ocr.demo.fileprovider";

  protected ImageView ivInputImage;
  protected TextView tvOutputResult;

  protected String imagePath = "";
  private String currentPhotoPath;

  protected Predictor predictor = new Predictor();

  // Used to load the library on application startup.
  static {
    OCRPredictorNative.loadLibrary();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    tvOutputResult = (TextView) findViewById(R.id.sample_text);
    ivInputImage = super.findViewById(R.id.ivInputImage);
  }

  // 2.重写onResume方法
  @Override
  protected void onResume() {
    super.onResume();

    boolean settingsChanged = false;
    //这里要改成自己的初始图片路径
    String image_path = "images/1.jpg";
    settingsChanged |= !image_path.equalsIgnoreCase(imagePath);
    if (settingsChanged) {
      imagePath = image_path;
      loadModel();
    }
  }


  // 3.重写onActivityResult()方法
  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode == RESULT_OK) {
      if (currentPhotoPath != null) {
        ExifInterface exif = null;
        try {
          exif = new ExifInterface(currentPhotoPath);
        } catch (IOException e) {
          e.printStackTrace();
        }
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
          ExifInterface.ORIENTATION_UNDEFINED);
        Log.i(TAG, "rotation " + orientation);
        Bitmap image = BitmapFactory.decodeFile(currentPhotoPath);
        image = PaddleOcrUtils.rotateBitmap(image, orientation);
        onImageChanged(image);
      } else {
        Log.e(TAG, "currentPhotoPath is null");
      }
    }
  }


  // 4.重写onRequestPermissionsResult()方法
  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (grantResults[0] != PackageManager.PERMISSION_GRANTED || grantResults[1] != PackageManager.PERMISSION_GRANTED) {
      Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
    }
  }


  // 5.重写onDestroy()方法
  @Override
  protected void onDestroy() {
    if (predictor != null) {
      predictor.releaseModel();
    }
    super.onDestroy();
  }


  // 5.拍照方法（在xml文件里用对应拍照按钮的onclick绑定）
  // ！！其中“MainActivity”改成自己的Activity名，“com.example.myapplication”改成自己的包名
  public void takePhoto(View v) {
    if (requestAllPermissions()) {
      Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
      if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
        File photoFile = null;
        try {
          photoFile = createImageFile();
        } catch (IOException ex) {
          Toast.makeText(MainActivity.this,
            "Create Camera temp file failed: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
        }
        if (photoFile != null) {
          //@see AndroidManifest.xml
          Uri photoURI = FileProvider.getUriForFile(this, appFileprovider, photoFile);

          currentPhotoPath = photoFile.getAbsolutePath();
          takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
          startActivityForResult(takePictureIntent, TAKE_PHOTO_REQUEST_CODE);
        }
      }
    }
  }

  // 6.判断是否调用权限
  private boolean requestAllPermissions() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
      != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this,
      Manifest.permission.CAMERA)
      != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
          Manifest.permission.CAMERA},
        0);
      return false;
    }
    return true;
  }

  // 7.模型相关
  public void loadModel() {
    if (onLoadModel()) {
      onLoadModelSuccessed();
    }
    return;
  }

  public boolean onLoadModel() {
    // 初始化predictor
    // 这里“MainActivity”改成自己的Activity
    return predictor.init(MainActivity.this);
  }

  public void onLoadModelSuccessed() {
    try {
      if (imagePath.isEmpty()) {
        return;
      }
      Bitmap image = null;
      if (!imagePath.substring(0, 1).equals("/")) {
        InputStream imageStream = getAssets().open(imagePath);
        image = BitmapFactory.decodeStream(imageStream);
      } else {
        if (!new File(imagePath).exists()) {
          return;
        }
        image = BitmapFactory.decodeFile(imagePath);
      }
      if (image != null && predictor.isLoaded()) {
        // 输入识别图片
        predictor.setInputImage(image);
        runModel();
      }
    } catch (IOException e) {
      // !!"MainActivity"改成自己的Activity
      Toast.makeText(MainActivity.this, "Load image failed!", Toast.LENGTH_SHORT).show();
      e.printStackTrace();
    }
  }

  public void runModel() {
    if (onRunModel()) {
      onRunModelSuccessed();
    }
  }


  public boolean onRunModel() {
    return predictor.isLoaded() && predictor.runModel();
  }

  // 这里拿到识别结果！！
  public void onRunModelSuccessed() {
    //！！这里拿到识别结果outputImage 类型为Bitmap
    Bitmap outputImage = predictor.outputImage();
    if (outputImage != null) {
      ivInputImage.setImageBitmap(outputImage);
    }
    //这里拿到predictor.outputResult()是识别结果 类型为String
    //格式为  1：识别结果1/n
    //       2：识别结果2/n
    //       3：识别结果3/n
    String result = predictor.outputResult();
    //输出异常堆栈,获取程序调用路径
    tvOutputResult.setText(result);
    tvOutputResult.scrollTo(0, 0);
    //throw new RuntimeException(result);
  }

  // 8.图片相关
  public void onImageChanged(Bitmap image) {
    if (image != null && predictor.isLoaded()) {
      predictor.setInputImage(image);
      runModel();
    }
  }

  private File createImageFile() throws IOException {
    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    String imageFileName = "JPEG_" + timeStamp + "_";
    File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
    File image = File.createTempFile(imageFileName, ".bmp", storageDir);
    return image;
  }
}