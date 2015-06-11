package com.example.navisys;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.example.navisys.GPSTracker;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.provider.ContactsContract;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;

import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

@SuppressLint("HandlerLeak")
public class MainActivity extends Activity implements OnItemClickListener {
	public static boolean active = false, nav = false;
	ArrayAdapter<String> listAdapter;
	ListView listView;
	TextToSpeech tts;
	BluetoothAdapter btAdapter;
	BluetoothSocket ble;
	Set<BluetoothDevice> devicesArray;
	ArrayList<String> pairedDevices;
	TextView t;
	ConnectedThread connectedThread;
	ArrayList<BluetoothDevice> devices;
	public static final UUID MY_UUID = UUID
			.fromString("00001101-0000-1000-8000-00805F9B34FB");
	protected static final int SUCCESS_CONNECT = 0;
	protected static final int MESSAGE_READ = 1;
	IntentFilter filter;
	BroadcastReceiver receiver;
	public static String tag = "debugging", addr, contactName;
	public double dlat = 0, dlng = 0;
	public static int flag = 0;
	public final int RESULT_SPEECH = 100;

	Handler ts = new Handler() {
		@Override
		public void handleMessage(Message m) {
			super.handleMessage(m);

			switch (m.what) {

			case SUCCESS_CONNECT:
				// DO something
				connectedThread = new ConnectedThread((BluetoothSocket) m.obj);
				ble = (BluetoothSocket) m.obj;
				listView.setVisibility(View.GONE);
				t = (TextView) findViewById(R.id.tvPD);
				t.setText("Receiving");
				Toast.makeText(getApplicationContext(), "Connected",
						Toast.LENGTH_LONG).show();
				tts.speak("Connected to the Bluetooth",
						TextToSpeech.QUEUE_FLUSH, null);
				connectedThread.start();
				Log.i(tag, "connected");
				break;

			case MESSAGE_READ:

				String s = m.obj.toString();
				// t=(TextView)findViewById(R.id.tvPD);
				// t.setText(s);
				if (!(s.contains(" ") && s.indexOf(" ") == 1 || s.length() == 1))
					return;
				try {
					if (s.charAt(0) == '0')
						tts.speak("Please Stop. Obstacle on your left",
								TextToSpeech.QUEUE_ADD, null);
					else if (s.charAt(0) == '1')
						tts.speak("Please Stop. Obstacle center",
								TextToSpeech.QUEUE_ADD, null);
					else if (s.charAt(0) == '2')
						tts.speak("Please Stop. Obstacle on your right ",
								TextToSpeech.QUEUE_ADD, null);
					else if (s.charAt(0) == '4') {
						String dist = s.substring(2);
						tts.speak("Human detected on your left at " + dist
								+ "centimeters", TextToSpeech.QUEUE_ADD, null);
					} else if (s.charAt(0) == '5') {
						String dist = s.substring(2);
						tts.speak("Human detected on your center at " + dist
								+ "centimeters", TextToSpeech.QUEUE_ADD, null);
					} else if (s.charAt(0) == '6') {
						String dist = s.substring(2);
						tts.speak("Human detected on your right at " + dist
								+ "centimeters", TextToSpeech.QUEUE_ADD, null);
					}

					else if (s.charAt(0) == '3')
						tts.speak(
								"Moving obstacle detected. Road crossing not adviced",
								TextToSpeech.QUEUE_ADD, null);
					else if (s.charAt(0) == '7')
						tts.speak("No camera connected",
								TextToSpeech.QUEUE_ADD, null);
					else if (s.charAt(0) == '9') {
						if (active) {
							tts.speak("What can i do for you...",
									TextToSpeech.QUEUE_FLUSH, null);
							Thread.sleep(3000);
							Intent intSpeech = new Intent(
									RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
							intSpeech.putExtra(
									RecognizerIntent.EXTRA_LANGUAGE_MODEL,
									"en-US");

							try {
								startActivityForResult(intSpeech, RESULT_SPEECH);
							} catch (ActivityNotFoundException a) {
								Toast t = Toast
										.makeText(
												getApplicationContext(),
												"Opps! Your device doesn't support Speech to Text",
												Toast.LENGTH_SHORT);
								t.show();
							}
						}
					}
				}

				catch (Exception e) {
					Toast.makeText(getApplicationContext(), e.toString(),
							Toast.LENGTH_SHORT).show();
					tts.speak("exception", TextToSpeech.QUEUE_FLUSH, null);
				}
				break;

			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		// getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		tts = new TextToSpeech(getApplicationContext(),
				new TextToSpeech.OnInitListener() {
					@Override
					public void onInit(int status) {

					}
				});
		if (btAdapter == null) {
			Toast.makeText(getApplicationContext(), "No bluetooth detected",
					Toast.LENGTH_LONG).show();
			tts.speak("Your device does not have a bluetooth adapter",
					TextToSpeech.QUEUE_FLUSH, null);
			finish();
		}

		if (!btAdapter.isEnabled()) {
			// turnOnBT();
			btAdapter.enable();
		}

		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		start();
		check c = new check();
		c.start();
	}

	private void start() {
		// TODO Auto-generated method stub
		init();
		getPairedDevices();
		startDiscovery();
	}

	private void startDiscovery() {
		// TODO Auto-generated method stub
		btAdapter.cancelDiscovery();
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		btAdapter.startDiscovery();

	}

	private void getPairedDevices() {
		// TODO Auto-generated method stub
		devicesArray = btAdapter.getBondedDevices();
		if (devicesArray.size() > 0) {
			for (BluetoothDevice device : devicesArray) {
				pairedDevices.add(device.getName());

			}
		}
	}

	@Override
	public void onBackPressed() {
		// TODO Auto-generated method stub
		super.onBackPressed();
		try {
			if (receiver != null)
				unregisterReceiver(receiver);
			ble.close();
			tts.shutdown();
			btAdapter.disable();

		} catch (Exception e) {
			/*
			 * Toast.makeText(getApplicationContext(), e.toString(),
			 * Toast.LENGTH_LONG).show();
			 */
		} finally {
			finish();
		}

	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		try {
			if (receiver != null)
				unregisterReceiver(receiver);
		} catch (Exception e) {
			/*
			 * Toast.makeText(getApplicationContext(), e.toString(),
			 * Toast.LENGTH_LONG).show();
			 */
		}
		btAdapter.disable();
		tts.shutdown();
	}

	private void reg() {

		filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		registerReceiver(receiver, filter);
		filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
		registerReceiver(receiver, filter);
	}

	private void init() {

		listView = (ListView) findViewById(R.id.listView);
		listView.setOnItemClickListener(this);
		listAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1, 0);
		listView.setAdapter(listAdapter);
		pairedDevices = new ArrayList<String>();
		filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		devices = new ArrayList<BluetoothDevice>();
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				// TODO Auto-generated method stub
				try {
					String action = intent.getAction();

					if (BluetoothDevice.ACTION_FOUND.equals(action)) {
						BluetoothDevice device = intent
								.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
						String s = "";
						for (int a = 0; a < pairedDevices.size(); a++) {
							if (device.getName().equals(pairedDevices.get(a))) {
								// append
								s = "(Paired)";
								break;
							}
						}

						if (device.getAddress().equals("98:D3:31:20:05:C4")
								&& flag == 0) {
							ConnectThread connect = new ConnectThread(device);
							connect.start();
						}
						if(!devices.contains(device)){
							devices.add(device);
							listAdapter.add(device.getName() + " " + s + " " + "\n"
									+ device.getAddress());
						}
						
					}

					else if (BluetoothAdapter.ACTION_STATE_CHANGED
							.equals(action)) {
						if (btAdapter.getState() == BluetoothAdapter.STATE_OFF) {
							btAdapter.enable();
							// turnOnBT();
						}
					}
				} catch (Exception e) {
				}
			}

		};

