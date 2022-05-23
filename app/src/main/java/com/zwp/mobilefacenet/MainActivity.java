package com.zwp.mobilefacenet;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;

import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.DexterError;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.PermissionRequestErrorListener;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;
import com.zwp.mobilefacenet.faceantispoofing.FaceAntiSpoofing;
import com.zwp.mobilefacenet.mobilefacenet.MobileFaceNet;
import com.zwp.mobilefacenet.mtcnn.Align;
import com.zwp.mobilefacenet.mtcnn.Box;
import com.zwp.mobilefacenet.mtcnn.MTCNN;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_IMAGE = 1;
    private MTCNN mtcnn; // 人脸检测
    private FaceAntiSpoofing fas; // 活体检测
    private MobileFaceNet mfn; // 人脸比对

    public static Bitmap bitmap1;
    public static Bitmap bitmap2;
    private Bitmap bitmapCrop1;
    private Bitmap bitmapCrop2;

    private ImageButton imageButton1;
    private ImageButton imageButton2;
    private ImageView imageViewCrop1;
    private ImageView imageViewCrop2;
    private TextView resultTextView;
    private TextView resultTextView2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageButton1 = findViewById(R.id.image_button1);
        imageButton2 = findViewById(R.id.image_button2);
        imageViewCrop1 = findViewById(R.id.image_view_crop1);
        imageViewCrop2 = findViewById(R.id.image_view_crop2);
        Button cropBtn = findViewById(R.id.crop_btn);
        Button deSpoofingBtn = findViewById(R.id.de_spoofing_btn);
        Button compareBtn = findViewById(R.id.compare_btn);
        resultTextView = findViewById(R.id.result_text_view);
        resultTextView2 = findViewById(R.id.result_text_view2);

        try {
            mtcnn = new MTCNN(getAssets());
            fas = new FaceAntiSpoofing(getAssets());
            mfn = new MobileFaceNet(getAssets());
        } catch (IOException e) {
            e.printStackTrace();
        }

        requestPermission();
        cropBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                faceCrop();
            }
        });
        deSpoofingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                antiSpoofing();
            }
        });
        compareBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                faceCompare();
            }
        });
    }

    private void requestPermission() {
        Dexter.withActivity(this)
                .withPermissions(
                        android.Manifest.permission.CAMERA,

                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                        // check if all permissions are granted
                        if (report.areAllPermissionsGranted()) {
                            initCamera();
                        }

                        // check for permanent denial of any permission
                        if (report.isAnyPermissionPermanentlyDenied()) {
                            Log.e("PERMISSONS_DENIED", report.getDeniedPermissionResponses().get(0).getPermissionName());
                            Toast.makeText(MainActivity.this, "App needs camera permission", Toast.LENGTH_LONG).show();
                            navigateToSettings();
                            // permission is denied permenantly, navigate user to app settings
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                })
                .withErrorListener(new PermissionRequestErrorListener() {

                    @Override
                    public void onError(DexterError error) {
                        Log.e("MAIN_ACTIVITY", error.toString());
                    }
                })
                .onSameThread()
                .check();
    }

    private void navigateToSettings() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(MainActivity.this, "no package", Toast.LENGTH_LONG).show();
        }

    }

    /**
     * 人脸检测并裁减
     */
    private void faceCrop() {
        if (bitmap1 == null || bitmap2 == null) {
            Toast.makeText(this, "Please take two photos", Toast.LENGTH_LONG).show();
            return;
        }

        Bitmap bitmapTemp1 = bitmap1.copy(bitmap1.getConfig(), false);
        Bitmap bitmapTemp2 = bitmap2.copy(bitmap1.getConfig(), false);

        // 检测出人脸数据
        long start = System.currentTimeMillis();
        Vector<Box> boxes1 = mtcnn.detectFaces(bitmapTemp1, bitmapTemp1.getWidth() / 5); // 只有这句代码检测人脸，下面都是根据Box在图片中裁减出人脸
        long end = System.currentTimeMillis();
        resultTextView.setText("Time-consuming forward propagation of face detection：" + (end - start));
        resultTextView2.setText("");
        Vector<Box> boxes2 = mtcnn.detectFaces(bitmapTemp2, bitmapTemp2.getWidth() / 5); // 只有这句代码检测人脸，下面都是根据Box在图片中裁减出人脸
        if (boxes1.size() == 0 || boxes2.size() == 0) {
            Toast.makeText(MainActivity.this, "Face photo not detected", Toast.LENGTH_LONG).show();
            return;
        }

        // 这里因为使用的每张照片里只有一张人脸，所以取第一个值，用来剪裁人脸
        Box box1 = boxes1.get(0);
        Box box2 = boxes2.get(0);

        // 人脸矫正
        bitmapTemp1 = Align.face_align(bitmapTemp1, box1.landmark);
        bitmapTemp2 = Align.face_align(bitmapTemp2, box2.landmark);
        boxes1 = mtcnn.detectFaces(bitmapTemp1, bitmapTemp1.getWidth() / 5);
        boxes2 = mtcnn.detectFaces(bitmapTemp2, bitmapTemp2.getWidth() / 5);
        box1 = boxes1.get(0);
        box2 = boxes2.get(0);

        box1.toSquareShape();
        box2.toSquareShape();
        box1.limitSquare(bitmapTemp1.getWidth(), bitmapTemp1.getHeight());
        box2.limitSquare(bitmapTemp2.getWidth(), bitmapTemp2.getHeight());
        Rect rect1 = box1.transform2Rect();
        Rect rect2 = box2.transform2Rect();

        // 剪裁人脸
        bitmapCrop1 = MyUtil.crop(bitmapTemp1, rect1);
        bitmapCrop2 = MyUtil.crop(bitmapTemp2, rect2);

        // 绘制人脸框和五点
//        Utils.drawBox(bitmapTemp1, box1, 10);
//        Utils.drawBox(bitmapTemp2, box2, 10);

//        bitmapCrop1 = MyUtil.readFromAssets(this, "42.png");
//        bitmapCrop2 = MyUtil.readFromAssets(this, "52.png");
        imageViewCrop1.setImageBitmap(bitmapCrop1);
        imageViewCrop2.setImageBitmap(bitmapCrop2);
    }

    /**
     * 活体检测
     */
    private void antiSpoofing() {
        if (bitmapCrop1 == null || bitmapCrop2 == null) {
            Toast.makeText(this, "Please detect faces first", Toast.LENGTH_LONG).show();
            return;
        }

        // 活体检测前先判断图片清晰度
        int laplace1 = fas.laplacian(bitmapCrop1);

        String text = "Sharpness test result left：" + laplace1;
        if (laplace1 < FaceAntiSpoofing.LAPLACIAN_THRESHOLD) {
            text = text + "，" + "False";
            resultTextView.setTextColor(getResources().getColor(android.R.color.holo_red_light));
        } else {
            long start = System.currentTimeMillis();

            // 活体检测
            float score1 = fas.antiSpoofing(bitmapCrop1);

            long end = System.currentTimeMillis();

            text = "Liveness test results left：" + score1;
            if (score1 < FaceAntiSpoofing.THRESHOLD) {
                text = text + "，" + "True";
                resultTextView.setTextColor(getResources().getColor(android.R.color.holo_green_light));
            } else {
                text = text + "，" + "False";
                resultTextView.setTextColor(getResources().getColor(android.R.color.holo_red_light));
            }
            text = text + "。time consuming" + (end - start);
        }
        resultTextView.setText(text);

        // 第二张图片活体检测前先判断图片清晰度
        int laplace2 = fas.laplacian(bitmapCrop2);

        String text2 = "Sharpness test result left：" + laplace2;
        if (laplace2 < FaceAntiSpoofing.LAPLACIAN_THRESHOLD) {
            text2 = text2 + "，" + "False";
            resultTextView2.setTextColor(getResources().getColor(android.R.color.holo_red_light));
        } else {
            // 活体检测
            float score2 = fas.antiSpoofing(bitmapCrop2);
            text2 = "Liveness Test Results right：" + score2;
            if (score2 < FaceAntiSpoofing.THRESHOLD) {
                text2 = text2 + "，" + "True";
                resultTextView2.setTextColor(getResources().getColor(android.R.color.holo_green_light));
            } else {
                text2 = text2 + "，" + "False";
                resultTextView2.setTextColor(getResources().getColor(android.R.color.holo_red_light));
            }
        }
        resultTextView2.setText(text2);
    }

    /**
     * 人脸比对
     */
    private void faceCompare() {
        if (bitmapCrop1 == null || bitmapCrop2 == null) {
            Toast.makeText(this, "Please detect faces first", Toast.LENGTH_LONG).show();
            return;
        }

        long start = System.currentTimeMillis();
        float same = mfn.compare(bitmapCrop1, bitmapCrop2); // 就这一句有用代码，其他都是UI
        long end = System.currentTimeMillis();

        String text = "face comparison result：" + same;
        if (same > MobileFaceNet.THRESHOLD) {
            text = text + "，" + "True";
            resultTextView.setTextColor(getResources().getColor(android.R.color.holo_green_light));
        } else {
            text = text + "，" + "False";
            resultTextView.setTextColor(getResources().getColor(android.R.color.holo_red_light));
        }
        text = text + "，time consuming" + (end - start);
        resultTextView.setText(text);
        resultTextView2.setText("");
    }

    /*********************************** 以下是相机部分 ***********************************/
    public static ImageButton currentBtn;

    private void initCamera() {
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu(v
                );
            }
        };
        imageButton1.setOnClickListener(listener);
        imageButton2.setOnClickListener(listener);
    }

    private void showMenu(final View v) {
        PopupMenu popup = new PopupMenu(MainActivity.this, v);
        //Inflating the Popup using xml file
        popup.getMenuInflater().inflate(R.menu.menu, popup.getMenu());
        currentBtn = (ImageButton) v;

        //registering popup with OnMenuItemClickListener
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.camera:
                        startActivity(new Intent(MainActivity.this, CameraActivity.class));
                        return true;
                    case R.id.gallery:
                        Intent intent = new Intent();
                        intent.setType("image/*");
                        intent.setAction(Intent.ACTION_GET_CONTENT);
                        startActivityForResult(Intent.createChooser(intent, "Select image"), PICK_IMAGE);
                        return true;
                    default:
                        return false;
                }
            }
        });

        popup.show();//showing popup menu
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK) {
            try {
               // assert data != null;

                if (data.getData() != null) {

                    Uri imageUri = data.getData();

                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                  currentBtn.setImageBitmap(bitmap);
                    if (currentBtn.getId() == R.id.image_button1) {
                        Log.e("MAIN_ACTIVITY", String.valueOf(bitmap));

                        bitmap1 = bitmap;
                    } else if (currentBtn.getId() == R.id.image_button2) {
                        bitmap2 = bitmap;
                    }
                }



            } catch (IOException e) {
                Log.e("TAG", e.getMessage());
                e.printStackTrace();
            }


        }
        //Log.e("MAIN_ACTIVITY", String.valueOf(requestCode));
    }
}
