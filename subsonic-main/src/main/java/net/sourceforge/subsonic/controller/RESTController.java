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
package net.sourceforge.subsonic.controller;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.multiaction.MultiActionController;

import net.sourceforge.subsonic.Logger;
import net.sourceforge.subsonic.ajax.ChatService;
import net.sourceforge.subsonic.ajax.LyricsInfo;
import net.sourceforge.subsonic.ajax.LyricsService;
import net.sourceforge.subsonic.command.UserSettingsCommand;
import net.sourceforge.subsonic.domain.MusicFile;
import net.sourceforge.subsonic.domain.MusicFolder;
import net.sourceforge.subsonic.domain.MusicIndex;
import net.sourceforge.subsonic.domain.Player;
import net.sourceforge.subsonic.domain.PlayerTechnology;
import net.sourceforge.subsonic.domain.Playlist;
import net.sourceforge.subsonic.domain.RandomSearchCriteria;
import net.sourceforge.subsonic.domain.SearchCriteria;
import net.sourceforge.subsonic.domain.SearchResult;
import net.sourceforge.subsonic.domain.TranscodeScheme;
import net.sourceforge.subsonic.domain.TransferStatus;
import net.sourceforge.subsonic.domain.User;
import net.sourceforge.subsonic.domain.UserSettings;
import net.sourceforge.subsonic.service.JukeboxService;
import net.sourceforge.subsonic.service.MusicFileService;
import net.sourceforge.subsonic.service.PlayerService;
import net.sourceforge.subsonic.service.PlaylistService;
import net.sourceforge.subsonic.service.SearchService;
import net.sourceforge.subsonic.service.SecurityService;
import net.sourceforge.subsonic.service.SettingsService;
import net.sourceforge.subsonic.service.StatusService;
import net.sourceforge.subsonic.service.TranscodingService;
import net.sourceforge.subsonic.service.LuceneSearchService;
import net.sourceforge.subsonic.service.AudioScrobblerService;
import net.sourceforge.subsonic.util.StringUtil;
import net.sourceforge.subsonic.util.XMLBuilder;

import static net.sourceforge.subsonic.util.XMLBuilder.Attribute;

/**
 * Multi-controller used for the REST API.
 * <p/>
 * For documentation, please refer to api.jsp.
 *
 * @author Sindre Mehus
 */
public class RESTController extends MultiActionController {

    private static final Logger LOG = Logger.getLogger(RESTController.class);

    private SettingsService settingsService;
    private SecurityService securityService;
    private PlayerService playerService;
    private MusicFileService musicFileService;
    private TranscodingService transcodingService;
    private DownloadController downloadController;
    private CoverArtController coverArtController;
    private UserSettingsController userSettingsController;
    private LeftController leftController;
    private HomeController homeController;
    private StatusService statusService;
    private StreamController streamController;
    private SearchService searchService;
    private PlaylistService playlistService;
    private ChatService chatService;
    private LyricsService lyricsService;
    private net.sourceforge.subsonic.ajax.PlaylistService playlistControlService;
    private JukeboxService jukeboxService;
    private AudioScrobblerService audioScrobblerService;

    public void ping(HttpServletRequest request, HttpServletResponse response) throws Exception {
        XMLBuilder builder = createXMLBuilder(request, response, true).endAll();
        response.getWriter().print(builder);
    }

    public void getLicense(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        XMLBuilder builder = createXMLBuilder(request, response, true);

        String email = settingsService.getLicenseEmail();
        String key = settingsService.getLicenseCode();
        Date date = settingsService.getLicenseDate();
        boolean valid = settingsService.isLicenseValid();

        List<Attribute> attributes = new ArrayList<Attribute>();
        attributes.add(new Attribute("valid", valid));
        if (valid) {
            attributes.add(new Attribute("email", email));
            attributes.add(new Attribute("key", key));
            attributes.add(new Attribute("date", StringUtil.toISO8601(date)));
        }

        builder.add("license", attributes, true);
        builder.endAll();
        response.getWriter().print(builder);
    }

    public void getMusicFolders(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        XMLBuilder builder = createXMLBuilder(request, response, true);
        builder.add("musicFolders", false);

        for (MusicFolder musicFolder : settingsService.getAllMusicFolders()) {
            List<Attribute> attributes = new ArrayList<Attribute>();
            attributes.add(new Attribute("id", musicFolder.getId()));
            if (musicFolder.getName() != null) {
                attributes.add(new Attribute("name", musicFolder.getName()));
            }
            builder.add("musicFolder", attributes, true);
        }
        builder.endAll();
        response.getWriter().print(builder);
    }

