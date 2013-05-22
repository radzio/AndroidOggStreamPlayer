package net.droidlabs.audio.ogg.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import net.droidlabs.audio.ogg.OggStreamPlayer;

public class DemoActivity extends Activity
{
    private OggStreamPlayer player;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        player = new OggStreamPlayer();

    }

    public void playAsync(View view)
    {
        player.playAsync("http://78.28.48.14:8000/stream.ogg");
    }

    public void stop(View view)
    {
        player.stop();
    }
}
