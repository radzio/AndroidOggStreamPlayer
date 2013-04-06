package net.droidlabs.audio.ogg;

/*
 * Based on Jon Kristensen JOrbis tutorial
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;
import com.jcraft.jogg.Packet;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownServiceException;


/**
 * The <code>OggStreamPlayer</code> thread class will simply download and play
 * OGG media. All you need to do is supply a valid URL as the first argument.
 */
public class OggStreamPlayer
{

    public static final String TAG = OggStreamPlayer.class.getSimpleName();

    // If you wish to debug this source, please set the variable below to true.
    private final boolean debugMode = true;

    /*
     * URLConnection and InputStream objects so that we can open a connection to
     * the media file.
     */
    private URLConnection urlConnection = null;
    private InputStream inputStream = null;

    /*
     * We need a buffer, it's size, a count to know how many bytes we have read
     * and an index to keep track of where we are. This is standard networking
     * stuff used with read().
     */
    byte[] buffer = null;
    int bufferSize = 2048;
    int count = 0;
    int index = 0;

    /*
     * JOgg and JOrbis require fields for the converted buffer. This is a buffer
     * that is modified in regards to the number of audio channels. Naturally,
     * it will also need a size.
     */
    byte[] convertedBuffer;
    int convertedBufferSize;

    // A three-dimensional an array with PCM information.
    private float[][][] pcmInfo;

    // The index for the PCM information.
    private int[] pcmIndex;

    // Here are the four required JOgg objects...
    private Packet joggPacket;
    private Page joggPage;
    private StreamState joggStreamState;
    private SyncState joggSyncState;

    // ... followed by the four required JOrbis objects.
    private DspState jorbisDspState;
    private Block jorbisBlock;
    private Comment jorbisComment;
    private Info jorbisInfo;
    private AudioTrack track;
    private boolean isStopped = false;

    private OggStreamPlayerCallback playerCallback;

    public OggStreamPlayer(OggStreamPlayerCallback playerCallback)
    {
        this.playerCallback = playerCallback;
    }

    public OggStreamPlayer()
    {
        this(null);
    }

    /**
     * Given a string, <code>getUrl()</code> will return an URL object.
     *
     * @param url the URL to be opened
     * @return the URL object
     */

    public void play(String url)
    {
        play(getUrl(url));
    }

    public void play(URL url)
    {
        isStopped = false;

        if (playerCallback != null)
        {
            playerCallback.playerStarted();
        }

        joggStreamState = new StreamState();
        joggSyncState = new SyncState();

        // ... followed by the four required JOrbis objects.
        jorbisDspState = new DspState();
        jorbisBlock = new Block(jorbisDspState);
        jorbisComment = new Comment();
        jorbisInfo = new Info();


        joggPacket = new Packet();
        joggPage = new Page();

        configureInputStream(url);

        playImpl();
    }

    public void playAsync(String url)
    {
        playAsync(getUrl(url));
    }

    public void playAsync(final URL url)
    {
        new Thread(new Runnable()
        {
            public void run()
            {
                try
                {
                    play(url);
                }
                catch (Exception e)
                {
                    Log.e(TAG, "playAsync():", e);

                    if (playerCallback != null)
                    {
                        playerCallback.playerException( e );
                    }
                }
            }
        }).start();
    }

    public void stop()
    {
        isStopped = true;
    }

    private URL getUrl(String pUrl)
    {
        URL url = null;

        try
        {
            url = new URL(pUrl);
        }
        catch (MalformedURLException exception)
        {
            Log.e(TAG, "Malformed \"url\" parameter: \"" + pUrl + "\"");
        }

        return url;
    }

    /**
     * Sets the <code>inputStream</code> object by taking an URL, opens a
     * connection to it and get the <code>InputStream</code>.
     *
     * @param pUrl the url to the media file
     */
    private void configureInputStream(URL pUrl)
    {
        // Try to open a connection to the URL.
        try
        {
            urlConnection = pUrl.openConnection();
        }
        catch (UnknownServiceException exception)
        {
            Log.e(TAG, "The protocol does not support input.");
        }
        catch (IOException exception)
        {
            Log.e(TAG, "An I/O error occoured while trying create the "
                    + "URL connection.");
        }

        // If we have a connection, try to create an input stream.
        if (urlConnection != null)
        {
            try
            {
                inputStream = urlConnection.getInputStream();
            }
            catch (IOException exception)
            {
                Log.e(TAG, "An I/O error occoured while trying to get an "
                        + "input stream from the URL.");
                //Log.e(TAG, exception);
            }
        }
    }