    public void getIndexes(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        XMLBuilder builder = createXMLBuilder(request, response, true);

        long ifModifiedSince = ServletRequestUtils.getLongParameter(request, "ifModifiedSince", 0L);
        long lastModified = leftController.getLastModified(request);

        if (lastModified <= ifModifiedSince) {
            builder.endAll();
            response.getWriter().print(builder);
            return;
        }

        builder.add("indexes", "lastModified", lastModified, false);

        List<MusicFolder> musicFolders = settingsService.getAllMusicFolders();
        Integer musicFolderId = ServletRequestUtils.getIntParameter(request, "musicFolderId");
        if (musicFolderId != null) {
            for (MusicFolder musicFolder : musicFolders) {
                if (musicFolderId.equals(musicFolder.getId())) {
                    musicFolders = Arrays.asList(musicFolder);
                    break;
                }
            }
        }

        List<MusicFile> shortcuts = leftController.getShortcuts(musicFolders, settingsService.getShortcutsAsArray());
        for (MusicFile shortcut : shortcuts) {
            builder.add("shortcut", true,
                    new Attribute("name", shortcut.getName()),
                    new Attribute("id", StringUtil.utf8HexEncode(shortcut.getPath())));
        }

        SortedMap<MusicIndex, SortedSet<MusicIndex.Artist>> indexedArtists = leftController.getCacheEntry(musicFolders, lastModified).getIndexedArtists();

        for (Map.Entry<MusicIndex, SortedSet<MusicIndex.Artist>> entry : indexedArtists.entrySet()) {
            builder.add("index", "name", entry.getKey().getIndex(), false);

            for (MusicIndex.Artist artist : entry.getValue()) {
                for (MusicFile musicFile : artist.getMusicFiles()) {
                    if (musicFile.isDirectory()) {
                        builder.add("artist", true,
                                new Attribute("name", artist.getName()),
                                new Attribute("id", StringUtil.utf8HexEncode(musicFile.getPath())));
                    }
                }
            }
            builder.end();
        }
        builder.endAll();
        response.getWriter().print(builder);
    }

    public void getMusicDirectory(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        Player player = playerService.getPlayer(request, response);

        MusicFile dir;
        try {
            String path = StringUtil.utf8HexDecode(ServletRequestUtils.getRequiredStringParameter(request, "id"));
            dir = musicFileService.getMusicFile(path);
        } catch (Exception x) {
            LOG.warn("Error in REST API.", x);
            error(request, response, ErrorCode.GENERIC, getErrorMessage(x));
            return;
        }

        XMLBuilder builder = createXMLBuilder(request, response, true);
        builder.add("directory", false,
                new Attribute("id", StringUtil.utf8HexEncode(dir.getPath())),
                new Attribute("name", dir.getName()));

        File coverArt = musicFileService.getCoverArt(dir);

        for (MusicFile musicFile : dir.getChildren(true, true, true)) {
            List<Attribute> attributes = createAttributesForMusicFile(player, coverArt, musicFile);
            builder.add("child", attributes, true);
        }
        builder.endAll();
        response.getWriter().print(builder);
    }

    @Deprecated
    public void search(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        XMLBuilder builder = createXMLBuilder(request, response, true);
        Player player = playerService.getPlayer(request, response);

        String any = request.getParameter("any");
        String artist = request.getParameter("artist");
        String album = request.getParameter("album");
        String title = request.getParameter("title");

        StringBuilder query = new StringBuilder();
        if (any != null) {
            query.append(any).append(" ");
        }
        if (artist != null) {
            query.append(artist).append(" ");
        }
        if (album != null) {
            query.append(album).append(" ");
        }
        if (title != null) {
            query.append(title);
        }

        SearchCriteria criteria = new SearchCriteria();
        criteria.setQuery(query.toString().trim());
        criteria.setCount(ServletRequestUtils.getIntParameter(request, "count", 20));
        criteria.setOffset(ServletRequestUtils.getIntParameter(request, "offset", 0));

        SearchResult result = searchService.search(criteria, LuceneSearchService.IndexType.SONG);
        builder.add("searchResult", false,
                new Attribute("offset", result.getOffset()),
                new Attribute("totalHits", result.getTotalHits()));

        for (MusicFile musicFile : result.getMusicFiles()) {
            File coverArt = musicFileService.getCoverArt(musicFile.getParent());
            List<Attribute> attributes = createAttributesForMusicFile(player, coverArt, musicFile);
            builder.add("match", attributes, true);
        }
        builder.endAll();
        response.getWriter().print(builder);
    }

    public void search2(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        XMLBuilder builder = createXMLBuilder(request, response, true);
        Player player = playerService.getPlayer(request, response);

        builder.add("searchResult2", false);

        String query = request.getParameter("query");
        SearchCriteria criteria = new SearchCriteria();
        criteria.setQuery(StringUtils.trimToEmpty(query));
        criteria.setCount(ServletRequestUtils.getIntParameter(request, "artistCount", 20));
        criteria.setOffset(ServletRequestUtils.getIntParameter(request, "artistOffset", 0));
        SearchResult artists = searchService.search(criteria, LuceneSearchService.IndexType.ARTIST);
        for (MusicFile musicFile : artists.getMusicFiles()) {
            builder.add("artist", true,
                    new Attribute("name", musicFile.getName()),
                    new Attribute("id", StringUtil.utf8HexEncode(musicFile.getPath())));
        }

        criteria.setCount(ServletRequestUtils.getIntParameter(request, "albumCount", 20));
        criteria.setOffset(ServletRequestUtils.getIntParameter(request, "albumOffset", 0));
        SearchResult albums = searchService.search(criteria, LuceneSearchService.IndexType.ALBUM);
        for (MusicFile musicFile : albums.getMusicFiles()) {
            List<Attribute> attributes = createAttributesForMusicFile(player, null, musicFile);
            builder.add("album", attributes, true);
        }

        criteria.setCount(ServletRequestUtils.getIntParameter(request, "songCount", 20));
        criteria.setOffset(ServletRequestUtils.getIntParameter(request, "songOffset", 0));
        SearchResult songs = searchService.search(criteria, LuceneSearchService.IndexType.SONG);
        for (MusicFile musicFile : songs.getMusicFiles()) {
            File coverArt = musicFileService.getCoverArt(musicFile.getParent());
            List<Attribute> attributes = createAttributesForMusicFile(player, coverArt, musicFile);
            builder.add("song", attributes, true);
        }

        builder.endAll();
        response.getWriter().print(builder);
    }

