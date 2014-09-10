package org.linphone;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.linphone.LinphoneSimpleListener.LinphoneOnComposingReceivedListener;
import org.linphone.LinphoneSimpleListener.LinphoneOnFileTransferListener;
import org.linphone.LinphoneSimpleListener.LinphoneOnMessageReceivedListener;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatMessage.State;
import org.linphone.core.LinphoneChatMessage.StateListener;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneContent;
import org.linphone.core.LinphoneContentImpl;
import org.linphone.core.LinphoneCore;
import org.linphone.mediastream.Log;
import org.linphone.ui.AvatarWithShadow;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.content.CursorLoader;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class ChatFragment extends Fragment 
implements OnClickListener, LinphoneOnComposingReceivedListener, LinphoneOnMessageReceivedListener, LinphoneOnFileTransferListener, StateListener {
	private static final int ADD_PHOTO = 1337;
	private static final int MENU_DELETE_MESSAGE = 0;
	private static final int MENU_SAVE_PICTURE = 1;
	private static final int MENU_PICTURE_SMALL = 2;
	private static final int MENU_PICTURE_MEDIUM = 3;
	private static final int MENU_PICTURE_LARGE = 4;
	private static final int MENU_PICTURE_REAL = 5;
	private static final int MENU_COPY_TEXT = 6;
	private static final int MENU_RESEND_MESSAGE = 7;
	private static final int SIZE_SMALL = 500;
	private static final int SIZE_MEDIUM = 1000;
	private static final int SIZE_LARGE = 1500;
	
	private String sipUri;
	private boolean isDownloading, isUploading;
	private ByteArrayOutputStream downloadData;
	private ByteArrayInputStream uploadData;
	private LinphoneChatMessage currentFileTransferMessage;
	
	private LinphoneChatRoom chatRoom;
	private Uri imageToUploadUri;
	private Bitmap imageToUpload;
	
	private EditText message;
	private ImageView cancelUpload;
	private TextView sendImage, sendMessage, contactName, remoteComposing;
	private AvatarWithShadow contactPicture;
	private RelativeLayout uploadLayout, textLayout;
	private ListView messagesList;
	private ProgressBar uplaodProgressBar, downloadProgressBar;
	private TextWatcher textWatcher;
	private Handler mHandler;
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		sipUri = getArguments().getString("SipUri");
		String displayName = getArguments().getString("DisplayName");
		String pictureUri = getArguments().getString("PictureUri");
		
		View view = inflater.inflate(R.layout.chat, container, false);
		
		contactName = (TextView) view.findViewById(R.id.contactName);
        contactPicture = (AvatarWithShadow) view.findViewById(R.id.contactPicture);
        displayChatHeader(displayName, pictureUri);

        sendMessage = (TextView) view.findViewById(R.id.sendMessage);
        sendMessage.setOnClickListener(this);

        remoteComposing = (TextView) view.findViewById(R.id.remoteComposing);
        remoteComposing.setVisibility(View.GONE);
        
        messagesList = (ListView) view.findViewById(R.id.chatMessageList);
        
        message = (EditText) view.findViewById(R.id.message);
        if (!getActivity().getResources().getBoolean(R.bool.allow_chat_multiline)) {
        	message.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE);
        	message.setMaxLines(1);
        }
        
        uploadLayout = (RelativeLayout) view.findViewById(R.id.uploadLayout);
        textLayout = (RelativeLayout) view.findViewById(R.id.messageLayout);
        
        uplaodProgressBar = (ProgressBar) view.findViewById(R.id.progressbar);
        sendImage = (TextView) view.findViewById(R.id.sendPicture);
        if (!getResources().getBoolean(R.bool.disable_chat_send_file)) {
	        registerForContextMenu(sendImage);
	        sendImage.setOnClickListener(this);
        } else {
        	sendImage.setEnabled(false);
        }
  
        cancelUpload = (ImageView) view.findViewById(R.id.cancelUpload);
        cancelUpload.setOnClickListener(this);
		
		textWatcher = new TextWatcher() {
			public void afterTextChanged(Editable arg0) {
			}

			public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
			}

			public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
				if (message.getText().toString().equals("")) {
					sendMessage.setEnabled(false);
				} else {
					if (chatRoom != null)
						chatRoom.compose();
					sendMessage.setEnabled(true);
				}
			}
		};
        
        mHandler = new Handler();
		
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			chatRoom = lc.getOrCreateChatRoom(sipUri);
			chatRoom.markAsRead();
		}
		
		return view;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putString("messageDraft", message.getText().toString());
		super.onSaveInstanceState(outState);
	}
	
	@Override
	public void onPause() {
		message.removeTextChangedListener(textWatcher);
	
		LinphoneService.instance().removeMessageNotification();

		if (LinphoneManager.isInstanciated()) {
			LinphoneManager.getInstance().setOnComposingReceivedListener(null);
			LinphoneManager.getInstance().setOnFileTransferListener(null);
		}

		super.onPause();
		
		onSaveInstanceState(getArguments());
	}
	
	@Override
	public void onResume() {
		message.addTextChangedListener(textWatcher);
		
		if (LinphoneManager.isInstanciated()) {
			LinphoneManager.getInstance().setOnComposingReceivedListener(this);
			LinphoneManager.getInstance().setOnFileTransferListener(this);
		}

		super.onResume();

		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.CHAT);
			LinphoneActivity.instance().updateChatFragment(this);

			if (getResources().getBoolean(R.bool.show_statusbar_only_on_dialer)) {
				LinphoneActivity.instance().hideStatusBar();
			}
		}

		String draft = getArguments().getString("messageDraft");
		message.setText(draft);

		remoteComposing.setVisibility(chatRoom.isRemoteComposing() ? View.VISIBLE : View.GONE);
	}

	@Override
	public void onClick(View v) {
		//TODO
		switch (v.getId()) {
		case R.id.sendMessage:
			break;
		case R.id.sendPicture:
			startImagePicker();
			break;
		case R.id.cancelUpload:
			isUploading = isDownloading = false;
			chatRoom.cancelFileTransfer(currentFileTransferMessage);
			break;
		}
		
	}

	@Override
	public void onComposingReceived(LinphoneChatRoom room) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onFileTransferProgressChanged(final int progress) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				ProgressBar bar = null;
				
				if (isUploading && uplaodProgressBar != null) {
					bar = uplaodProgressBar;
				} else if (isDownloading && downloadProgressBar != null) {
					bar = downloadProgressBar;
				}
				
				if (bar != null) {
					bar.setProgress(progress);
				}
			}
		});
	}

	@Override
	public void onFileDownloadDataReceived(LinphoneChatMessage msg, LinphoneContent content, String data, int size) {
		if (size == 0 && downloadData != null && downloadData.size() == content.getSize()) {
			// Download finished, save the picture
			isDownloading = false;
			ByteArrayInputStream bis = new ByteArrayInputStream(downloadData.toByteArray());
			Bitmap bm = BitmapFactory.decodeStream(bis);
			String localPath = saveImageOnDevice(bm, msg.getStorageId());
			try {
				downloadData.close();
			} catch (IOException e) {
				Log.e(e);
			}
			downloadData = null;
			//TODO
		} else {
			// Append data to previously received data
			if (downloadData != null) {
				try {
					downloadData.write(data.getBytes());
				} catch (IOException e) {
					Log.e(e);
				}
			}
		}
	}

	@Override
	public int onFileUploadDataNeeded(LinphoneChatMessage message, LinphoneContent content, ByteBuffer data, int size) {
		if (uploadData != null && uploadData.available() < size) {
			Log.w("Asking for more bytes than remaining...");
			size = uploadData.available();
		}
		byte[] buffer = new byte[size];
		int bytesWritten = uploadData.read(buffer, 0, size);
		data.put(buffer);
		buffer = null;

		if (uploadData.available() == 0) { // Upload terminate
			try {
				uploadData.close();
			} catch (IOException e) {
				Log.e(e);
			}
			uploadData = null;
			//TODO
		}
		return bytesWritten;
	}

	@Override
	public void onMessageReceived(LinphoneAddress from, LinphoneChatMessage msg, int id) {
		//TODO Auto-generated method stub
	}

	@Override
	public void onLinphoneChatMessageStateChanged(LinphoneChatMessage msg, State state) {
		// TODO Auto-generated method stub
	}

	public String getSipUri() {
		return sipUri;
	}

	private void displayChatHeader(String displayName, String pictureUri) {
		if (displayName == null && getResources().getBoolean(R.bool.only_display_username_if_unknown) && LinphoneUtils.isSipAddress(sipUri)) {
        	contactName.setText(LinphoneUtils.getUsernameFromAddress(sipUri));
		} else if (displayName == null) {
			contactName.setText(sipUri);
		} else {
			contactName.setText(displayName);
		}

        if (pictureUri != null) {
        	LinphoneUtils.setImagePictureFromUri(LinphoneActivity.instance(), contactPicture.getView(), Uri.parse(pictureUri), R.drawable.unknown_small);
        } else {
        	contactPicture.setImageResource(R.drawable.unknown_small);
        }
	}

	public void changeDisplayedChat(String newSipUri, String displayName, String pictureUri) {
		sipUri = newSipUri;
		if (LinphoneActivity.isInstanciated()) {
			String draft = LinphoneActivity.instance().getChatStorage().getDraft(sipUri);
			if (draft == null)
				draft = "";
			message.setText(draft);
		}

		displayChatHeader(displayName, pictureUri);
		//displayMessages();
	}
	
	private void downloadImage(LinphoneChatMessage message) {
		downloadData = new ByteArrayOutputStream();
		isDownloading = true;
		message.startFileDownload(this);
	}
	
	private LinphoneChatMessage createUploadImageMessage(Bitmap bm) {
		if (bm == null) {
			return null;
		}
		
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] data = null;
		
    	bm.compress(CompressFormat.JPEG, 100, bos);
    	data = bos.toByteArray();
    	try {
			bos.close();
		} catch (IOException e) {
			Log.e(e);
		}
		uploadData = new ByteArrayInputStream(data);
        
        LinphoneContent content = new LinphoneContentImpl("cotcot.jpg", "image", "jpeg", data, null);
        return chatRoom.createFileTransferMessage(content);
	}
	
	private void uploadImage(Bitmap bm) {
		LinphoneChatMessage message = createUploadImageMessage(bm);
		if (message != null) {
			chatRoom.sendMessage(message, this);
			isUploading = true;
		}
	}
	
	private String saveImageOnDevice(Bitmap bm, int id) {
		try {
			String path = Environment.getExternalStorageDirectory().toString();
			if (!path.endsWith("/"))
				path += "/";
			path += "Pictures/";
			File directory = new File(path);
			directory.mkdirs();

			String filename = getString(R.string.picture_name_format).replace("%s", String.valueOf(id));
			File file = new File(path, filename);

			OutputStream fOut = null;
			fOut = new FileOutputStream(file);

			bm.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
			fOut.flush();
			fOut.close();

			MediaStore.Images.Media.insertImage(getActivity().getContentResolver(),file.getAbsolutePath(),file.getName(),file.getName());
			return file.getAbsolutePath();
		} catch (Exception e) {
			Log.e(e);
		}
		return null;
	}

	private void startImagePicker() {
	    final List<Intent> cameraIntents = new ArrayList<Intent>();
	    final Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
	    File file = new File(Environment.getExternalStorageDirectory(), getString(R.string.temp_photo_name));
	    imageToUploadUri = Uri.fromFile(file);
    	captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageToUploadUri);
	    cameraIntents.add(captureIntent);

	    final Intent galleryIntent = new Intent();
	    galleryIntent.setType("image/*");
	    galleryIntent.setAction(Intent.ACTION_GET_CONTENT);

	    final Intent chooserIntent = Intent.createChooser(galleryIntent, getString(R.string.image_picker_title));
	    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(new Parcelable[]{}));

	    startActivityForResult(chooserIntent, ADD_PHOTO);
    }

	private void showPopupMenuAskingImageSize(String filePath, Bitmap bm) {
		if (bm == null && filePath != null) {
			bm = BitmapFactory.decodeFile(filePath);
			try {
				ExifInterface exif = new ExifInterface(filePath);
				int pictureOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0);
				Matrix matrix = new Matrix();
				if (pictureOrientation == 6) {
					matrix.postRotate(90);
				} else if (pictureOrientation == 3) {
					matrix.postRotate(180);
				} else if (pictureOrientation == 8) {
					matrix.postRotate(270);
				}
				bm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
			} catch (Exception e) {
				Log.e(e);
			}
		}
		imageToUpload = bm;
		
		try {
			sendImage.showContextMenu();
		} catch (Exception e) { Log.e(e); };
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		if (v.getId() == R.id.sendPicture) {
			menu.add(0, MENU_PICTURE_SMALL, 0, getString(R.string.share_picture_size_small));
			menu.add(0, MENU_PICTURE_MEDIUM, 0, getString(R.string.share_picture_size_medium));
			menu.add(0, MENU_PICTURE_LARGE, 0, getString(R.string.share_picture_size_large));
//			Not a good idea, very big pictures cause Out of Memory exceptions, slow display, ...
//			menu.add(0, MENU_PICTURE_REAL, 0, getString(R.string.share_picture_size_real));
		}
	}
	
	private Bitmap scaleDownBitmap(Bitmap bm, int pixelsMax) {
		if (bm != null) {
            if (bm.getWidth() > bm.getHeight() && bm.getWidth() > pixelsMax) {
            	bm = Bitmap.createScaledBitmap(bm, pixelsMax, (pixelsMax * bm.getHeight()) / bm.getWidth(), false);
            } else if (bm.getHeight() > bm.getWidth() && bm.getHeight() > pixelsMax) {
            	bm = Bitmap.createScaledBitmap(bm, (pixelsMax * bm.getWidth()) / bm.getHeight(), pixelsMax, false);
            }
        }
		return bm;
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		//TODO
		switch (item.getItemId()) {
		case MENU_PICTURE_SMALL:
			imageToUpload = scaleDownBitmap(imageToUpload, SIZE_SMALL);
			break;
		case MENU_PICTURE_MEDIUM:
			imageToUpload = scaleDownBitmap(imageToUpload, SIZE_MEDIUM);
			break;
		case MENU_PICTURE_LARGE:
			imageToUpload = scaleDownBitmap(imageToUpload, SIZE_LARGE);
			break;
		case MENU_PICTURE_REAL:
			break;
		}
		
		uploadImage(imageToUpload);
		return true;
	}

	private String getRealPathFromURI(Uri contentUri) {
		String[] proj = { MediaStore.Images.Media.DATA };
	    CursorLoader loader = new CursorLoader(getActivity(), contentUri, proj, null, null, null);
	    Cursor cursor = loader.loadInBackground();
	    if (cursor != null && cursor.moveToFirst()) {
		    int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
		    String result = cursor.getString(column_index);
		    cursor.close();
		    return result;
	    }
	    return null;
    }
	
	@Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ADD_PHOTO && resultCode == Activity.RESULT_OK) {
        	if (data != null && data.getExtras() != null && data.getExtras().get("data") != null) {
        		Bitmap bm = (Bitmap) data.getExtras().get("data");
        		showPopupMenuAskingImageSize(null, bm);
        	}
        	else if (data != null && data.getData() != null) {
	    		String filePath = getRealPathFromURI(data.getData());
	        	showPopupMenuAskingImageSize(filePath, null);
        	}
        	else if (imageToUploadUri != null) {
        		String filePath = imageToUploadUri.getPath();
        		showPopupMenuAskingImageSize(filePath, null);
        	}
        	else {
        		File file = new File(Environment.getExternalStorageDirectory(), getString(R.string.temp_photo_name));
        		if (file.exists()) {
	        	    imageToUploadUri = Uri.fromFile(file);
	        	    String filePath = imageToUploadUri.getPath();
	        		showPopupMenuAskingImageSize(filePath, null);
        		}
        	}
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
    }
}
