package com.example.heart_under_blade.test;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.provider.DocumentFile;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity {

    int idOfSelectedItemOfContextMenu;
    DocumentFile selectedFile;

    LinearLayout scrollView;

    DocumentFile pickedDirectory;
    List<DocumentFile> directory = new ArrayList<DocumentFile>();
    private static final int READ_REQUEST_CODE = 42;
    private static final int WRITE_REQUEST_CODE = 43;
    private static final String TAG = "LOG";

    private final int CURRENT_ACTION_ENCRYPT = 1;
    private final int CURRENT_ACTION_DECRYPT = 2;
    CryptThread cryptThread;

    Handler mHandler;
    private final int HANDLER_ENCRYPT_FINISHED = 1;
    private final int HANDLER_DECRYPT_FINISHED = 2;
    public final int HANDLER_BLUETOOTH_CONNECTED = 3;
    public final int HANDLER_RETRIVED_BUF = 4;
    public final int HANDLER_BEGIN_LISTEN = 5;
    public final int HANDLER_FILE_JUST_SEND = 6;
    public final int HANDLER_RETRIVED_FILE_JUST_SAVED = 7;

    private final int CONTEXT_MENU_SEND = 1;
    private final int CONTEXT_MENU_CRYPT = 2;
    private final int CONTEXT_MENU_DECRYPT = 3;
    private final int CONTEXT_MENU_DOWNLOAD = 4;
    private final int CONTEXT_MENU_DELETE = 5;

    // 0 - nothing // 1 - phone dir // 2 - bth directory
    private int DIRECTORY_ON_MAIN_SCREEN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NotificationCompat.Builder encFinished = new NotificationCompat.Builder(this).setSmallIcon(R.drawable.icon)
                .setContentTitle("Aveli")
                .setContentText("Encrypting finished");
        final NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        final Notification encFinNoti = encFinished.build();
        Uri ringURI = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        encFinNoti.sound = ringURI;
        //mNotificationManager.notify(0, justSendNoti);

        NotificationCompat.Builder decFinished = new NotificationCompat.Builder(this).setSmallIcon(R.drawable.icon)
                .setContentTitle("Aveli")
                .setContentText("File just saved");
        final Notification decFinNoti = decFinished.build();
        decFinNoti.sound = ringURI;

        mHandler = new Handler() {
            public void handleMessage(android.os.Message msg) {

                switch (msg.what) {
                    case HANDLER_ENCRYPT_FINISHED: {
                        Toast.makeText(getApplicationContext(), "Encrypting finished", Toast.LENGTH_SHORT).show();
                        mNotificationManager.notify(0, encFinNoti);
                    } break;
                    case HANDLER_DECRYPT_FINISHED: {
                        Toast.makeText(getApplicationContext(), "Decrypting finished", Toast.LENGTH_SHORT).show();
                        mNotificationManager.notify(0, decFinNoti);
                    }
                }
            }
        };

        pickedDirectory = null;

        DIRECTORY_ON_MAIN_SCREEN = 0;

        scrollView = (LinearLayout) findViewById(R.id.layoutOnScroll);
    }

    public void clickOnOpenDir(View v) {
        DIRECTORY_ON_MAIN_SCREEN = 1;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, READ_REQUEST_CODE);
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        switch (DIRECTORY_ON_MAIN_SCREEN) {
            case 1: {
                idOfSelectedItemOfContextMenu = v.getId();

                //menu.add(0, CONTEXT_MENU_SEND, 0, "Send");
                menu.add(0, CONTEXT_MENU_CRYPT, 0, "Encrypt");
                menu.add(0, CONTEXT_MENU_DECRYPT, 0, "Decrypt");
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {

        if (DIRECTORY_ON_MAIN_SCREEN == 1) {

            String nameOfSelectedFile = directory.get(idOfSelectedItemOfContextMenu).getName();
            InputStream in = null;
            OutputStream out = null;

            // open
            try {
                in = getContentResolver().openInputStream(directory.get(idOfSelectedItemOfContextMenu).getUri());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            switch (item.getItemId()) {
                case CONTEXT_MENU_CRYPT: {
                    DocumentFile newFile = pickedDirectory.createFile("*/*", "crypted." + directory.get(idOfSelectedItemOfContextMenu).getName());

                    try {
                        out = getContentResolver().openOutputStream(newFile.getUri());
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }

                    if (in != null && out != null) {
                        cryptThread = new CryptThread(CURRENT_ACTION_ENCRYPT, in, out);
                        cryptThread.start();
                    }

                }
                break;
                case CONTEXT_MENU_DECRYPT: {
                    DocumentFile newFile = pickedDirectory.createFile("*/*", "decrypted." + directory.get(idOfSelectedItemOfContextMenu).getName());

                    try {
                        out = getContentResolver().openOutputStream(newFile.getUri());
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }

                    if (in != null && out != null) {
                        cryptThread = new CryptThread(CURRENT_ACTION_DECRYPT, in, out);
                        cryptThread.start();
                    }
                }
                break;
            }
        }

        return super.onContextItemSelected(item);
    }

    /*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        List<String> list = bluetooth.getAvailableDeveces();
        for (int i = 0; i < list.size(); i++) {
            menu.add(0, i, 0, list.get(i));
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        bluetooth.connectTo(item.getItemId());
        return super.onOptionsItemSelected(item);
    } */

    private int return_file_icon_id(String fileName)
    {
        int i = fileName.lastIndexOf(".");
        String extention = fileName.substring(i + 1);
        extention = extention.toLowerCase();

        if (extention.equals("jpg") || extention.equals("jpeg") || extention.equals("png")) {
            return R.drawable.pic_icon1;
        }
        if (extention.equals("mp4") || extention.equals("avi") || extention.equals("mov")) {
            return R.drawable.video_icon1;
        }
        if (extention.equals("mp3")) {
            return R.drawable.audio_icon1;
        }

        return R.drawable.doc_icon1;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (resultCode == RESULT_OK) {
            Uri treeUri = resultData.getData();
            pickedDirectory = DocumentFile.fromTreeUri(this, treeUri);

            // clear current directory
            directory.clear();

            // get list of all files in returned directory
            for (DocumentFile file : pickedDirectory.listFiles()) {
                Log.d(TAG, "Found file " + file.getName() + " with size " + file.length());
                directory.add(file);
            }

            LinearLayout scrollLayout = (LinearLayout) findViewById(R.id.layoutOnScroll);
            scrollLayout.removeAllViews();

            // we got the file list, now we need to output this list in our scroll list
            int idCount = 0;
            for (DocumentFile file : directory) {

                if (file.isFile()) {

                    ImageView icon = new ImageView(this);
                    ViewGroup.LayoutParams iconParam = new ViewGroup.LayoutParams(150, 150);

                    // create layout with text
                    TextView fileName = new TextView(this);
                    fileName.setText(file.getName());
                    fileName.setTextSize(17);

                    LinearLayout layoutWithText = new LinearLayout(this);
                    layoutWithText.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout.LayoutParams layoutWithTextParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    layoutWithTextParams.leftMargin = 60;
                    layoutWithTextParams.topMargin = 30;
                    layoutWithText.setLayoutParams(layoutWithTextParams);

                    layoutWithText.addView(fileName);


                    // this layout we will add to layoutOnScroll
                    LinearLayout tempLayout = new LinearLayout(this);
                    LinearLayout.LayoutParams layoutParam = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    layoutParam.leftMargin = 10;
                    layoutParam.bottomMargin = 10;
                    tempLayout.setLayoutParams(layoutParam);
                    tempLayout.setOrientation(LinearLayout.HORIZONTAL);

                    // icon.setImageDrawable(getResources().getDrawable(R.drawable.file_icon));
                    icon.setImageDrawable(getResources().getDrawable(return_file_icon_id(file.getName())));
                    icon.setLayoutParams(iconParam);
                    tempLayout.setId(idCount);
                    registerForContextMenu(tempLayout);

                    tempLayout.addView(icon);
                    tempLayout.addView(layoutWithText);

                    scrollLayout.addView(tempLayout);
                }
                idCount++;
            }
        }
    }

    private byte[] intToByte(int num) {
        byte[] bytes = new byte[4];

        bytes[0] = (byte) ((num >> 24) & 0xff);
        bytes[1] = (byte) ((num >> 16) & 0xff);
        bytes[2] = (byte) ((num >> 8) & 0xff);
        bytes[3] = (byte) (num & 0xff);

        return bytes;
    }

    /***************************************************
     *                      THREADS                    *
     ***************************************************/


    private class CryptThread extends Thread {

        private boolean is_fail;
        private int CURRENT_ACTION;
        private InputStream in;
        private OutputStream out;
        //MyCrypt myCrypt = new MyCrypt();

        private Key key;

        CryptThread (int action, InputStream inputStream, OutputStream outputStream) {
            this.is_fail = false;
            this.CURRENT_ACTION = action;
            this.in = inputStream;
            this.out = outputStream;

            EditText passwordLine = findViewById(R.id.passwordTextLine);
            String password = passwordLine.getText().toString();

            try {

                MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
                digest.update(password.getBytes());

                this.key = new SecretKeySpec(digest.digest(), "AES");

            } catch (Exception e) {
                Log.d("Error", e.getMessage());
                is_fail = true;
            }


        }

        public void run() {
            switch(this.CURRENT_ACTION) {
                case CURRENT_ACTION_ENCRYPT: {
                    try {
                        Cipher cipher = Cipher.getInstance("AES");
                        cipher.init(Cipher.ENCRYPT_MODE, this.key);
                        while (in.available() > 0) {
                            int available_bytes = in.available();
                            byte[] buffer = new byte[available_bytes];
                            // byte[] encrypted_buffer = new byte[available_bytes];
                            in.read(buffer);

                            byte[] encrypted_buffer = cipher.doFinal(buffer);

                            /* int offset = 16;
                            for (int i = 0; i < available_bytes; i += offset) {
                                if (available_bytes - i < offset) {
                                    byte[] block = new byte[available_bytes - i];
                                    System.arraycopy(buffer, i, block, 0, available_bytes - i);
                                    byte[] encrypted_block = cipher.doFinal(block);
                                    System.arraycopy(encrypted_block, 0, encrypted_buffer, i, available_bytes - i);
                                } else {
                                    // encrypt this block
                                    byte[] block = new byte[16];
                                    System.arraycopy(buffer, i, block, 0, offset);
                                    byte[] encrypted_block = cipher.update(block);
                                    System.arraycopy(encrypted_block, 0, encrypted_buffer, i, offset);
                                }
                            }*/
                            out.write(encrypted_buffer);
                        }
                    } catch (Exception e) {
                        Log.d("Warning", e.getMessage());
                    }
                    mHandler.obtainMessage(HANDLER_ENCRYPT_FINISHED).sendToTarget();
                } break;
                case CURRENT_ACTION_DECRYPT: {
                    try {
                        Cipher cipher = Cipher.getInstance("AES");
                        cipher.init(Cipher.DECRYPT_MODE, this.key);
                        while (in.available() > 0) {
                            int available_bytes = in.available();
                            byte[] buffer = new byte[available_bytes];
                            // byte[] decrypted_buffer = new byte[available_bytes];
                            in.read(buffer);

                            byte[] decrypted_buffer = cipher.doFinal(buffer);

                            /* int offset = 16;
                            for (int i = 0; i < available_bytes; i += offset) {
                                if (available_bytes - i < offset) {
                                    byte[] block = new byte[available_bytes - i];
                                    System.arraycopy(buffer, i, block, 0, available_bytes - i);
                                    byte[] decrypted_block = cipher.doFinal(block);
                                    System.arraycopy(decrypted_block, 0, decrypted_buffer, i, available_bytes - i);
                                } else {
                                    // encrypt this block
                                    byte[] block = new byte[16];
                                    System.arraycopy(buffer, i, block, 0, offset);
                                    byte[] decrypted_block = cipher.update(block);
                                    System.arraycopy(decrypted_block, 0, decrypted_buffer, i, offset);
                                }
                            }*/
                            out.write(decrypted_buffer);
                        }
                    } catch (Exception e) {
                        Log.d("Warning", e.getMessage());
                    }
                    mHandler.obtainMessage(HANDLER_DECRYPT_FINISHED).sendToTarget();
                } break;
            }
        }



    }

}