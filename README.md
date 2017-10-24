# LogcatViewer library

### Feature:

- Priority filter
- Clear logcat
- Export as file 
- Floating window

### Integrate guide

1. Clone this library as a project module, add module dependence.

2. Add following code to your `AndroidManifest.xml`

   ```xml
   <application>
   	...  
   	<provider
   		android:name="android.support.v4.content.FileProvider"
   		android:authorities="${applicationId}.fileprovider"
   		android:grantUriPermissions="true"
   		android:exported="false">
   		<meta-data
   			android:name="android.support.FILE_PROVIDER_PATHS"
   			android:resource="@xml/logcat_filepaths" />
   	</provider>
   </application>
   ```


3. Add launch code in your code:

   ```java
   public void launchLogcatViewer() {
   	LogcatActivity.launch(getContext());
   }
   ```

   ​