reyna : Icelandic for "try"
=====
An android store and forward library for http post requests.
Reyna will store your requests and post them when there is a valid connection.

## Installation
Reyna is a standard android library and can be referenced as a aar (Android Archive) in your project.

## Android Manifest
You will need to add the following entries into your AndroidManifest.xml in order for reyna to have the correct permissions, services and receivers.

```xml
    <!-- Add these permissions to your manifest -->
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.WAKE_LOCK"/>
    
    <application android:label="YOUR APP NAME">
        
        ...
        
        <!-- Add these services and receiver to your application -->
        <service android:name="it.sii.reyna.services.StoreService" />
        <service android:name="it.sii.reyna.services.ForwardService" />
        <receiver android:name="it.sii.reyna.receivers.ForwardServiceReceiver">
            <intent-filter>
                <action android:name="android.net.conn.CONNECTIVITY_CHANGE" />
            </intent-filter>
        </receiver>
    </application>
```

## Usage


```java
	
	// Add any headers if required
	Header[] headers = new Header[] {
		new Header("Content-Type", "application/json"),
		new Header("myheader", "header content"),

		// gzip content when posting
		new Header("Content-Encoding", "gzip")
	};


	// Create the message to send
	Message message = new Message(
		new URI("http://server.tosendmessageto.com"),
		"body of post, probably JSON",
		headers);
		
	// (Optionally) set the maximum number of tries
	message.setNumberOfTries(100);	

	// Send the message to Reyna
	 Reyna.sendMessage(message, getActivity(), new Reyna.PostResponse() {
         @Override
         public void onPostSuccess(Long messageID) {
             // Success = "No permanent error, I took care of it"
         }

         @Override
         public void onPostError(String errorMessage) {
             // Error = "Permanent error, I will not even try to send it again"
         }
      }
```
Post is done with RestClient, and it works nicely with retry policies etc.
This fork of Reyna, in fact, uses the same concept of "transient" VS. "permanent" error.



