package org.beiwe.app.ui.user;

import org.beiwe.app.R;
import org.beiwe.app.session.SessionActivity;
import org.beiwe.app.storage.PersistentData;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/**The about page!
 * @author Everyone! */
public class AboutActivityLoggedIn extends SessionActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about);
		
		findViewById(R.id.resetAPP).setVisibility(View.INVISIBLE);
		((TextView)findViewById(R.id.about_page_body)).setText(PersistentData.getAboutPageText());
	}
	public void onClickText(View view){}
}
