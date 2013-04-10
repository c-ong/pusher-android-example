package com.pusher.android.example;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Switch;
import android.widget.TextView;

import com.pusher.client.Pusher;
import com.pusher.client.PusherOptions;
import com.pusher.client.channel.Channel;
import com.pusher.client.channel.ChannelEventListener;
import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.ConnectionStateChange;
import com.pusher.client.util.HttpAuthorizer;

public class MainActivity extends Activity
	implements ConnectionEventListener, ChannelEventListener {
	
	private static final String PUBLIC_CHANNEL_NAME = "a_channel";
	
	private Pusher pusher;
	private Channel publicChannel;
	
	private ConnectionState targetState = ConnectionState.DISCONNECTED;
	private static final ScheduledExecutorService connectionAttemptsWorker = Executors.newSingleThreadScheduledExecutor();
	private int failedConnectionAttempts = 0;
	private static int MAX_RETRIES = 10;
	
	private Switch connectionSwitch;
	private TextView logTextView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO: login token?
		HttpAuthorizer authorizer = new HttpAuthorizer("http://www.leggetter.co.uk/pusher/pusher-examples/php/authentication/src/private_auth.php");
		PusherOptions options = new PusherOptions().setEncrypted(true).setAuthorizer(authorizer);
		pusher = new Pusher("8817c5eeccfb1ea2d1c6", options);
		
		// bind to all connection events
		pusher.getConnection().bind(ConnectionState.ALL, this);
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// Get view for logging
		logTextView = (TextView)this.findViewById(R.id.loggerText);
		
		bindToConnectionSwitch();
		
		log("Application running");
		
		publicChannel = pusher.subscribe(PUBLIC_CHANNEL_NAME, this, "some_event");
	}
	
	// Connect/disconnect depending on switch state
	private void bindToConnectionSwitch() {
		connectionSwitch = (Switch)this.findViewById(R.id.connectSwitch);
		connectionSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton button, boolean checked) {
				targetState = (checked? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED );
				achieveExpectedConnectionState();
			}
		});
	}
	
	private void achieveExpectedConnectionState() {
		ConnectionState currentState = pusher.getConnection().getState();
		if(currentState == targetState) {
			// do nothing, we're there.
			failedConnectionAttempts = 0;
		}
		else if( targetState == ConnectionState.CONNECTED &&
						 failedConnectionAttempts == MAX_RETRIES ) {
			targetState = ConnectionState.DISCONNECTED;
			log( "failed to connect after " + failedConnectionAttempts + " attempts. Reconnection attempts stopped.");
		}
		else if( currentState == ConnectionState.DISCONNECTED &&
				     targetState == ConnectionState.CONNECTED ) {
			Runnable task = new Runnable() {
		    public void run() {
		      pusher.connect();
		    }
		  };
		  log("Connecting in " + failedConnectionAttempts + " seconds");
		  connectionAttemptsWorker.schedule(task, (failedConnectionAttempts), TimeUnit.SECONDS);
		  ++failedConnectionAttempts;
		}
		else if( currentState == ConnectionState.CONNECTED &&
						 targetState == ConnectionState.DISCONNECTED ) {
			pusher.disconnect();
		}
		else {
			// transitional state
		}
	}
	
	//ConnectionEventListener implementation
	public void onConnectionStateChange(ConnectionStateChange change) {
		String msg = String.format("Connection state changed from [%s] to [%s]",
				change.getPreviousState(), change.getCurrentState() );
		
		log( msg );
		
		achieveExpectedConnectionState();
	}

	public void onError(String message, String code, Exception e) {
		String msg = String.format("Connection error: [%s] [%s] [%s]", message, code, e);
		log(msg);
	}

	// ChannelEventListener implementation
	public void onEvent(String channelName, String eventName, String data) {
		String msg = String.format("Event received: [%s] [%s] [%s]", channelName, eventName, data);
		log( msg );
	}

	public void onSubscriptionSucceeded(String channelName) {
		String msg = String.format("Subscription succeeded for [%s]", channelName);
		log( msg );
	}
	
	// Logging helper method
	private void log(String msg) {
		LogTask task = new LogTask(logTextView, msg);
		task.execute();
	}

}

// Used for logging on the UI thread
class LogTask extends AsyncTask<Void, Void, Void> {
	
	TextView view;
	String msg;
	
	public LogTask(TextView view, String msg) {
		this.view = view;
		this.msg = msg;
	}

	@Override
	protected Void doInBackground(Void... args) {
		return null;
	}

	@Override
	protected void onPostExecute(Void result) {
		System.out.println(msg);
		
		String currentLog = view.getText().toString();
		String newLog = msg + "\n" + currentLog;
		view.setText(newLog);
		
		super.onPostExecute(result);
	}
	
}
