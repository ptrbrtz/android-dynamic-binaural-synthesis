/*
Copyright (c) 2016 Peter Bartz

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package net.ptrbrtz.adbs.android.utils;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;

public class DialogHelper {
	public static AlertDialog showOneButtonDialog(Context context, String title, String message, String okButtonText) {
		return showOneButtonDialog(context, title, message, okButtonText, null);
	}

	public static AlertDialog showOneButtonDialog(Context context, String title, String message, String okButtonText, DialogInterface.OnClickListener okButtonClickListener) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(title).setMessage(message).setCancelable(false).setPositiveButton(okButtonText, okButtonClickListener);
		return builder.show();
	}

	public static AlertDialog showTwoButtonDialog(Context context, String title, String message, String yesButtonText, DialogInterface.OnClickListener yesButtonClickListener, 
			String noButtonText, DialogInterface.OnClickListener noButtonClickListener) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(title).setMessage(message).setCancelable(false).setPositiveButton(yesButtonText, yesButtonClickListener).setNegativeButton(noButtonText, noButtonClickListener);
		return builder.show();
	}

	public static AlertDialog showThreeButtonDialog(Context context, String title, String message, String yesButtonText, DialogInterface.OnClickListener yesButtonClickListener, 
			String noButtonText, DialogInterface.OnClickListener noButtonClickListener, String cancelButtonText, DialogInterface.OnClickListener cancelButtonClickListener) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(title).setMessage(message).setCancelable(false).setPositiveButton(yesButtonText, yesButtonClickListener).setNegativeButton(noButtonText, noButtonClickListener).
			setNeutralButton(cancelButtonText, cancelButtonClickListener);
		return builder.show();
	}

	public static ProgressDialog showProgressBarDialog(Context context, String title, String message, int maxProgress) {
		ProgressDialog progressDialog = new ProgressDialog(context);
		progressDialog.setTitle(title);
		progressDialog.setMessage(message);
		progressDialog.setCancelable(false);
		progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		progressDialog.setMax(maxProgress);
		progressDialog.show();
		return progressDialog;
	}
	
	public static ProgressDialog showProgressDialog(Context context, String title, String message) {
		ProgressDialog progressDialog = new ProgressDialog(context);
		progressDialog.setTitle(title);
		progressDialog.setMessage(message);
		progressDialog.setCancelable(false);
		progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		progressDialog.show();
		return progressDialog;
	}
	
	public static ProgressDialog showProgressDialogWithButton(Context context, String title, String message, int whichButton, String buttonText, DialogInterface.OnClickListener onClickListener) {
		ProgressDialog progressDialog = new ProgressDialog(context);
		progressDialog.setTitle(title);
		progressDialog.setMessage(message);
		progressDialog.setCancelable(false);
		progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		progressDialog.setButton(whichButton, buttonText, onClickListener);
		progressDialog.show();
		return progressDialog;
	}
}