    /**
     * This method is probably easiest understood by looking at the body.
     * However, it will - if no problems occur - call methods to initialize the
     * JOgg JOrbis libraries, read the header, initialize the sound system, read
     * the body of the stream and clean up.
     */
    private void playImpl()
    {
        // Check that we got an InputStream.
        if (inputStream == null)
        {
            Log.e(TAG, "We don't have an input stream and therefor cannot continue.");
            return;
        }

        // Initialize JOrbis.
        initializeJOrbis();

		/*
         * If we can read the header, we try to inialize the sound system. If we
		 * could initialize the sound system, we try to read the body.
		 */
        if (readHeader())
        {
            if (initializeSound())
            {
                readBody();
            }
        }

        // Afterwards, we clean up.
        cleanUp();

        if (playerCallback != null)
        {
            playerCallback.playerStopped();
        }

    }

    /**
     * Initializes JOrbis. First, we initialize the <code>SyncState</code>
     * object. After that, we prepare the <code>SyncState</code> buffer. Then
     * we "initialize" our buffer, taking the data in <code>SyncState</code>.
     */
    private void initializeJOrbis()
    {
        debugOutput("Initializing JOrbis.");

        // Initialize SyncState
        joggSyncState.init();

        // Prepare the to SyncState internal buffer
        joggSyncState.buffer(bufferSize);

		/*
         * Fill the buffer with the data from SyncState's internal buffer. Note
		 * how the size of this new buffer is different from bufferSize.
		 */
        buffer = joggSyncState.data;

        debugOutput("Done initializing JOrbis.");
    }

    /**
     * This method reads the header of the stream, which consists of three
     * packets.
     *
     * @return true if the header was successfully read, false otherwise
     */
    private boolean readHeader()
    {
        debugOutput("Starting to read the header.");

		/*
		 * Variable used in loops below. While we need more data, we will
		 * continue to read from the InputStream.
		 */
        boolean needMoreData = true;

		/*
		 * We will read the first three packets of the header. We start off by
		 * defining packet = 1 and increment that value whenever we have
		 * successfully read another packet.
		 */
        int packet = 1;

		/*
		 * While we need more data (which we do until we have read the three
		 * header packets), this loop reads from the stream and has a big
		 * <code>switch</code> statement which does what it's supposed to do in
		 * regards to the current packet.
		 */
        while (needMoreData)
        {
            // Read from the InputStream.
            try
            {
                count = inputStream.read(buffer, index, bufferSize);
            }
            catch (IOException exception)
            {
                Log.e(TAG, "Could not read from the input stream.");

            }

            // We let SyncState know how many bytes we read.
            joggSyncState.wrote(count);

			/*
			 * We want to read the first three packets. For the first packet, we
			 * need to initialize the StreamState object and a couple of other
			 * things. For packet two and three, the procedure is the same: we
			 * take out a page, and then we take out the packet.
			 */
            switch (packet)
            {
                // The first packet.
                case 1:
                {
                    // We take out a page.
                    switch (joggSyncState.pageout(joggPage))
                    {
                        // If there is a hole in the data, we must exit.
                        case -1:
                        {
                            Log.e(TAG, "There is a hole in the first packet data.");
                            return false;
                        }

                        // If we need more data, we break to get it.
                        case 0:
                        {
                            break;
                        }

						/*
						 * We got where we wanted. We have successfully read the
						 * first packet, and we will now initialize and reset
						 * StreamState, and initialize the Info and Comment
						 * objects. Afterwards we will check that the page
						 * doesn't contain any errors, that the packet doesn't
						 * contain any errors and that it's Vorbis data.
						 */
                        case 1:
                        {
                            // Initializes and resets StreamState.
                            joggStreamState.init(joggPage.serialno());
                            joggStreamState.reset();

                            // Initializes the Info and Comment objects.
                            jorbisInfo.init();
                            jorbisComment.init();

                            // Check the page (serial number and stuff).
                            if (joggStreamState.pagein(joggPage) == -1)
                            {
                                Log.e(TAG, "We got an error while reading the first header page.");
                                return false;
                            }

							/*
							 * Try to extract a packet. All other return values
							 * than "1" indicates there's something wrong.
							 */
                            if (joggStreamState.packetout(joggPacket) != 1)
                            {
                                Log.e(TAG, "We got an error while reading the first header packet.");
                                return false;
                            }

							/*
							 * We give the packet to the Info object, so that it
							 * can extract the Comment-related information,
							 * among other things. If this fails, it's not
							 * Vorbis data.
							 */
                            if (jorbisInfo.synthesis_headerin(jorbisComment, joggPacket) < 0)
                            {
                                Log.e(TAG, "We got an error while interpreting the first packet. Apparantly, it's not Vorbis data.");
                                return false;
                            }

                            // We're done here, let's increment "packet".
                            packet++;
                            break;
                        }
                    }

					/*
					 * Note how we are NOT breaking here if we have proceeded to
					 * the second packet. We don't want to read from the input
					 * stream again if it's not necessary.
					 */
                    if (packet == 1) break;
                }

                // The code for the second and third packets follow.
                case 2:
                case 3:
                {
                    // Try to get a new page again.
                    switch (joggSyncState.pageout(joggPage))
                    {
                        // If there is a hole in the data, we must exit.
                        case -1:
                        {
                            Log.e(TAG, "There is a hole in the second or third packet data.");
                            return false;
                        }

                        // If we need more data, we break to get it.
                        case 0:
                        {
                            break;
                        }

						/*
						 * Here is where we take the page, extract a packet and
						 * and (if everything goes well) give the information to
						 * the Info and Comment objects like we did above.
						 */
                        case 1:
                        {
                            // Share the page with the StreamState object.
                            joggStreamState.pagein(joggPage);

							/*
							 * Just like the switch(...packetout...) lines
							 * above.
							 */
                            switch (joggStreamState.packetout(joggPacket))
                            {
                                // If there is a hole in the data, we must exit.
                                case -1:
                                {
                                    Log.e(TAG, "There is a hole in the first packet data.");
                                    return false;
                                }

                                // If we need more data, we break to get it.
                                case 0:
                                {
                                    break;
                                }

                                // We got a packet, let's process it.
                                case 1:
                                {
									/*
									 * Like above, we give the packet to the
									 * Info and Comment objects.
									 */
                                    jorbisInfo.synthesis_headerin(jorbisComment, joggPacket);

                                    // Increment packet.
                                    packet++;

                                    if (packet == 4)
                                    {
										/*
										 * There is no fourth packet, so we will
										 * just end the loop here.
										 */
                                        needMoreData = false;
                                    }

                                    break;
                                }
                            }

                            break;
                        }
                    }

                    break;
                }
            }

            // We get the new index and an updated buffer.
            index = joggSyncState.buffer(bufferSize);
            buffer = joggSyncState.data;

			/*
			 * If we need more data but can't get it, the stream doesn't contain
			 * enough information.
			 */
            if (count == 0 && needMoreData)
            {
                Log.e(TAG, "Not enough header data was supplied.");
                return false;
            }
        }

        debugOutput("Finished reading the header.");

        return true;
    }

