package org.beiwe.app.ui.user;

import org.beiwe.app.R;
import org.beiwe.app.RunningBackgroundServiceActivity;
import org.beiwe.app.session.SessionActivity;
import org.beiwe.app.storage.PersistentData;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**The about page!
 * @author Everyone! */
public class AboutActivityLoggedIn extends SessionActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about);
		RunningBackgroundServiceActivity.nNeedToClick = 10;
		((TextView)findViewById(R.id.about_page_body)).setText(PersistentData.getAboutPageText());
	}
}
