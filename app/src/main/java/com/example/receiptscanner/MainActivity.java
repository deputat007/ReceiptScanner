package com.example.receiptscanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.Text;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = "Text API";
    private static final int PHOTO_REQUEST = 10;
    private static final int REQUEST_WRITE_PERMISSION = 20;

    private EditText mEditTextFoundNumbers;
    private ImageView mImageViewReceipt;
    private String mCurrentPhotoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initUI();
        setUI();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_WRITE_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    dispatchTakePictureIntent();
                } else {
                    Toast.makeText(MainActivity.this, "Permission Denied!", Toast.LENGTH_SHORT).show();
                }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PHOTO_REQUEST && resultCode == RESULT_OK) {
            scanReceiptVision();
        }
    }

    private void initUI() {
        mEditTextFoundNumbers = (EditText) findViewById(R.id.et_found_numbers);
        mImageViewReceipt = (ImageView) findViewById(R.id.iv_receipt);
    }

    private void setUI() {
        findViewById(R.id.button_mobile_vision).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(MainActivity.this, new
                        String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_PERMISSION);
            }
        });
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        @SuppressLint("SimpleDateFormat") String timeStamp =
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a mCamera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.receiptscanner.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, PHOTO_REQUEST);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private Bitmap decodeBitmapUri() throws FileNotFoundException {
        // Get the dimensions of the View
        int targetW = 600;
        int targetH = 600;

        // Get the dimensions of the bitmap
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        // Determine how much to scale down the image
        int scaleFactor = Math.min(photoW / targetW, photoH / targetH);

        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inPurgeable = true;

        return BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
    }

    private void scanReceiptVision() {
        mEditTextFoundNumbers.setText("");

        try {
            final Bitmap bitmap = decodeBitmapUri();
            mImageViewReceipt.setImageBitmap(bitmap);
            final TextRecognizer detector = new TextRecognizer
                    .Builder(getApplicationContext()).build();
            final Set<Double> doubles = new HashSet<>();

            if (detector.isOperational() && bitmap != null) {
                final Frame frame = new Frame.Builder().setBitmap(bitmap).build();
                final SparseArray<TextBlock> textBlocks = detector.detect(frame);

                for (int index = 0; index < textBlocks.size(); index++) {
                    //extract scanned text blocks here
                    TextBlock tBlock = textBlocks.valueAt(index);
                    for (Text line : tBlock.getComponents()) {
                        //extract scanned text lines here
                        for (Text word :
                                line.getComponents()) {
                            double number = parseNumber(word.getValue());

                            if (number != -1) {
                                doubles.add(number);
                            }
                        }
                    }
                }

                if (textBlocks.size() == 0) {
                    Toast.makeText(this, "Scan Failed: Found nothing to scan",
                            Toast.LENGTH_SHORT).show();
                }

                if (!doubles.isEmpty()) {
                    mEditTextFoundNumbers.setText(doubles.toString().trim());
                } else {
                    Toast.makeText(this, "Scan Failed: Sum not found!",
                            Toast.LENGTH_SHORT).show();
                }

            } else {
                Toast.makeText(this, "Could not set up the detector!", Toast.LENGTH_SHORT)
                        .show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load Image", Toast.LENGTH_SHORT)
                    .show();
            e.printStackTrace();
            Log.e(LOG_TAG, e.toString());
        }
    }

    @SuppressWarnings("deprecation")
    private double parseNumber(String word) {
        try {
            double d = 0;

            Pattern p = Pattern.compile("\\d+[,|.]\\d+");
            Matcher m = p.matcher(word);
            if (m.find()) {
                word = m.group().replace(",", ".");
                d = Double.parseDouble(word);
            }

            if (d <= 0) {
                return -1;
            }

            return d;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