    /**
     * This method starts the sound system. It starts with initializing the
     * <code>DspState</code> object, after which it sets up the
     * <code>Block</code> object. Last but not least, it opens a line to the
     * source data line.
     *
     * @return true if the sound system was successfully started, false
     *         otherwise
     */
    private boolean initializeSound()
    {
        debugOutput("Initializing the sound system.");

        // This buffer is used by the decoding method.
        convertedBufferSize = bufferSize * 2;
        convertedBuffer = new byte[convertedBufferSize];

        // Initializes the DSP synthesis.
        jorbisDspState.synthesis_init(jorbisInfo);

        // Make the Block object aware of the DSP.
        jorbisBlock.init(jorbisDspState);

        // Wee need to know the channels and rate.
        int channels = jorbisInfo.channels;
        int rate = jorbisInfo.rate;

        int minimumBufferSize;

        if (channels == 1)
        {
            minimumBufferSize = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            track = new AudioTrack(AudioManager.STREAM_MUSIC,
                    rate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize < minimumBufferSize ? minimumBufferSize : bufferSize ,
                    AudioTrack.MODE_STREAM);
        }
        else
        {
            minimumBufferSize = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            track = new AudioTrack(AudioManager.STREAM_MUSIC,
                    rate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize < minimumBufferSize ? minimumBufferSize : bufferSize,
                    AudioTrack.MODE_STREAM);
        }


        track.play();

		/*
		 * We create the PCM variables. The index is an array with the same
		 * length as the number of audio channels.
		 */
        pcmInfo = new float[1][][];
        pcmIndex = new int[jorbisInfo.channels];

        debugOutput("Done initializing the sound system.");

        return true;
    }

