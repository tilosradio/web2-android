package pontezit.android.tilos.com.service;

interface IMediaPlaybackService
{
    void openStream(String path);
    int getQueuePosition();
    boolean isPlaying();
    void stop();
    void pause();
    void play();
    void prev();
    void next();
    long duration();
    long position();
    long seek(long pos);
    String getStreamType();
    String getReadableTime();
    String getShowName();
    long [] getQueue();
    void setQueuePosition(int index);
    String getPath();
    int getAudioSessionId();
    
    // custom methods
    int getTrackId();
    String getMediaUri();
    String getTrackNumber();
    void setSleepTimerMode(int sleepmode);
    int getSleepTimerMode();
}
