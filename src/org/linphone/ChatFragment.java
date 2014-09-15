package org.linphone;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.linphone.LinphoneSimpleListener.LinphoneOnComposingReceivedListener;
import org.linphone.LinphoneSimpleListener.LinphoneOnFileTransferListener;
import org.linphone.LinphoneSimpleListener.LinphoneOnMessageReceivedListener;
import org.linphone.compatibility.Compatibility;
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
import org.linphone.ui.BubbleChat;
import org.linphone.ui.BubbleChat.BubbleChatActionListener;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
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
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class ChatFragment extends Fragment 
implements OnClickListener, LinphoneOnComposingReceivedListener, LinphoneOnMessageReceivedListener, LinphoneOnFileTransferListener, StateListener {
	private static final int ADD_PHOTO = 1337;
	private static final int MENU_DELETE_MESSAGE = 0;
	private static final int MENU_PICTURE_SMALL = 2;
	private static final int MENU_PICTURE_MEDIUM = 3;
	private static final int MENU_PICTURE_LARGE = 4;
	private static final int MENU_PICTURE_REAL = 5;
	private static final int MENU_COPY_TEXT = 6;
	private static final int SIZE_SMALL = 500;
	private static final int SIZE_MEDIUM = 1000;
	private static final int SIZE_LARGE = 1500;
	
	private String sipUri;
	private boolean isDownloading, isUploading;
	private LinphoneChatMessage currentFileTransferMessage;
	private String imageAlreadyHasAnId;
	private ChatMessageAdapter adapter;
	
	private LinphoneChatRoom chatRoom;
	private Uri imageToUploadUri;
	private Bitmap imageToUpload;
	private OnGlobalLayoutListener keyboardListener;
	
	private View view;
	private EditText message;
	private ImageView cancelUpload;
	private TextView sendImage, sendMessage, contactName, remoteComposing;
	private AvatarWithShadow contactPicture;
	private RelativeLayout uploadLayout, textLayout;
	private ListView messagesList;
	private ProgressBar uploadProgressBar;
	private TextWatcher textWatcher;
	private Handler mHandler;
	private TextView progressBarText;
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		sipUri = getArguments().getString("SipUri");
		String displayName = getArguments().getString("DisplayName");
		String pictureUri = getArguments().getString("PictureUri");
		
		view = inflater.inflate(R.layout.chat, container, false);
		
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
        
        uploadProgressBar = (ProgressBar) view.findViewById(R.id.progressbar);
        progressBarText = (TextView) view.findViewById(R.id.progressBarText);
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
		}

		// Force hide keyboard
		if (LinphoneActivity.isInstanciated()) {
			InputMethodManager imm = (InputMethodManager) LinphoneActivity.instance().getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(view.getWindowToken(),0);
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
		removeVirtualKeyboardVisiblityListener();

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
		addVirtualKeyboardVisiblityListener();

		super.onResume();

		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.CHAT);
			LinphoneActivity.instance().updateChatFragment(this);

			if (getResources().getBoolean(R.bool.show_statusbar_only_on_dialer)) {
				LinphoneActivity.instance().hideStatusBar();
			}
		}
		
		if (isUploading || isDownloading) {
			int progress = LinphoneManager.getInstance().getLastFileTransferProgress();
			uploadProgressBar.setProgress(progress);
			
			if (progress == 100) {
				isUploading = isDownloading = false;
				uploadLayout.setVisibility(View.GONE);
				textLayout.setVisibility(View.VISIBLE);
			}
		}

		String draft = getArguments().getString("messageDraft");
		message.setText(draft);

		remoteComposing.setVisibility(chatRoom.isRemoteComposing() ? View.VISIBLE : View.GONE);
		displayMessages();
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.sendMessage:
			sendMessage(message.getText().toString());
			message.setText("");
			break;
		case R.id.sendPicture:
			startImagePicker();
			break;
		case R.id.cancelUpload:
			isUploading = isDownloading = false;
			chatRoom.cancelFileTransfer(currentFileTransferMessage);
			uploadLayout.setVisibility(View.GONE);
			textLayout.setVisibility(View.VISIBLE);
			break;
		}
	}

	@Override
	public void onComposingReceived(LinphoneChatRoom room) {
		if (chatRoom != null && room != null && chatRoom.getPeerAddress().asStringUriOnly().equals(room.getPeerAddress().asStringUriOnly())) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					remoteComposing.setVisibility(chatRoom.isRemoteComposing() ? View.VISIBLE : View.GONE);
				}
			});
		}
	}

	@Override
	public void onFileTransferProgressChanged(final int progress) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				uploadProgressBar.setProgress(progress);
				if (progress == 100) {
					uploadLayout.setVisibility(View.GONE);
					textLayout.setVisibility(View.VISIBLE);
				}
			}
		});
	}

	@Override
	public void onFileDownloadFinished(LinphoneChatMessage msg, LinphoneContent content) {
		isDownloading = false;
		
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				if (adapter != null) {
					adapter.refreshHistory();
					adapter.notifyDataSetChanged();
					scrollToEnd();
				}
			}
		});
	}

	@Override
	public void onFileUploadFinished(LinphoneChatMessage message, LinphoneContent content) {
		isUploading = false;
	}

	@Override
	public void onMessageReceived(LinphoneAddress from, final LinphoneChatMessage msg) {
		if (from.asStringUriOnly().equals(sipUri)) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					adapter.refreshHistory();
					adapter.notifyDataSetChanged();
					
					scrollToEnd();
					chatRoom.markAsRead();
					LinphoneActivity.instance().updateMissedChatCount();
				}
			});
		}
	}

	@Override
	public void onLinphoneChatMessageStateChanged(LinphoneChatMessage msg, State state) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				adapter.refreshHistory();
				adapter.notifyDataSetChanged();
			}
		});
	}

	private void addVirtualKeyboardVisiblityListener() {
		keyboardListener = new OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
			    Rect visibleArea = new Rect();
			    view.getWindowVisibleDisplayFrame(visibleArea);

			    int heightDiff = view.getRootView().getHeight() - (visibleArea.bottom - visibleArea.top);
			    if (heightDiff > 200) {
			    	showKeyboardVisibleMode();
			    } else {
			    	hideKeyboardVisibleMode();
			    }
			}
		};
		view.getViewTreeObserver().addOnGlobalLayoutListener(keyboardListener);
	}

	private void removeVirtualKeyboardVisiblityListener() {
		Compatibility.removeGlobalLayoutListener(view.getViewTreeObserver(), keyboardListener);
	}

	public void showKeyboardVisibleMode() {
		LinphoneActivity.instance().hideMenu(true);
		contactPicture.setVisibility(View.GONE);
		scrollToEnd();
	}

	public void hideKeyboardVisibleMode() {
		LinphoneActivity.instance().hideMenu(false);
		contactPicture.setVisibility(View.VISIBLE);
	}

	private void scrollToEnd() {
		messagesList.smoothScrollToPosition(messagesList.getCount());
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
	
	public void displayMessages() {
		adapter = new ChatMessageAdapter(this.getActivity(), chatRoom.getHistory());
		messagesList.setAdapter(adapter);  
		chatRoom.markAsRead();
		LinphoneActivity.instance().updateMissedChatCount();
	}

	public void changeDisplayedChat(String newSipUri, String displayName, String pictureUri) {
		sipUri = newSipUri;
		chatRoom = LinphoneManager.getLcIfManagerNotDestroyedOrNull().getOrCreateChatRoom(sipUri);
		displayChatHeader(displayName, pictureUri);
		displayMessages();
	}
	
	private void sendMessage(String message) {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		boolean isNetworkReachable = lc == null ? false : lc.isNetworkReachable();

		if (chatRoom != null && message != null && message.length() > 0 && isNetworkReachable) {
			LinphoneChatMessage chatMessage = chatRoom.createLinphoneChatMessage(message);
			chatRoom.sendMessage(chatMessage, this);
		} else if (!isNetworkReachable && LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().displayCustomToast(getString(R.string.error_network_unreachable), Toast.LENGTH_LONG);
		}
	}
	
	private void downloadImage(LinphoneChatMessage message) {
		if (message.getFileTransferInformation() == null) {
			Log.w("Unable to download, no file transfer information inside the message");
			return;
		}
		
		if (isUploading || isDownloading) {
			LinphoneActivity.instance().displayCustomToast(getString(R.string.already_downloading_or_uploading), Toast.LENGTH_LONG);
			return;
		}
		
		currentFileTransferMessage = message;
		isDownloading = true;

		uploadProgressBar.setProgress(0);
		uploadLayout.setVisibility(View.VISIBLE);
		progressBarText.setText(getString(R.string.downloading_image));
		textLayout.setVisibility(View.GONE);

		LinphoneManager.getInstance().setupFileDownload();
		message.startFileDownload(new StateListener() {
			@Override
			public void onLinphoneChatMessageStateChanged(LinphoneChatMessage msg, State state) {
				if (state == State.FileTransferError) {
					mHandler.post(new Runnable() {
						@Override
						public void run() {
							LinphoneActivity.instance().displayCustomToast(getString(R.string.error), Toast.LENGTH_SHORT);
						}
					});
				}
			}
		});
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

		LinphoneManager.getInstance().setupFileUpload(data);
        LinphoneContent content = new LinphoneContentImpl(bm.hashCode() + "-" + System.currentTimeMillis() + ".jpg", "image", "jpeg", data, null, 0);
        return chatRoom.createFileTransferMessage(content);
	}
	
	private void uploadImage(Bitmap bm) {
		if (isUploading || isDownloading) {
			LinphoneActivity.instance().displayCustomToast(getString(R.string.already_downloading_or_uploading), Toast.LENGTH_LONG);
			return;
		}
		
		LinphoneChatMessage message = createUploadImageMessage(bm);
		if (message != null) {
			if (imageAlreadyHasAnId == null) {
				message.setAppData(LinphoneUtils.saveImageOnDevice(LinphoneActivity.instance(), message.getFileTransferInformation().getName(), bm));
			} else {
				Log.w("Image fetched from gallery, already has ID " + imageAlreadyHasAnId);
				message.setAppData(imageAlreadyHasAnId);
			}
			imageAlreadyHasAnId = null;
			
			chatRoom.sendMessage(message, this);
			currentFileTransferMessage = message;
			isUploading = true;
			uploadProgressBar.setProgress(0);
			uploadLayout.setVisibility(View.VISIBLE);
			progressBarText.setText(getString(R.string.uploading_image));
			textLayout.setVisibility(View.GONE);
		}
	}

	private void startImagePicker() {
	    List<Intent> cameraIntents = new ArrayList<Intent>();
	    Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
	    File file = new File(Environment.getExternalStorageDirectory(), getString(R.string.temp_photo_name));
	    imageToUploadUri = Uri.fromFile(file);
    	captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageToUploadUri);
	    cameraIntents.add(captureIntent);

	    Intent galleryIntent = new Intent();
	    galleryIntent.setType("image/*");
	    galleryIntent.setAction(Intent.ACTION_PICK);

	    Intent chooserIntent = Intent.createChooser(galleryIntent, getString(R.string.image_picker_title));
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
	
	private LinphoneChatMessage getMessageForId(int id) {
		LinphoneChatMessage message = null;
		if (adapter != null) {
			int position = 0;
			for (position = 0; position < adapter.getCount(); position++) {
				if (id == adapter.getItemId(position)) {
					message = adapter.getItem(position);
					break;
				}
			}
		}
		return message;
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

	private void copyTextMessageToClipboard(int id) {
		LinphoneChatMessage msg = getMessageForId(id);
		if (msg == null)
			return;
		
		if (msg.getText() != null) {
			Compatibility.copyTextToClipboard(getActivity(), msg.getText());
			LinphoneActivity.instance().displayCustomToast(getString(R.string.text_copied_to_clipboard), Toast.LENGTH_SHORT);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
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
		case MENU_DELETE_MESSAGE:
			chatRoom.deleteMessage(getMessageForId(item.getGroupId()));
			adapter.refreshHistory();
			adapter.notifyDataSetChanged();
			break;
		case MENU_COPY_TEXT:
			copyTextMessageToClipboard(item.getGroupId());
			break;
		}
		
		if (imageToUpload != null) {
			uploadImage(imageToUpload);
			imageToUpload = null;
		}
		return true;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		if (v.getId() == R.id.sendPicture) {
			menu.removeGroup(0);
			if (imageToUpload != null) {
				menu.add(0, MENU_PICTURE_SMALL, 0, getString(R.string.share_picture_size_small));
				//menu.add(0, MENU_PICTURE_MEDIUM, 0, getString(R.string.share_picture_size_medium));
				//menu.add(0, MENU_PICTURE_LARGE, 0, getString(R.string.share_picture_size_large));
				menu.add(0, MENU_PICTURE_REAL, 0, getString(R.string.share_picture_size_real));
			}
		} else {
			// To avoid duplicated entries on images
			menu.removeItem(MENU_DELETE_MESSAGE);
			menu.removeItem(MENU_COPY_TEXT);
			
			menu.add(v.getId(), MENU_DELETE_MESSAGE, 0, getString(R.string.delete));
			
			ImageView iv = (ImageView) v.findViewById(R.id.image);
			if (iv == null || iv.getVisibility() != View.VISIBLE) {
				menu.add(v.getId(), MENU_COPY_TEXT, 0, getString(R.string.copy_text));
			}
		}
	}
	
	@Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
		imageAlreadyHasAnId = null;
        if (requestCode == ADD_PHOTO && resultCode == Activity.RESULT_OK) {
        	if (data != null && data.getExtras() != null && data.getExtras().get("data") != null) {
        		Bitmap bm = (Bitmap) data.getExtras().get("data");
        		showPopupMenuAskingImageSize(null, bm);
        	}
        	else if (data != null && data.getData() != null) {
        		Context context = LinphoneActivity.instance();
	    		imageAlreadyHasAnId = LinphoneUtils.getImageIdFromUri(context, data.getData());
	    		String filePath = LinphoneUtils.getImagePathForImageId(context, imageAlreadyHasAnId);
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
	
	class ChatMessageAdapter extends BaseAdapter {
		LinphoneChatMessage[] history;
		Context context;
		 
		public ChatMessageAdapter(Context context, LinphoneChatMessage[] history) {
			this.history = history; 
			this.context = context;
		}
		
		public void refreshHistory(){
			this.history = chatRoom.getHistory();
		}
		 
		@Override
		public int getCount() {
			return history.length;
		}

		@Override
		public LinphoneChatMessage getItem(int position) {
			return history[position];
		}

		@Override
		public long getItemId(int position) {
			return history[position].getStorageId();
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			RelativeLayout layout;
			if (convertView != null) {
				layout = (RelativeLayout) convertView;
				layout.removeAllViews();
			} else {
				layout = new RelativeLayout(context);
			}
			
			final LinphoneChatMessage msg = history[position];
			
			BubbleChat bubble = new BubbleChat(context, null, msg, new BubbleChatActionListener() {
				@Override
				public void onDownloadButtonClick() {
					downloadImage(msg);
				}
			});
			
			if (currentFileTransferMessage == msg) {
				bubble.setDownloadInProgressLayout();
			}
			
			View view = bubble.getView();
			registerForContextMenu(view);
			registerForContextMenu(view.findViewById(R.id.image));
			layout.addView(view);
			
			return layout;
		}
	}
}
