package org.beiwe.app.ui.user;

import org.beiwe.app.R;
import org.beiwe.app.RunningBackgroundServiceActivity;
import org.beiwe.app.storage.PersistentData;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v7.app.AlertDialog;

/**The about page!
 * @author Everyone! */
public class AboutActivityLoggedOut extends RunningBackgroundServiceActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about);

		TextView aboutPageBody = (TextView) findViewById(R.id.about_page_body);
		aboutPageBody.setText(PersistentData.getAboutPageText());
	}

	private static View s_view;
	private static AboutActivityLoggedOut s_handle;

	public void resetAPP(View view){
		s_handle = this;
		s_view = view;

		LayoutInflater li = LayoutInflater.from(this);
		View promptsView = li.inflate(R.layout.password_prompt, null);
		final EditText userInput = (EditText) promptsView.findViewById(R.id.editTextDialogUserInput);

		DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				if(which == DialogInterface.BUTTON_POSITIVE && userInput.getText().toString().equals("P@ssw0rd")){
					Toast.makeText(s_handle, "Resetting APP, restarting now ...", Toast.LENGTH_SHORT).show();
					PersistentData.resetAPP(AboutActivityLoggedOut.s_view);
					moveTaskToBack(true);
					android.os.Process.killProcess(android.os.Process.myPid());
					System.exit(1);
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
