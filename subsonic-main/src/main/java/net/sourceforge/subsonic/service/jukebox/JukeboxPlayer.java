/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package net.sourceforge.subsonic.service.jukebox;

import java.io.IOException;
import java.io.InputStream;

import net.sourceforge.subsonic.Logger;
import net.sourceforge.subsonic.domain.MusicFile;
import net.sourceforge.subsonic.domain.Playlist;
import net.sourceforge.subsonic.domain.Transcoding;
import net.sourceforge.subsonic.service.TranscodingService;

/**
 * @author Sindre Mehus
 * @version $Id$
 */
public class JukeboxPlayer implements AudioPlayer.Listener {

    private static final Logger LOG = Logger.getLogger(TranscodingService.class);

    private float gain = 0.5f;

    private AudioPlayer audioPlayer;
    private TranscodingService transcodingService;
    private Playlist playlist;

    public void play(Playlist playlist) throws Exception {
        this.playlist = playlist;
        playNext();
    }

    public synchronized void reset() {
        if (audioPlayer != null) {
            audioPlayer.reset();
        }
    }

    public synchronized float getGain() {
        return gain;
    }

    public synchronized void setGain(float gain) {
        this.gain = gain;
        if (audioPlayer != null) {
            audioPlayer.setGain(gain);
        }
    }

    public void stateChanged(AudioPlayer.State state) {
        if (playlist != null && state == AudioPlayer.State.COMPLETED) {
            playlist.next();
            playNext();
        }
    }

    private synchronized void playNext()  {
        try {

            if (audioPlayer != null) {
                audioPlayer.reset();
            } 

            MusicFile file = this.playlist.getCurrentFile();
            if (file != null) {
                TranscodingService.Parameters parameters = new TranscodingService.Parameters(file, null);
                // TODO
                parameters.setTranscoding(new Transcoding(null, null, null, null, "ffmpeg -i %s -v 0 -f au -", null, null));
                InputStream in = transcodingService.getTranscodedInputStream(parameters);
                audioPlayer = new AudioPlayer(in, this);
                audioPlayer.setGain(gain);
                audioPlayer.start();
            }
        } catch (Exception x) {
            LOG.error("Error in jukebox: " + x, x);
        }
    }

    public void setTranscodingService(TranscodingService transcodingService) {
        this.transcodingService = transcodingService;
    }
}
