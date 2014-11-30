package com.example.printbuddy;

import java.util.HashMap;
import java.util.Iterator;

import com.example.printbuddy.MyView.OnToggledListener;

import android.support.v7.app.ActionBarActivity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.GridLayout;
import android.widget.TextView;

public class MainActivity extends ActionBarActivity 
 implements OnToggledListener{

 TextView textStatus;
 MyView[] myViews;
 GridLayout myGridLayout;
 
 private static final int targetVendorID = 9025; //Arduino Uno
 private static final int targetProductID = 67; //Arduino Uno, not 0067
 private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
 PendingIntent mPermissionIntent;
 UsbDevice deviceFound = null;
 UsbInterface usbInterfaceFound = null;
 UsbInterface usbInterface;
 UsbDeviceConnection usbDeviceConnection;
 UsbEndpoint endpointIn = null;
 UsbEndpoint endpointOut = null;

 @Override
 protected void onCreate(Bundle savedInstanceState) {
  super.onCreate(savedInstanceState);
  setContentView(R.layout.activity_main);

  textStatus = (TextView) findViewById(R.id.textstatus);
  myGridLayout = (GridLayout) findViewById(R.id.mygrid);

  int numOfCol = myGridLayout.getColumnCount();
  int numOfRow = myGridLayout.getRowCount();
  myViews = new MyView[numOfCol * numOfRow];
  for (int yPos = 0; yPos < numOfRow; yPos++) {
   for (int xPos = 0; xPos < numOfCol; xPos++) {
    MyView tView = new MyView(this, xPos, yPos);
    tView.setOnToggledListener(this);
    myViews[yPos * numOfCol + xPos] = tView;
    myGridLayout.addView(tView);
   }
  }

  myGridLayout.getViewTreeObserver().addOnGlobalLayoutListener(
    new OnGlobalLayoutListener() {

     @Override
     public void onGlobalLayout() {

      int pLength;
      final int MARGIN = 5;

      int pWidth = myGridLayout.getWidth();
      int pHeight = myGridLayout.getHeight();
      int numOfCol = myGridLayout.getColumnCount();
      int numOfRow = myGridLayout.getRowCount();

      // Set myGridLayout equal width and height
      if (pWidth >= pHeight) {
       pLength = pHeight;
      } else {
       pLength = pWidth;
      }
      ViewGroup.LayoutParams pParams = myGridLayout
        .getLayoutParams();
      pParams.width = pLength;
      pParams.height = pLength;
      myGridLayout.setLayoutParams(pParams);

      int w = pLength / numOfCol; // pWidth/numOfCol;
      int h = pLength / numOfRow; // pHeight/numOfRow;

      for (int yPos = 0; yPos < numOfRow; yPos++) {
       for (int xPos = 0; xPos < numOfCol; xPos++) {
        GridLayout.LayoutParams params = (GridLayout.LayoutParams) myViews[yPos
          * numOfCol + xPos].getLayoutParams();
        params.width = w - 2 * MARGIN;
        params.height = h - 2 * MARGIN;
        params.setMargins(MARGIN, MARGIN, MARGIN,
          MARGIN);
        myViews[yPos * numOfCol + xPos]
          .setLayoutParams(params);
       }
      }

      // deprecated in API level 16
      myGridLayout.getViewTreeObserver()
        .removeGlobalOnLayoutListener(this);
      // for API Level >= 16
      // myGridLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
     }
    });
  
  // register the broadcast receiver
  mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
   ACTION_USB_PERMISSION), 0);
  IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
  registerReceiver(mUsbReceiver, filter);

  registerReceiver(mUsbDeviceReceiver, new IntentFilter(
   UsbManager.ACTION_USB_DEVICE_ATTACHED));
  registerReceiver(mUsbDeviceReceiver, new IntentFilter(
   UsbManager.ACTION_USB_DEVICE_DETACHED));

  connectUsb();
 }

 @Override
 public void OnToggled(MyView v, boolean touchOn) {
  if(deviceFound != null){
   byte[] bytesSend = setupCmd();

   usbDeviceConnection.bulkTransfer(endpointOut,
     bytesSend, bytesSend.length, 0);
  }
 }
 
 private byte[] setupCmd(){
  byte[] bytes = new byte[18];
  
  bytes[0] = (byte) 0xFF; //sync byte
  bytes[1] = 0x01; //CMD
  
  int i = 2;
  int numOfRow = 8;
  int numOfCol = 8;
  int numOfHalfCol = 4;

  for (int yPos = 0; yPos < numOfRow; yPos++) {
   byte b = 0x00;
   byte mask = 0x01;

   for (int xPos = 0; xPos < numOfHalfCol; xPos++) {
    
    if(myViews[yPos * numOfCol + xPos].touchOn){
     b = (byte) (b | mask);
    }
    
    mask = (byte) (mask << 1);
   }
   bytes[i] = b;
   
   i++;
   b = 0x00;
   mask = 0x10;
   for (int xPos = numOfHalfCol; xPos < numOfCol; xPos++) {
    
    if(myViews[yPos * numOfCol + xPos].touchOn){
     b = (byte) (b | mask);
    }
    
    mask = (byte) (mask << 1);
   }
   bytes[i] = b;
   
   i++;
  }

  return bytes;
 }
 
 //usb related
 private boolean setupUsbComm() {

  // for more info, search SET_LINE_CODING and
  // SET_CONTROL_LINE_STATE in the document:
  // "Universal Serial Bus Class Definitions for Communication Devices"
  // at http://adf.ly/dppFt
  final int RQSID_SET_LINE_CODING = 0x20;
  final int RQSID_SET_CONTROL_LINE_STATE = 0x22;

  boolean success = false;

  UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
  Boolean permitToRead = manager.hasPermission(deviceFound);

  if (permitToRead) {
   usbDeviceConnection = manager.openDevice(deviceFound);
   if (usbDeviceConnection != null) {
    usbDeviceConnection.claimInterface(usbInterfaceFound, true);

    usbDeviceConnection.controlTransfer(0x21, // requestType
      RQSID_SET_CONTROL_LINE_STATE, // SET_CONTROL_LINE_STATE
      0, // value
      0, // index
      null, // buffer
      0, // length
      0); // timeout

    // baud rate = 9600
    // 8 data bit
    // 1 stop bit
    byte[] encodingSetting = new byte[] { (byte) 0x80, 0x25, 0x00,
      0x00, 0x00, 0x00, 0x08 };
    usbDeviceConnection.controlTransfer(0x21, // requestType
      RQSID_SET_LINE_CODING, // SET_LINE_CODING
      0, // value
      0, // index
      encodingSetting, // buffer
      7, // length
      0); // timeout
   }

  } else {
   manager.requestPermission(deviceFound, mPermissionIntent);
   textStatus.setText("Permission: " + permitToRead);
  }

  return success;
 }
 
 private void connectUsb() {

  textStatus.setText("connectUsb()");

  searchEndPoint();

  if (usbInterfaceFound != null) {
   setupUsbComm();
  }

 }
 
 private void releaseUsb() {

  textStatus.setText("releaseUsb()");

  if (usbDeviceConnection != null) {
   if (usbInterface != null) {
    usbDeviceConnection.releaseInterface(usbInterface);
    usbInterface = null;
   }
   usbDeviceConnection.close();
   usbDeviceConnection = null;
  }

  deviceFound = null;
  usbInterfaceFound = null;
  endpointIn = null;
  endpointOut = null;
 }

 private void searchEndPoint() {

  usbInterfaceFound = null;
  endpointOut = null;
  endpointIn = null;

  // Search device for targetVendorID and targetProductID
  if (deviceFound == null) {
   UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
   HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
   Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

   while (deviceIterator.hasNext()) {
    UsbDevice device = deviceIterator.next();

    if (device.getVendorId() == targetVendorID) {
     if (device.getProductId() == targetProductID) {
      deviceFound = device;
     }
    }
   }
  }

  if (deviceFound == null) {
   textStatus.setText("device not found");
  } else {

   // Search for UsbInterface with Endpoint of USB_ENDPOINT_XFER_BULK,
   // and direction USB_DIR_OUT and USB_DIR_IN

   for (int i = 0; i < deviceFound.getInterfaceCount(); i++) {
    UsbInterface usbif = deviceFound.getInterface(i);

    UsbEndpoint tOut = null;
    UsbEndpoint tIn = null;

    int tEndpointCnt = usbif.getEndpointCount();
    if (tEndpointCnt >= 2) {
     for (int j = 0; j < tEndpointCnt; j++) {
      if (usbif.getEndpoint(j).getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
       if (usbif.getEndpoint(j).getDirection() == UsbConstants.USB_DIR_OUT) {
        tOut = usbif.getEndpoint(j);
       } else if (usbif.getEndpoint(j).getDirection() == UsbConstants.USB_DIR_IN) {
        tIn = usbif.getEndpoint(j);
       }
      }
     }

     if (tOut != null && tIn != null) {
      // This interface have both USB_DIR_OUT
      // and USB_DIR_IN of USB_ENDPOINT_XFER_BULK
      usbInterfaceFound = usbif;
      endpointOut = tOut;
      endpointIn = tIn;
     }
    }

   }

  }
 }
 
 private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

  @Override
  public void onReceive(Context context, Intent intent) {
   String action = intent.getAction();
   if (ACTION_USB_PERMISSION.equals(action)) {

    textStatus.setText("ACTION_USB_PERMISSION");

    synchronized (this) {
     UsbDevice device = (UsbDevice) intent
       .getParcelableExtra(UsbManager.EXTRA_DEVICE);

     if (intent.getBooleanExtra(
       UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
      if (device != null) {
       connectUsb();
      }
     } else {
      textStatus.setText("permission denied for device ");
     }
    }
   }
  }
 };

 private final BroadcastReceiver mUsbDeviceReceiver = new BroadcastReceiver() {

  @Override
  public void onReceive(Context context, Intent intent) {
   String action = intent.getAction();
   if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {

    deviceFound = (UsbDevice) intent
      .getParcelableExtra(UsbManager.EXTRA_DEVICE);

    textStatus.setText("ACTION_USB_DEVICE_ATTACHED");

    connectUsb();

   } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {

    UsbDevice device = (UsbDevice) intent
      .getParcelableExtra(UsbManager.EXTRA_DEVICE);

    textStatus.setText("ACTION_USB_DEVICE_DETACHED");

    if (device != null) {
     if (device == deviceFound) {
      releaseUsb();
     }
    }
    
   }
  }

 };

}