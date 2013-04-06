package net.droidlabs.audio.ogg;

/**
 * Created with IntelliJ IDEA.
 * User: Radek Piekarz
 * Date: 06.04.13
 * Time: 18:56
 */
public interface OggStreamPlayerCallback
{
    public void playerStarted();

    public void playerStopped();

    public void playerException(Throwable t);
}