		registerReceiver(receiver, filter);
		filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
		registerReceiver(receiver, filter);

	}

	public int getLatLong(String youraddress) {

		try {
			String uri = "http://maps.google.com/maps/api/geocode/json?address="
					+ URLEncoder.encode(youraddress, "UTF-8") + "&sensor=false";
			HttpGet httpGet = new HttpGet(uri);
			HttpClient client = new DefaultHttpClient();
			HttpResponse response;
			StringBuilder stringBuilder = new StringBuilder();

			response = client.execute(httpGet);
			HttpEntity entity = response.getEntity();
			InputStream stream = entity.getContent();
			int b;
			while ((b = stream.read()) != -1) {
				stringBuilder.append((char) b);
			}

			JSONObject jsonObject = new JSONObject();

			jsonObject = new JSONObject(stringBuilder.toString());

			if (jsonObject.get("status").toString().equalsIgnoreCase("OK")) {
				dlng = ((JSONArray) jsonObject.get("results")).getJSONObject(0)
						.getJSONObject("geometry").getJSONObject("location")
						.getDouble("lng");

				dlat = ((JSONArray) jsonObject.get("results")).getJSONObject(0)
						.getJSONObject("geometry").getJSONObject("location")
						.getDouble("lat");
				nav = true;
				return 1;
			} else {
				Toast.makeText(getApplicationContext(),
						"no result found on maps", Toast.LENGTH_SHORT).show();
				nav = false;
				return 0;
			}

		} catch (ClientProtocolException e) {
			e.printStackTrace();
			Toast.makeText(getApplicationContext(), e.toString(),
					Toast.LENGTH_LONG).show();
			return 0;

		} catch (IOException e) {
			e.printStackTrace();
			Toast.makeText(getApplicationContext(), e.toString(),
					Toast.LENGTH_LONG).show();
			return 0;

		} catch (JSONException e) {
			e.printStackTrace();
			Toast.makeText(getApplicationContext(), e.toString(),
					Toast.LENGTH_LONG).show();
			return 0;

		} catch (Exception e) {
			Toast.makeText(getApplicationContext(), e.toString(),
					Toast.LENGTH_LONG).show();
			return 0;
		}
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		reg();
		startDiscovery();
		active = true;

	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		active = false;
		try {
			if (receiver != null)
				unregisterReceiver(receiver);
		} catch (Exception e) {
			/*
			 * Toast.makeText(getApplicationContext(), e.toString(),
			 * Toast.LENGTH_LONG).show();
			 */
		}
	}

	@Override
	protected void onStop() {
		// TODO Auto-generated method stub
		super.onStop();
		active = false;
	}

	@Override
	protected void onStart() {
		// TODO Auto-generated method stub
		super.onStart();
		active = true;
	}

	@Override
	protected void onRestart() {
		// TODO Auto-generated method stub
		super.onRestart();
		active = true;
		tts.speak("Back to the Application", TextToSpeech.QUEUE_ADD, null);
	}

	void check() {
		if (dlat < 12.0 || dlat > 14)
			nav = false;
		if (dlng < 75 || dlng > 79)
			nav = false;
	}

	String getContactAddr(String contactNam) {

		String id = null;
		int found = 0;
		Cursor cur = getContentResolver().query(
				ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
		if (cur.getCount() > 0) {
			while (cur.moveToNext()) {
				id = cur.getString(cur
						.getColumnIndex(ContactsContract.Contacts._ID));
				String name = cur
						.getString(cur
								.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
				if (name.equalsIgnoreCase(contactNam)) {
					found = 1;
					break;
				}
			}
		}
		cur.close();
		if (found == 1) {
			String street = null;
			String addrWhere = ContactsContract.Data.CONTACT_ID + " = ? AND "
					+ ContactsContract.Data.MIMETYPE + " = ?";
			String[] addrWhereParams = new String[] {
					id,
					ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE };
			Cursor addrCur = getContentResolver().query(
					ContactsContract.Data.CONTENT_URI, null, addrWhere,
					addrWhereParams, null);
			while (addrCur.moveToNext()) {

				street = addrCur
						.getString(addrCur
								.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.STREET));
			}
			addrCur.close();

			return street;
		}
		return null;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// TODO Auto-generated method stub
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
		case RESULT_SPEECH: {
			if (resultCode == RESULT_OK && null != data) {

				ArrayList<String> text = data
						.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
				addr = text.get(0).toString();
				Toast.makeText(getApplicationContext(), addr, Toast.LENGTH_LONG)
						.show();

				if (addr.equalsIgnoreCase("no")
						|| addr.equalsIgnoreCase("cancel")
						|| addr.equalsIgnoreCase("exit")
						|| addr.equalsIgnoreCase("nowhere")) {
					tts.speak("To Navigate,toggle the switch",
							TextToSpeech.QUEUE_FLUSH, null);
					return;
				}

				else if (addr.contains("in contact")) {
					String temp;
					int i = addr.indexOf("in contact");
					contactName = addr.substring(0, i - 1);
					if ((temp = getContactAddr(contactName)) != null)
						addr = temp;
					else {
						tts.speak("Contact information of " + contactName
								+ " not found", TextToSpeech.QUEUE_FLUSH, null);
						return;
					}

				} else if (addr.contains("exit application")) {
					tts.speak("Exiting application now",
							TextToSpeech.QUEUE_FLUSH, null);
					finish();
					return;
				}

				else if (addr.contains("call")) {
					try {
						String phoneNumber = "", Lname = addr.substring(5);
						Cursor phones = getContentResolver()
								.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
										null, null, null, null);
						int fl = 0;
						while (phones.moveToNext()) {
							String name = phones
									.getString(phones
											.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
							phoneNumber = phones
									.getString(phones
											.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
							if (name.equalsIgnoreCase(Lname)) {
								fl = 1;
								break;
							}
						}
						phones.close();
						if (fl == 1) {
							Intent callIntent = new Intent(Intent.ACTION_CALL);
							callIntent.setData(Uri.parse("tel:" + phoneNumber));
							tts.speak("Calling " + Lname,
									TextToSpeech.QUEUE_FLUSH, null);
							startActivity(callIntent);
						} else
							tts.speak("Contact Information of"+Lname+" Not Found",
									TextToSpeech.QUEUE_FLUSH, null);
					} catch (Exception e) {

					}

				}

				else if (addr.contains("emergency")) {
					final GPSTracker gpsTracker = new GPSTracker(this);
					String lat = "";
					if (gpsTracker.canGetLocation()) {
						lat = String.valueOf(gpsTracker.latitude);
						lat += " " + String.valueOf(gpsTracker.longitude);
						SmsManager sms = SmsManager.getDefault();
						String message = "SOS " + lat;
						sms.sendTextMessage("+917829908952", null, message,
								null, null);
						sms.sendTextMessage("+918553336207", null, message,
								null, null);
						sms.sendTextMessage("+918867479488", null, message,
								null, null);
					} else {
						tts.speak("Location Service not enabled",
								TextToSpeech.QUEUE_FLUSH, null);
					}
					return;
				}
				else if(addr.contains("take me to"))
				{
				addr=addr.substring(11);
				Toast.makeText(getApplicationContext(), addr, Toast.LENGTH_LONG)
						.show();
				tts.speak("Do you want to go to " + addr,
						TextToSpeech.QUEUE_FLUSH, null);
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}

				Intent intSpeech = new Intent(
						RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
				intSpeech.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
						"en-US");

				try {
					startActivityForResult(intSpeech, 8);
				} catch (ActivityNotFoundException a) {
					Toast t = Toast.makeText(getApplicationContext(),
							"Opps! Your device doesn't support Speech to Text",
							Toast.LENGTH_SHORT);
					t.show();
				}
			}
		  }
			break;
		}
		case 8: {
			if (resultCode == RESULT_OK && null != data) {
				ArrayList<String> text = data
						.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
				String ack;
				ack = text.get(0).toString();
				Toast.makeText(getApplicationContext(), ack, Toast.LENGTH_LONG)
						.show();
				if (ack.equalsIgnoreCase("yes")
						|| ack.equalsIgnoreCase("confirm")
						|| ack.equalsIgnoreCase("ok")) {
					StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
							.permitAll().build();
					StrictMode.setThreadPolicy(policy);
					int status = getLatLong(addr);
					Toast.makeText(getApplicationContext(), dlat + " " + dlng,
							Toast.LENGTH_SHORT).show();
					if (status == 0) {
						tts.speak("Sorry, Destination not found",
								TextToSpeech.QUEUE_FLUSH, null);
						break;
					}
					check();
					if (nav == true) {
						tts.speak("Taking you to" + addr,
								TextToSpeech.QUEUE_FLUSH, null);
						Toast.makeText(getApplicationContext(),
								"Lat: " + dlat + " Lang:" + dlng,
								Toast.LENGTH_LONG).show();
						try {

							Intent in = new Intent(Intent.ACTION_VIEW,
									Uri.parse("google.navigation:q=" + dlat
											+ "," + dlng + "&mode=w"));
							in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
							startActivity(in);
						} catch (Exception e) {
							Toast.makeText(getApplicationContext(),
									e.toString(), Toast.LENGTH_LONG).show();
						}
					} else
						tts.speak(
								"Sorry, Destination cannot be reached by walk",
								TextToSpeech.QUEUE_FLUSH, null);
				} else if ((ack.equalsIgnoreCase("no"))
						|| ack.equalsIgnoreCase("cancel")) {
					tts.speak("To navigate, toggle the switch again",
							TextToSpeech.QUEUE_FLUSH, null);
				} else {
					tts.speak(
							"I'm sorry, I din't get you! toggle the switch again",
							TextToSpeech.QUEUE_FLUSH, null);
				}
			}
			break;
		}
		}
	}

	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		// TODO Auto-generated method stub

		if (btAdapter.isDiscovering()) {
			btAdapter.cancelDiscovery();
		}

		BluetoothDevice selectedDevice = devices.get(arg2);
		ConnectThread connect = new ConnectThread(selectedDevice);
		connect.start();
		Log.i(tag, "in click listener");

		if (!listAdapter.getItem(arg2).contains("Paired")) {
			Toast.makeText(getApplicationContext(),
					"Connecting to an unpaired device", Toast.LENGTH_LONG)
					.show();
		}
	}

	private class ConnectThread extends Thread {

		private final BluetoothSocket mmSocket;
		private final BluetoothDevice mmDevice;

		public ConnectThread(BluetoothDevice device) {
			// Use a temporary object that is later assigned to mmSocket,
			// because mmSocket is final
			BluetoothSocket tmp = null;
			mmDevice = device;
			Log.i(tag, "construct");
			// Get a BluetoothSocket to connect with the given BluetoothDevice
			try {
				// MY_UUID is the app's UUID string, also used by the server
				// code
				tmp = mmDevice.createRfcommSocketToServiceRecord(MY_UUID);
			} catch (IOException e) {
				Log.i(tag, "get socket failed");

			}
			mmSocket = tmp;
		}

		public void run() {
			// Cancel discovery because it will slow down the connection
			btAdapter.cancelDiscovery();
			Log.i(tag, "connect - run");
			try {
				// Connect the device through the socket. This will block
				// until it succeeds or throws an exception
				mmSocket.connect();
				Log.i(tag, "connect - succeeded");
			} catch (IOException connectException) {
				Log.i(tag, "connect failed");
				// Unable to connect; close the socket and get out
				try {
					mmSocket.close();
				} catch (IOException closeException) {
					this.cancel();
				}
				return;
			}

			// Do work to manage the connection (in a separate thread)

			ts.obtainMessage(SUCCESS_CONNECT, mmSocket).sendToTarget();
		}

		/** Will cancel an in-progress connection, and close the socket */
		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
				Log.d("Exception", e.toString());
			}
		}
	}

	private class ConnectedThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;

		public ConnectedThread(BluetoothSocket socket) {
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			// Get the input and output streams, using temp objects because
			// member streams are final
			try {
				tmpIn = mmSocket.getInputStream();
				tmpOut = mmSocket.getOutputStream();
			} catch (IOException e) {
			}

			mmInStream = tmpIn;
			mmOutStream = tmpOut;
			flag = 1;
		}

		@SuppressLint("NewApi")
		public void run() {
			// buffer store for the stream
			// bytes returned from read()
			int f = 1;
			char ch;
			String r;
			// Keep listening to the InputStream until an exception occurs
			while (true) {
				try {
					// Read from the InputStream
					// Send the obtained bytes to the UI activity
					// Thread.sleep(1000);
					if (f == 1) {

						clear();
						f++;
					}
					r = "";
					if (mmInStream.available() > 0) {
						while ((ch = (char) mmInStream.read()) != '\n')
							r += ch;
						ts.obtainMessage(MESSAGE_READ, r).sendToTarget();
					} else
						mmOutStream.write("8".getBytes());
				} catch (IOException e) {
					Log.d("Exception", e.toString());
					try {
						mmSocket.close();
						mmSocket.close();
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}

					tts.speak("Bluetooth Device disconnected",
							TextToSpeech.QUEUE_FLUSH, null);
					/*
					 * Toast.makeText(getApplicationContext(), e.toString(),
					 * Toast.LENGTH_SHORT).show();
					 */
					flag = 0;
					break;

				}
			}
		}

		public void clear() {
			try {
				Log.d("debugging", "ino");
				while (mmInStream.available() > 0) {
					mmInStream.read();
					Log.d("debugging", "in");
				}
			} catch (Exception e) {
				Log.i("Exception", e.toString());
			}
		}

		/*
		 * Call this from the main activity to shutdown the connection public
		 * void cancel() { try { mmSocket.close(); } catch (IOException e)
		 * {this.cancel(); } }
		 */
	}

	class check extends Thread {
		int f = 1;

		public void run() {
			while (true) {
				if (flag == 0) {
					startDiscovery();
					try {
						Thread.sleep(3000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
	}

}
