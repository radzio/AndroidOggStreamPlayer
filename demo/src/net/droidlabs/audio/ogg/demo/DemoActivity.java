package net.droidlabs.audio.ogg.demo;

import android.app.Activity;
import android.os.Bundle;
import android.os.StrictMode;
import net.droidlabs.audio.ogg.OggStreamPlayer;

public class DemoActivity extends Activity
{
    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);


        //do not kill me I will fix this later ;-)
        if (android.os.Build.VERSION.SDK_INT > 9)
        {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        OggStreamPlayer player = new OggStreamPlayer("http://78.28.48.14:8000/stream.ogg");
        player.start();
    }
}
