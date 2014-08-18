package org.beiwe.app.survey;

import org.beiwe.app.R;
import org.beiwe.app.storage.TextFileManager;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

public class SurveyTimingsRecorder {
	
	public static String header = "timestamp,question id,question type,question text,question answer options,answer";
		
	
	/**
	 * Create a new Survey Response file, and record the timestamp of when 
	 * the survey first displayed to the user
	 */
	public static void recordSurveyFirstDisplayed() {
		// Create a new data file to record answers for only this survey
		// TODO: decide if it's possible for a user to take two surveys at once, and if that's a problem
		TextFileManager.getSurveyTimingsFile().newFile();
		
		String message = "Survey first rendered and displayed to user";
		appendLineToLogFile(message);
	}
	
	
	/**
	 * Record (in the Survey Response file) the answer to a single survey question
	 * @param answer the user's input answer
	 * @param questionDescription the question that was answered
	 */
	public static void recordAnswer(String answer, QuestionDescription questionDescription) {
		String message = "";
		message += sanitizeString(questionDescription.getId()) + TextFileManager.DELIMITER;
		message += sanitizeString(questionDescription.getType()) + TextFileManager.DELIMITER;
		message += sanitizeString(questionDescription.getText()) + TextFileManager.DELIMITER;
		message += sanitizeString(questionDescription.getOptions()) + TextFileManager.DELIMITER;
		message += sanitizeString(answer);
		Log.i("SurveyTimingsRecorder", message);
		
		appendLineToLogFile(message);
	}

	
	/**
	 * Record (in the Survey Response file) that the user pressed the "Submit" 
	 * button at the bottom of a survey
	 * @param appContext
	 */
	public static void recordSubmit(Context appContext) {
		String message = "User hit submit";
		appendLineToLogFile(message);		
	}
	
	
	/**
	 * Write a line to the bottom of the Survey Response file
	 * @param message
	 */
	private static void appendLineToLogFile(String message) {
		/** Handles the logging, includes a new line for the CSV files.
		 * This code is otherwised reused everywhere.*/
		Long javaTimeCode = System.currentTimeMillis();
		String line = javaTimeCode.toString() + TextFileManager.DELIMITER + message; 

		Log.i("SurveyTimingsRecorder", line);
		TextFileManager.getSurveyTimingsFile().write(line);
	}

	
	/**
	 * Sanitize a string for use in a Tab-Separated Values file
	 * @param input string to be sanitized
	 * @return String with tabs and newlines removed
	 */
	public static String sanitizeString(String input) {
		// TODO: fix RegEx so it sanitizes '\t'
		input = input.replaceAll("[\t\n\r]", "  ");
		// Replace all commas in the text with semicolons, because commas are the delimiters
		input = input.replaceAll(",", ";");
		return input;
	}

}
