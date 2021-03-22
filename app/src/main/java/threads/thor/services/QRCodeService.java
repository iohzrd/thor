package threads.thor.services;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import threads.LogUtils;
import threads.thor.BuildConfig;


public class QRCodeService {
    private static final String TAG = QRCodeService.class.getSimpleName();
    private static final int QR_CODE_SIZE = 250;

    private static Bitmap getBitmap(@NonNull String hash) {
        MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
        try {
            BitMatrix bitMatrix = multiFormatWriter.encode(hash,
                    BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE);
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            return barcodeEncoder.createBitmap(bitMatrix);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Uri getImage(@NonNull Context context, @NonNull String hash) {


        Bitmap bitmap = getBitmap(hash);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] bytes = stream.toByteArray();
        bitmap.recycle();

        threads.thor.provider.FileProvider fileProvider = threads.thor.provider.FileProvider.getInstance(context);


        File newFile = new File(fileProvider.getImageDir(), "img" + hash.hashCode() + ".png");

        try {
            if (!newFile.exists()) {
                try (FileOutputStream fos = new FileOutputStream(newFile)) {
                    fos.write(bytes);
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

        return FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID, newFile);
    }
}

