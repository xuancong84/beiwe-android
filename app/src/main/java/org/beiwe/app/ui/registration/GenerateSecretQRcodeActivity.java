package org.beiwe.app.ui.registration;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.gson.Gson;

import org.beiwe.app.R;
import org.beiwe.app.RunningBackgroundServiceActivity;
import org.beiwe.app.storage.PersistentData;
import org.beiwe.app.storage.TextFileManager;
import org.beiwe.app.ui.makeQR.BarcodeFormat;
import org.beiwe.app.ui.makeQR.BitMatrix;
import org.beiwe.app.ui.makeQR.EncodeHintType;
import org.beiwe.app.ui.makeQR.ErrorCorrectionLevel;
import org.beiwe.app.ui.makeQR.QRCodeWriter;
import org.beiwe.app.ui.makeQR.WriterException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Activity used to show the secrets QR code to the user stored in the application.
 * This secret is useful for porting the application from one phone to another.
 * This screen should be shown only ONCE after the user logged in and data is saved on the phone.
 */

public class GenerateSecretQRcodeActivity extends RunningBackgroundServiceActivity {

	/** Users will go to this activity after registration */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_generate_secrets_qr_code);

		ImageView qrCode = findViewById(R.id.qrCodeImageView);
		TextView userId = findViewById(R.id.userId);
		Context context = getApplicationContext();

		Secrets secrets = new Secrets(
				PersistentData.getPatientID(),
				PersistentData.getHashSalt(),
				PersistentData.getHashIterations(),
				PersistentData.getLatitudeOffset(),
				PersistentData.getLongitudeOffset(),
				PersistentData.getServerUrl()
		);
		userId.setText(PersistentData.getPatientID());

		Bitmap bitmap = generateQRbitmap(new Gson().toJson(secrets),
				(int) (context.getResources().getDisplayMetrics().widthPixels / 1.3),
				(int) (context.getResources().getDisplayMetrics().heightPixels / 2.4),
				2,
				ErrorCorrectionLevel.Q
				);

		qrCode.setImageBitmap(bitmap);
	}

	/**
	 * Generate the qr code with giving the properties.
	 * @return the qr code image.
	 */
	public Bitmap generateQRbitmap(String mContent, int mWidth, int mHeight, int mMargin, ErrorCorrectionLevel mErrorCorrectionLevel) {
		Map<EncodeHintType, Object> hintsMap = new HashMap<>();
		hintsMap.put(EncodeHintType.CHARACTER_SET, "utf-8");
		hintsMap.put(EncodeHintType.ERROR_CORRECTION, mErrorCorrectionLevel);
		hintsMap.put(EncodeHintType.MARGIN, mMargin);
		try {
			BitMatrix bitMatrix = new QRCodeWriter().encode(mContent, BarcodeFormat.QR_CODE,
					mWidth, mHeight, hintsMap);
			int[] pixels = new int[mWidth * mHeight];
			for (int i = 0; i < mHeight; i++) {
				for (int j = 0; j < mWidth; j++) {
					pixels[i * mWidth + j] = bitMatrix.get(j, i) ? Color.BLACK : Color.WHITE;
				}
			}
			return Bitmap.createBitmap(pixels, mWidth, mHeight, Bitmap.Config.ARGB_8888);
		} catch (WriterException e) {
			TextFileManager.getDebugLogFile().writeEncrypted(
					"QRCodeHelper::generate: " + Arrays.toString(e.getStackTrace()));
		}
		return null;
	}

	public void proceedToNextScreen(View view) {
		Intent intent = new Intent(
				GenerateSecretQRcodeActivity.this,
				PhoneNumberEntryActivity.class);
		startActivity(intent);
	}
}

class Secrets {
	private String uid;
	public String hashKey;
	public String hashIteration;
	public String latitudeOffset;
	public String longitudeOffset;
	public String url;

	public Secrets(
			String id,
			byte[] key,
			int iteration,
			double latitude,
			double longitude,
			String serverUrl) {
		uid = id;
		hashKey = toBase64String(key);
		hashIteration = String.valueOf(iteration);
		latitudeOffset = String.valueOf(latitude);
		longitudeOffset = String.valueOf(longitude);
		url = serverUrl;
	}

	private static String toBase64String(byte[] hashKey) {
		return Base64.encodeToString(hashKey, Base64.NO_WRAP | Base64.URL_SAFE );
	}

	public static byte[] hashKeyBase64StringToByteArray(String key) {
		return Base64.decode(key, Base64.NO_WRAP | Base64.URL_SAFE );
	}

	@Override
	public String toString() {
		return "****************************************" +
				"\nUser Id: " + this.uid +
				"\nHash Key: " + this.hashKey +
				"\nHash Iteration: " + this.hashIteration +
				"\nLatitude Offset: " + this.latitudeOffset +
				"\nLongitude Offset: " + this.longitudeOffset +
				"\nServer Url: " + this.url +
				"\n****************************************";
	}
}