    public void getPlaylists(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        XMLBuilder builder = createXMLBuilder(request, response, true);

        builder.add("playlists", false);

        for (File playlist : playlistService.getSavedPlaylists()) {
            String id = StringUtil.utf8HexEncode(playlist.getName());
            String name = FilenameUtils.getBaseName(playlist.getName());
            builder.add("playlist", true, new Attribute("id", id), new Attribute("name", name));
        }
        builder.endAll();
        response.getWriter().print(builder);
    }

    public void getPlaylist(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        Player player = playerService.getPlayer(request, response);

        XMLBuilder builder = createXMLBuilder(request, response, true);

        try {
            String id = StringUtil.utf8HexDecode(ServletRequestUtils.getRequiredStringParameter(request, "id"));
            File file = playlistService.getSavedPlaylist(id);
            if (file == null) {
                throw new Exception("Playlist not found.");
            }
            Playlist playlist = new Playlist();
            playlistService.loadPlaylist(playlist, id);

            builder.add("playlist", false, new Attribute("id", StringUtil.utf8HexEncode(playlist.getName())),
                    new Attribute("name", FilenameUtils.getBaseName(playlist.getName())));
            for (MusicFile musicFile : playlist.getFiles()) {
                File coverArt = musicFileService.getCoverArt(musicFile.getParent());
                List<Attribute> attributes = createAttributesForMusicFile(player, coverArt, musicFile);
                builder.add("entry", attributes, true);
            }
            builder.endAll();
            response.getWriter().print(builder);
        } catch (ServletRequestBindingException x) {
            error(request, response, ErrorCode.MISSING_PARAMETER, getErrorMessage(x));
        } catch (Exception x) {
            LOG.warn("Error in REST API.", x);
            error(request, response, ErrorCode.GENERIC, getErrorMessage(x));
        }
    }

    public void jukeboxControl(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request, true);

        User user = securityService.getCurrentUser(request);
        if (!user.isJukeboxRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to use jukebox.");
            return;
        }

