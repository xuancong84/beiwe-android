package org.beiwe.app.ui.user;

import org.beiwe.app.R;
import org.beiwe.app.RunningBackgroundServiceActivity;
import org.beiwe.app.storage.PersistentData;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.support.v7.app.AlertDialog;

/**The about page!
 * @author Everyone! */
public class AboutActivityLoggedOut extends RunningBackgroundServiceActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about);

		findViewById(R.id.resetAPP).setVisibility(View.INVISIBLE);
		((TextView)findViewById(R.id.about_page_body)).setText(PersistentData.getAboutPageText());
	}

	private static int nNeedToClick = 10;
	public void onClickText(View view){
		if(--nNeedToClick==0)
			findViewById(R.id.resetAPP).setVisibility(View.VISIBLE);
	}

	private static View s_view;
	public void resetAPP(View view){
		s_view = view;

		LayoutInflater li = LayoutInflater.from(this);
		View promptsView = li.inflate(R.layout.password_prompt, null);
		final EditText userInput = promptsView.findViewById(R.id.editTextDialogUserInput);

		DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				if(which == DialogInterface.BUTTON_POSITIVE && userInput.getText().toString().equals("P@ssw0rd")){
					PersistentData.resetAPP(AboutActivityLoggedOut.s_view);
					moveTaskToBack(true);
					android.os.Process.killProcess(android.os.Process.myPid());
					System.exit(0);
				}
			}
		};

		new AlertDialog.Builder(this)
				.setView(promptsView)
				.setTitle("Warning")
				.setMessage("This will unregister any study and reset the APP. Are you sure?")
				.setPositiveButton("OK", dialogClickListener)
				.setNegativeButton("Cancel", dialogClickListener).show();
	}
}