    /**
     * This method reads the entire stream body. Whenever it extracts a packet,
     * it will decode it by calling <code>decodeCurrentPacket()</code>.
     */
    private void readBody()
    {
        debugOutput("Reading the body.");

		/*
		 * Variable used in loops below, like in readHeader(). While we need
		 * more data, we will continue to read from the InputStream.
		 */
        boolean needMoreData = true;

        while (needMoreData && isStopped == false)
        {
            switch (joggSyncState.pageout(joggPage))
            {
                // If there is a hole in the data, we just proceed.
                case -1:
                {
                    debugOutput("There is a hole in the data. We proceed.");
                }

                // If we need more data, we break to get it.
                case 0:
                {
                    break;
                }

                // If we have successfully checked out a page, we continue.
                case 1:
                {
                    // Give the page to the StreamState object.
                    joggStreamState.pagein(joggPage);

                    // If granulepos() returns "0", we don't need more data.
                    if (joggPage.granulepos() == 0)
                    {
                        needMoreData = false;
                        break;
                    }

                    // Here is where we process the packets.
                    processPackets:
                    while (true)
                    {
                        switch (joggStreamState.packetout(joggPacket))
                        {
                            // Is it a hole in the data?
                            case -1:
                            {
                                debugOutput("There is a hole in the data, we continue though.");
                            }

                            // If we need more data, we break to get it.
                            case 0:
                            {
                                break processPackets;
                            }

							/*
							 * If we have the data we need, we decode the
							 * packet.
							 */
                            case 1:
                            {
                                decodeCurrentPacket();
                            }
                        }
                    }

					/*
					 * If the page is the end-of-stream, we don't need more
					 * data.
					 */
                    if (joggPage.eos() != 0)
                        needMoreData = false;
                }
            }

            // If we need more data...
            if (needMoreData)
            {
                // We get the new index and an updated buffer.
                index = joggSyncState.buffer(bufferSize);
                buffer = joggSyncState.data;

                // Read from the InputStream.
                try
                {
                    count = inputStream.read(buffer, index, bufferSize);
                }
                catch (Exception e)
                {

                    return;
                }

                // We let SyncState know how many bytes we read.
                joggSyncState.wrote(count);

                // There's no more data in the stream.
                if (count == 0) needMoreData = false;
            }
        }
        debugOutput("Done reading the body.");
    }

    /**
     * A clean-up method, called when everything is finished. Clears the
     * JOgg/JOrbis objects and closes the <code>InputStream</code>.
     */
    private void cleanUp()
    {
        debugOutput("Cleaning up.");

        // Clear the necessary JOgg/JOrbis objects.
        joggStreamState.clear();
        jorbisBlock.clear();
        jorbisDspState.clear();
        jorbisInfo.clear();
        joggSyncState.clear();

        track.stop();

        urlConnection = null;

        // Closes the stream.
        try
        {
            if (inputStream != null)
                inputStream.close();
            inputStream = null;
        }
        catch (Exception e)
        {
        }


        buffer = null;
        bufferSize = 2048;

        count = 0;
        index = 0;

        convertedBuffer = null;
        convertedBufferSize = 0;

        pcmInfo = null;
        pcmIndex = null;

        debugOutput("Done cleaning up.");
    }

    /**
     * Decodes the current packet and sends it to the audio output line.
     */
    private void decodeCurrentPacket()
    {

        int samples;

        // Check that the packet is a audio data packet etc.
        if (jorbisBlock.synthesis(joggPacket) == 0)
        {
            // Give the block to the DspState object.
            jorbisDspState.synthesis_blockin(jorbisBlock);
        }

        // We need to know how many samples to process.
        int range;

		/*
		 * Get the PCM information and count the samples. And while these
		 * samples are more than zero...
		 */
        while ((samples = jorbisDspState.synthesis_pcmout(pcmInfo, pcmIndex))
                > 0)
        {
            // We need to know for how many samples we are going to process.
            if (samples < convertedBufferSize)
            {
                range = samples;
            }
            else
            {
                range = convertedBufferSize;
            }

            // For each channel...
            for (int i = 0; i < jorbisInfo.channels; i++)
            {
                int sampleIndex = i * 2;

                // For every sample in our range...
                for (int j = 0; j < range; j++)
                {
					/*
					 * Get the PCM value for the channel at the correct
					 * position.
					 */
                    int value = (int) (pcmInfo[0][i][pcmIndex[i] + j] * 32767);

					/*
					 * We make sure our value doesn't exceed or falls below
					 * +-32767.
					 */
                    if (value > 32767)
                    {
                        value = 32767;
                    }
                    if (value < -32768)
                    {
                        value = -32768;
                    }

					/*
					 * It the value is less than zero, we bitwise-or it with
					 * 32768 (which is 1000000000000000 = 10^15).
					 */
                    if (value < 0) value = value | 32768;

					/*
					 * Take our value and split it into two, one with the last
					 * byte and one with the first byte.
					 */
                    convertedBuffer[sampleIndex] = (byte) (value);
                    convertedBuffer[sampleIndex + 1] = (byte) (value >>> 8);

					/*
					 * Move the sample index forward by two (since that's how
					 * many values we get at once) times the number of channels.
					 */
                    sampleIndex += 2 * (jorbisInfo.channels);
                }
            }

            // Write the buffer to the audio output line.

            track.write(convertedBuffer, 0, 2 * jorbisInfo.channels * range);

            jorbisDspState.synthesis_read(range);
        }
    }

    /**
     * This method is being called internally to output debug information
     * whenever that is wanted.
     *
     * @param output the debug output information
     */
    private void debugOutput(String output)
    {
        if (debugMode)
        {
            Log.d(TAG, "Debug: " + output);
        }
    }
    
    public void setPlayerCallback(OggStreamPlayerCallback playerCallback)
    {
        this.playerCallback = playerCallback;
    }
}