        try {
            boolean returnPlaylist = false;
            String action = ServletRequestUtils.getRequiredStringParameter(request, "action");
            if ("start".equals(action)) {
                playlistControlService.doStart(request, response);
            } else if ("stop".equals(action)) {
                playlistControlService.doStop(request, response);
            } else if ("skip".equals(action)) {
                int index = ServletRequestUtils.getRequiredIntParameter(request, "index");
                playlistControlService.doSkip(request, response, index);
            } else if ("add".equals(action)) {
                String[] ids = ServletRequestUtils.getRequiredStringParameters(request, "id");
                List<String> paths = new ArrayList<String>(ids.length);
                for (String id : ids) {
                    paths.add(StringUtil.utf8HexDecode(id));
                }
                playlistControlService.doAdd(request, response, paths);
            } else if ("clear".equals(action)) {
                playlistControlService.doClear(request, response);
            } else if ("remove".equals(action)) {
                int index = ServletRequestUtils.getRequiredIntParameter(request, "index");
                playlistControlService.doRemove(request, response, index);
            } else if ("shuffle".equals(action)) {
                playlistControlService.doShuffle(request, response);
            } else if ("setGain".equals(action)) {
                float gain = ServletRequestUtils.getRequiredFloatParameter(request, "gain");
                jukeboxService.setGain(gain);
            } else if ("get".equals(action)) {
                returnPlaylist = true;
            }

            XMLBuilder builder = createXMLBuilder(request, response, true);

            if (returnPlaylist) {

                Player player = playerService.getPlayer(request, response);
                Playlist playlist = player.getPlaylist();
                Iterable<Attribute> attrs = Arrays.asList(new Attribute("currentIndex", playlist.getIndex()),
                        new Attribute("playing", playlist.getStatus() == Playlist.Status.PLAYING),
                        new Attribute("gain", jukeboxService.getGain()));
                builder.add("jukeboxPlaylist", attrs, false);
                for (MusicFile musicFile : playlist.getFiles()) {
                    File coverArt = musicFileService.getCoverArt(musicFile.getParent());
                    List<Attribute> attributes = createAttributesForMusicFile(player, coverArt, musicFile);
                    builder.add("entry", attributes, true);
                }
            }

            builder.endAll();
            response.getWriter().print(builder);

        } catch (ServletRequestBindingException x) {
            error(request, response, ErrorCode.MISSING_PARAMETER, getErrorMessage(x));
        } catch (Exception x) {
            LOG.warn("Error in REST API.", x);
            error(request, response, ErrorCode.GENERIC, getErrorMessage(x));
        }
    }

    public void createPlaylist(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request, true);

        User user = securityService.getCurrentUser(request);
        if (!user.isPlaylistRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to create playlists.");
            return;
        }

        try {

            String playlistId = request.getParameter("playlistId");
            String name = request.getParameter("name");
            if (playlistId == null && name == null) {
                error(request, response, ErrorCode.MISSING_PARAMETER, "Playlist ID or name must be specified.");
                return;
            }

            Playlist playlist = new Playlist();
            playlist.setName(playlistId != null ? StringUtil.utf8HexDecode(playlistId) : name);

            String[] ids = ServletRequestUtils.getStringParameters(request, "songId");
            for (String id : ids) {
                playlist.addFiles(true, musicFileService.getMusicFile(StringUtil.utf8HexDecode(id)));
            }
            playlistService.savePlaylist(playlist);

            XMLBuilder builder = createXMLBuilder(request, response, true);
            builder.endAll();
            response.getWriter().print(builder);

        } catch (ServletRequestBindingException x) {
            error(request, response, ErrorCode.MISSING_PARAMETER, getErrorMessage(x));
        } catch (Exception x) {
            LOG.warn("Error in REST API.", x);
            error(request, response, ErrorCode.GENERIC, getErrorMessage(x));
        }
    }

    public void deletePlaylist(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request, true);

        User user = securityService.getCurrentUser(request);
        if (!user.isPlaylistRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to delete playlists.");
            return;
        }

        try {
            String id = StringUtil.utf8HexDecode(ServletRequestUtils.getRequiredStringParameter(request, "id"));
            playlistService.deletePlaylist(id);

            XMLBuilder builder = createXMLBuilder(request, response, true);
            builder.endAll();
            response.getWriter().print(builder);

        } catch (ServletRequestBindingException x) {
            error(request, response, ErrorCode.MISSING_PARAMETER, getErrorMessage(x));
        } catch (Exception x) {
            LOG.warn("Error in REST API.", x);
            error(request, response, ErrorCode.GENERIC, getErrorMessage(x));
        }
    }

    public void getAlbumList(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        Player player = playerService.getPlayer(request, response);

        XMLBuilder builder = createXMLBuilder(request, response, true);
        builder.add("albumList", false);

        try {
            int size = ServletRequestUtils.getIntParameter(request, "size", 10);
            int offset = ServletRequestUtils.getIntParameter(request, "offset", 0);

            size = Math.max(0, Math.min(size, 500));
            offset = Math.max(0, Math.min(offset, 5000));

            String type = ServletRequestUtils.getRequiredStringParameter(request, "type");

            List<HomeController.Album> albums;
            if ("highest".equals(type)) {
                albums = homeController.getHighestRated(offset, size);
            } else if ("frequent".equals(type)) {
                albums = homeController.getMostFrequent(offset, size);
            } else if ("recent".equals(type)) {
                albums = homeController.getMostRecent(offset, size);
            } else if ("newest".equals(type)) {
                albums = homeController.getNewest(offset, size);
            } else {
                albums = homeController.getRandom(size);
            }

            for (HomeController.Album album : albums) {
                MusicFile musicFile = musicFileService.getMusicFile(album.getPath());
                File coverArt = null;
                if (album.getCoverArtPath() != null) {
                    coverArt = new File(album.getCoverArtPath());
                }
                List<Attribute> attributes = createAttributesForMusicFile(player, coverArt, musicFile);
                builder.add("album", attributes, true);
            }
            builder.endAll();
            response.getWriter().print(builder);
        } catch (ServletRequestBindingException x) {
            error(request, response, ErrorCode.MISSING_PARAMETER, getErrorMessage(x));
        } catch (Exception x) {
            LOG.warn("Error in REST API.", x);
            error(request, response, ErrorCode.GENERIC, getErrorMessage(x));
        }
    }

    public void getRandomSongs(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        Player player = playerService.getPlayer(request, response);

        XMLBuilder builder = createXMLBuilder(request, response, true);
        builder.add("randomSongs", false);

        try {
            int size = ServletRequestUtils.getIntParameter(request, "size", 10);
            size = Math.max(0, Math.min(size, 500));
            String genre = ServletRequestUtils.getStringParameter(request, "genre");
            Integer fromYear = ServletRequestUtils.getIntParameter(request, "fromYear");
            Integer toYear = ServletRequestUtils.getIntParameter(request, "toYear");
            Integer musicFolderId = ServletRequestUtils.getIntParameter(request, "musicFolderId");
            RandomSearchCriteria criteria = new RandomSearchCriteria(size, genre, fromYear, toYear, musicFolderId);

            for (MusicFile musicFile : searchService.getRandomSongs(criteria)) {
                File coverArt = musicFileService.getCoverArt(musicFile.getParent());
                List<Attribute> attributes = createAttributesForMusicFile(player, coverArt, musicFile);
                builder.add("song", attributes, true);
            }
            builder.endAll();
            response.getWriter().print(builder);
        } catch (ServletRequestBindingException x) {
            error(request, response, ErrorCode.MISSING_PARAMETER, getErrorMessage(x));
        } catch (Exception x) {
            LOG.warn("Error in REST API.", x);
            error(request, response, ErrorCode.GENERIC, getErrorMessage(x));
        }
    }

    public void getNowPlaying(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        XMLBuilder builder = createXMLBuilder(request, response, true);
        builder.add("nowPlaying", false);

        for (TransferStatus status : statusService.getAllStreamStatuses()) {

            Player player = status.getPlayer();
            File file = status.getFile();
            if (player != null && player.getUsername() != null && file != null) {

                String username = player.getUsername();
                UserSettings userSettings = settingsService.getUserSettings(username);
                if (!userSettings.isNowPlayingAllowed()) {
                    continue;
                }

                MusicFile musicFile = musicFileService.getMusicFile(file);
                File coverArt = musicFileService.getCoverArt(musicFile.getParent());

                long minutesAgo = status.getMillisSinceLastUpdate() / 1000L / 60L;
                if (minutesAgo < 60) {
                    List<Attribute> attributes = createAttributesForMusicFile(player, coverArt, musicFile);
                    attributes.add(new Attribute("username", username));
                    attributes.add(new Attribute("playerId", player.getId()));
                    if (player.getName() != null) {
                        attributes.add(new Attribute("playerName", player.getName()));
                    }
                    attributes.add(new Attribute("minutesAgo", minutesAgo));
                    builder.add("entry", attributes, true);
                }
            }
        }

        builder.endAll();
        response.getWriter().print(builder);
    }

    private List<Attribute> createAttributesForMusicFile(Player player, File coverArt, MusicFile musicFile) throws IOException {
        List<Attribute> attributes = new ArrayList<Attribute>();
        attributes.add(new Attribute("id", StringUtil.utf8HexEncode(musicFile.getPath())));
        attributes.add(new Attribute("parent", StringUtil.utf8HexEncode(musicFile.getParent().getPath())));
        attributes.add(new Attribute("title", musicFile.getTitle()));
        attributes.add(new Attribute("isDir", musicFile.isDirectory()));

        if (musicFile.isFile()) {
            MusicFile.MetaData metaData = musicFile.getMetaData();
            attributes.add(new Attribute("album", metaData.getAlbum()));
            attributes.add(new Attribute("artist", metaData.getArtist()));
            Integer duration = metaData.getDuration();
            if (duration != null) {
                attributes.add(new Attribute("duration", duration));
            }
            Integer bitRate = metaData.getBitRate();
            if (bitRate != null) {
                attributes.add(new Attribute("bitRate", bitRate));
            }

            Integer track = metaData.getTrackNumber();
            if (track != null) {
                attributes.add(new Attribute("track", track));
            }

            Integer year = metaData.getYearAsInteger();
            if (year != null) {
                attributes.add(new Attribute("year", year));
            }

            String genre = metaData.getGenre();
            if (genre != null) {
                attributes.add(new Attribute("genre", genre));
            }

            attributes.add(new Attribute("size", musicFile.length()));
            String suffix = musicFile.getSuffix();
            attributes.add(new Attribute("suffix", suffix));
            attributes.add(new Attribute("contentType", StringUtil.getMimeType(suffix)));
            attributes.add(new Attribute("isVideo", musicFile.isVideo()));

            if (coverArt != null) {
                attributes.add(new Attribute("coverArt", StringUtil.utf8HexEncode(coverArt.getPath())));
            }

            if (transcodingService.isTranscodingRequired(musicFile, player)) {
                String transcodedSuffix = transcodingService.getSuffix(player, musicFile);
                attributes.add(new Attribute("transcodedSuffix", transcodedSuffix));
                attributes.add(new Attribute("transcodedContentType", StringUtil.getMimeType(transcodedSuffix)));
            }

            String path = getRelativePath(musicFile);
            if (path != null) {
                attributes.add(new Attribute("path", path));
            }

        } else {

            File childCoverArt = musicFileService.getCoverArt(musicFile);
            if (childCoverArt != null) {
                attributes.add(new Attribute("coverArt", StringUtil.utf8HexEncode(childCoverArt.getPath())));
            }

            String artist = resolveArtist(musicFile);
            if (artist != null) {
                attributes.add(new Attribute("artist", artist));
            }

        }
        return attributes;
    }

    private String resolveArtist(MusicFile file) throws IOException {

        // If directory, find artist from metadata in child.
        if (file.isDirectory()) {
            file = file.getFirstChild();
            if (file == null) {
                return null;
            }
        }
        return file.getMetaData().getArtist();
    }


    private String getRelativePath(MusicFile musicFile) {

        String filePath = musicFile.getPath();

        // Convert slashes.
        filePath = filePath.replace('\\', '/');

        String filePathLower = filePath.toLowerCase();

        List<MusicFolder> musicFolders = settingsService.getAllMusicFolders();
        for (MusicFolder musicFolder : musicFolders) {
            String folderPath = musicFolder.getPath().getPath();
            folderPath = folderPath.replace('\\', '/');
            String folderPathLower = folderPath.toLowerCase();

            if (filePathLower.startsWith(folderPathLower)) {
                String relativePath = filePath.substring(folderPath.length());
                return relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
            }
        }

        return null;
    }

    public ModelAndView download(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        User user = securityService.getCurrentUser(request);
        if (!user.isDownloadRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to download files.");
            return null;
        }

        return downloadController.handleRequest(request, response);
    }

    public ModelAndView stream(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        User user = securityService.getCurrentUser(request);
        if (!user.isStreamRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to play files.");
            return null;
        }

        streamController.handleRequest(request, response);
        return null;
    }

    public void scrobble(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        XMLBuilder builder = createXMLBuilder(request, response, true);

        Player player = playerService.getPlayer(request, response);

        MusicFile file;
        try {
            String path = StringUtil.utf8HexDecode(ServletRequestUtils.getRequiredStringParameter(request, "id"));
            file = musicFileService.getMusicFile(path);
            boolean submission = ServletRequestUtils.getBooleanParameter(request, "submission", true);
            audioScrobblerService.register(file, player.getUsername(), submission);
        } catch (Exception x) {
            LOG.warn("Error in REST API.", x);
            error(request, response, ErrorCode.GENERIC, getErrorMessage(x));
            return;
        }

        builder.endAll();
        response.getWriter().print(builder);
    }

    public ModelAndView videoPlayer(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);

        Map<String, Object> map = new HashMap<String, Object>();
        String path = request.getParameter("path");
        MusicFile file = musicFileService.getMusicFile(path);

        int timeOffset = ServletRequestUtils.getIntParameter(request, "timeOffset", 0);
        timeOffset = Math.max(0, timeOffset);
        Integer duration = file.getMetaData().getDuration();
        if (duration != null) {
            map.put("skipOffsets", VideoPlayerController.createSkipOffsets(duration));
            timeOffset = Math.min(duration, timeOffset);
            duration -= timeOffset;
        }

        map.put("id", request.getParameter("id"));
        map.put("u", request.getParameter("u"));
        map.put("p", request.getParameter("p"));
        map.put("c", request.getParameter("c"));
        map.put("v", request.getParameter("v"));
        map.put("video", file);
        map.put("maxBitRate", ServletRequestUtils.getIntParameter(request, "maxBitRate", VideoPlayerController.DEFAULT_BIT_RATE));
        map.put("duration", duration);
        map.put("timeOffset", timeOffset);
        map.put("bitRates", VideoPlayerController.BIT_RATES);
        map.put("autoplay", ServletRequestUtils.getBooleanParameter(request, "autoplay", true));

        ModelAndView result = new ModelAndView("rest/videoPlayer");
        result.addObject("model", map);
        return result;
    }

    public ModelAndView getCoverArt(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        return coverArtController.handleRequest(request, response);
    }

    public void changePassword(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        try {

            String username = ServletRequestUtils.getRequiredStringParameter(request, "username");
            String password = ServletRequestUtils.getRequiredStringParameter(request, "password");

            User authUser = securityService.getCurrentUser(request);
            if (!authUser.isAdminRole() && !username.equals(authUser.getUsername())) {
                error(request, response, ErrorCode.NOT_AUTHORIZED, authUser.getUsername() + " is not authorized to change password for " + username);
                return;
            }

            User user = securityService.getUserByName(username);
            user.setPassword(password);
            securityService.updateUser(user);

            XMLBuilder builder = createXMLBuilder(request, response, true).endAll();
            response.getWriter().print(builder);
        } catch (ServletRequestBindingException x) {
            error(request, response, ErrorCode.MISSING_PARAMETER, getErrorMessage(x));
        } catch (Exception x) {
            LOG.warn("Error in REST API.", x);
            error(request, response, ErrorCode.GENERIC, getErrorMessage(x));
        }
    }

    public void getUser(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);

        String username;
        try {
            username = ServletRequestUtils.getRequiredStringParameter(request, "username");
        } catch (ServletRequestBindingException x) {
            error(request, response, ErrorCode.MISSING_PARAMETER, getErrorMessage(x));
            return;
        }

        User currentUser = securityService.getCurrentUser(request);
        if (!username.equals(currentUser.getUsername()) && !currentUser.isAdminRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, currentUser.getUsername() + " is not authorized to get details for other users.");
            return;
        }

        User requestedUser = securityService.getUserByName(username);
        if (requestedUser == null) {
            error(request, response, ErrorCode.NOT_FOUND, "No such user: " + username);
            return;
        }

        XMLBuilder builder = createXMLBuilder(request, response, true);
        List<Attribute> attributes = Arrays.asList(
                new Attribute("username", requestedUser.getUsername()),
                new Attribute("adminRole", requestedUser.isAdminRole()),
                new Attribute("settingsRole", requestedUser.isSettingsRole()),
                new Attribute("downloadRole", requestedUser.isDownloadRole()),
                new Attribute("uploadRole", requestedUser.isUploadRole()),
                new Attribute("playlistRole", requestedUser.isPlaylistRole()),
                new Attribute("coverArtRole", requestedUser.isCoverArtRole()),
                new Attribute("commentRole", requestedUser.isCommentRole()),
                new Attribute("podcastRole", requestedUser.isPodcastRole()),
                new Attribute("streamRole", requestedUser.isStreamRole()),
                new Attribute("jukeboxRole", requestedUser.isJukeboxRole())
        );

        builder.add("user", attributes, true);
        builder.endAll();
        response.getWriter().print(builder);
    }

    public void createUser(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        User user = securityService.getCurrentUser(request);
        if (!user.isAdminRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to create new users.");
            return;
        }

        try {
            UserSettingsCommand command = new UserSettingsCommand();
            command.setUsername(ServletRequestUtils.getRequiredStringParameter(request, "username"));
            command.setPassword(ServletRequestUtils.getRequiredStringParameter(request, "password"));
            command.setLdapAuthenticated(ServletRequestUtils.getBooleanParameter(request, "ldapAuthenticated", false));
            command.setAdminRole(ServletRequestUtils.getBooleanParameter(request, "adminRole", false));
            command.setCommentRole(ServletRequestUtils.getBooleanParameter(request, "commentRole", false));
            command.setCoverArtRole(ServletRequestUtils.getBooleanParameter(request, "coverArtRole", false));
            command.setDownloadRole(ServletRequestUtils.getBooleanParameter(request, "downloadRole", false));
            command.setStreamRole(ServletRequestUtils.getBooleanParameter(request, "streamRole", true));
            command.setUploadRole(ServletRequestUtils.getBooleanParameter(request, "uploadRole", false));
            command.setJukeboxRole(ServletRequestUtils.getBooleanParameter(request, "jukeboxRole", false));
            command.setPlaylistRole(ServletRequestUtils.getBooleanParameter(request, "playlistRole", false));
            command.setPodcastRole(ServletRequestUtils.getBooleanParameter(request, "podcastRole", false));
            command.setSettingsRole(ServletRequestUtils.getBooleanParameter(request, "settingsRole", true));
            command.setTranscodeSchemeName(ServletRequestUtils.getStringParameter(request, "transcodeScheme", TranscodeScheme.OFF.name()));

            userSettingsController.createUser(command);
            XMLBuilder builder = createXMLBuilder(request, response, true).endAll();
            response.getWriter().print(builder);

        } catch (ServletRequestBindingException x) {
            error(request, response, ErrorCode.MISSING_PARAMETER, getErrorMessage(x));
        } catch (Exception x) {
            LOG.warn("Error in REST API.", x);
            error(request, response, ErrorCode.GENERIC, getErrorMessage(x));
        }
    }

    public void deleteUser(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        User user = securityService.getCurrentUser(request);
        if (!user.isAdminRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to delete users.");
            return;
        }

        try {
            String username = ServletRequestUtils.getRequiredStringParameter(request, "username");
            securityService.deleteUser(username);

            XMLBuilder builder = createXMLBuilder(request, response, true).endAll();
            response.getWriter().print(builder);

        } catch (ServletRequestBindingException x) {
            error(request, response, ErrorCode.MISSING_PARAMETER, getErrorMessage(x));
        } catch (Exception x) {
            LOG.warn("Error in REST API.", x);
            error(request, response, ErrorCode.GENERIC, getErrorMessage(x));
        }
    }

    public void getChatMessages(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        XMLBuilder builder = createXMLBuilder(request, response, true);

        long since = ServletRequestUtils.getLongParameter(request, "since", 0L);

        builder.add("chatMessages", false);

        for (ChatService.Message message : chatService.getMessages(0L).getMessages()) {
            long time = message.getDate().getTime();
            if (time > since) {
                builder.add("chatMessage", true, new Attribute("username", message.getUsername()),
                        new Attribute("time", time), new Attribute("message", message.getContent()));
            }
        }
        builder.endAll();
        response.getWriter().print(builder);
    }

    public void addChatMessage(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        try {
            chatService.doAddMessage(ServletRequestUtils.getRequiredStringParameter(request, "message"), request);
            XMLBuilder builder = createXMLBuilder(request, response, true).endAll();
            response.getWriter().print(builder);
        } catch (ServletRequestBindingException x) {
            error(request, response, ErrorCode.MISSING_PARAMETER, getErrorMessage(x));
        }
    }

    public void getLyrics(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String artist = request.getParameter("artist");
        String title = request.getParameter("title");
        LyricsInfo lyrics = lyricsService.getLyrics(artist, title);

        XMLBuilder builder = createXMLBuilder(request, response, true);
        List<Attribute> attributes = new ArrayList<Attribute>();
        if (lyrics.getArtist() != null) {
            attributes.add(new Attribute("artist", lyrics.getArtist()));
        }
        if (lyrics.getTitle() != null) {
            attributes.add(new Attribute("title", lyrics.getTitle()));
        }
        builder.add("lyrics", attributes, false);
        if (lyrics.getLyrics() != null) {
            builder.addText(lyrics.getLyrics() + "\n");
        }

        builder.endAll();
        response.getWriter().print(builder);
    }

    private HttpServletRequest wrapRequest(HttpServletRequest request) {
        return wrapRequest(request, false);
    }

    private HttpServletRequest wrapRequest(final HttpServletRequest request, boolean jukebox) {
        final String playerId = createPlayerIfNecessary(request, jukebox);
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getParameter(String name) {

                // Renames "id" request parameter to "path".
                if ("path".equals(name)) {
                    try {
                        return StringUtil.utf8HexDecode(request.getParameter("id"));
                    } catch (Exception e) {
                        return null;
                    }
                }

                // Returns the correct player to be used in PlayerService.getPlayer()
                else if ("player".equals(name)) {
                    return playerId;
                }

                return super.getParameter(name);
            }
        };
    }

    private String getErrorMessage(Exception x) {
        if (x.getMessage() != null) {
            return x.getMessage();
        }
        return x.getClass().getSimpleName();
    }

    private void error(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String message) throws IOException {
        XMLBuilder builder = createXMLBuilder(request, response, false);
        builder.add("error", true,
                new XMLBuilder.Attribute("code", code.getCode()),
                new XMLBuilder.Attribute("message", message));
        builder.end();
        response.getWriter().print(builder);
    }

    private XMLBuilder createXMLBuilder(HttpServletRequest request, HttpServletResponse response, boolean ok) throws IOException {
        String format = ServletRequestUtils.getStringParameter(request, "f", "xml");
        boolean json = "json".equals(format);

        response.setContentType(json ? "application/json" : "text/xml");
        response.setCharacterEncoding(StringUtil.ENCODING_UTF8);

        XMLBuilder builder = new XMLBuilder(json);
        builder.preamble(StringUtil.ENCODING_UTF8);
        builder.add("subsonic-response", false,
                    new Attribute("xmlns", "http://subsonic.org/restapi"),
                    new Attribute("status", ok ? "ok" : "failed"),
                    new Attribute("version", StringUtil.getRESTProtocolVersion()));
        return builder;
    }

    private String createPlayerIfNecessary(HttpServletRequest request, boolean jukebox) {
        String username = request.getRemoteUser();
        String clientId = request.getParameter("c");
        if (jukebox) {
            clientId += "-jukebox";
        }

        List<Player> players = playerService.getPlayersForUserAndClientId(username, clientId);

        // If not found, create it.
        if (players.isEmpty()) {
            Player player = new Player();
            player.setIpAddress(request.getRemoteAddr());
            player.setUsername(username);
            player.setClientId(clientId);
            player.setName(clientId);
            player.setTechnology(jukebox ? PlayerTechnology.JUKEBOX : PlayerTechnology.EXTERNAL_WITH_PLAYLIST);
            playerService.createPlayer(player);
            players = playerService.getPlayersForUserAndClientId(username, clientId);
        }

        // Return the player ID.
        return !players.isEmpty() ? players.get(0).getId() : null;
    }

    public void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    public void setPlayerService(PlayerService playerService) {
        this.playerService = playerService;
    }

    public void setMusicFileService(MusicFileService musicFileService) {
        this.musicFileService = musicFileService;
    }

    public void setTranscodingService(TranscodingService transcodingService) {
        this.transcodingService = transcodingService;
    }

    public void setDownloadController(DownloadController downloadController) {
        this.downloadController = downloadController;
    }

    public void setCoverArtController(CoverArtController coverArtController) {
        this.coverArtController = coverArtController;
    }

    public void setUserSettingsController(UserSettingsController userSettingsController) {
        this.userSettingsController = userSettingsController;
    }

    public void setLeftController(LeftController leftController) {
        this.leftController = leftController;
    }

    public void setStatusService(StatusService statusService) {
        this.statusService = statusService;
    }

    public void setSearchService(SearchService searchService) {
        this.searchService = searchService;
    }

    public void setPlaylistService(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    public void setStreamController(StreamController streamController) {
        this.streamController = streamController;
    }

    public void setChatService(ChatService chatService) {
        this.chatService = chatService;
    }

    public void setHomeController(HomeController homeController) {
        this.homeController = homeController;
    }

    public void setLyricsService(LyricsService lyricsService) {
        this.lyricsService = lyricsService;
    }

    public void setPlaylistControlService(net.sourceforge.subsonic.ajax.PlaylistService playlistControlService) {
        this.playlistControlService = playlistControlService;
    }

    public void setJukeboxService(JukeboxService jukeboxService) {
        this.jukeboxService = jukeboxService;
    }

    public void setAudioScrobblerService(AudioScrobblerService audioScrobblerService) {
        this.audioScrobblerService = audioScrobblerService;
    }

    public static enum ErrorCode {

        GENERIC(0, "A generic error."),
        MISSING_PARAMETER(10, "Required parameter is missing."),
        PROTOCOL_MISMATCH_CLIENT_TOO_OLD(20, "Incompatible Subsonic REST protocol version. Client must upgrade."),
        PROTOCOL_MISMATCH_SERVER_TOO_OLD(30, "Incompatible Subsonic REST protocol version. Server must upgrade."),
        NOT_AUTHENTICATED(40, "Wrong username or password."),
        NOT_AUTHORIZED(50, "User is not authorized for the given operation."),
        NOT_LICENSED(60, "The trial period for the Subsonic server is over. Please donate to get a license key. Visit subsonic.org for details."),
        NOT_FOUND(70, "Requested data was not found.");

        private final int code;
        private final String message;

        ErrorCode(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
}