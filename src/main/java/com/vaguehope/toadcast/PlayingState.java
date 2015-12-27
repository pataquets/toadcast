package com.vaguehope.toadcast;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fourthline.cling.support.model.DIDLObject;
import org.fourthline.cling.support.model.MediaInfo;
import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.item.Item;

import su.litvak.chromecast.api.v2.Media;
import su.litvak.chromecast.api.v2.Media.StreamType;

public class PlayingState {

	private static final String DEFAULT_TITLE = "Toad Cast";
	private static final String DEFAULT_CONTENT_TYPE = "audio/mpeg";

	private final MediaInfo mediaInfo;
	private final String title;
	private final String artUri;
	private final String contentType;

	public PlayingState (final MediaInfo mediaInfo, final Item item) {
		if (mediaInfo == null) throw new IllegalArgumentException("mediaInfo must not be null.");
		if (mediaInfo.getCurrentURI() == null) throw new IllegalArgumentException("mediaInfo.currentUri must not be null.");
		this.mediaInfo = mediaInfo;

		if (item != null) {
			this.title = item.getTitle() != null ? item.getTitle() : DEFAULT_TITLE;

			final List<URI> arts = item.getPropertyValues(DIDLObject.Property.UPNP.ALBUM_ART_URI.class);
			if (arts != null && arts.size() > 0) {
				this.artUri = arts.get(0).toString();
			}
			else {
				this.artUri = null;
			}

			Res res = null;
			if (item.getResources().size() > 1) {
				for (final Res r : item.getResources()) {
					if ("audio".equalsIgnoreCase(r.getProtocolInfo().getContentFormatMimeType().getType())) {
						res = r;
					}
				}
			}
			if (res == null && item.getResources().size() > 0) {
				res = item.getResources().get(0);
			}
			if (res != null) {
				this.contentType = res.getProtocolInfo().getContentFormatMimeType().toStringNoParameters();
			}
			else {
				this.contentType = null;
			}
		}
		else {
			this.title = DEFAULT_TITLE;
			this.artUri = null;
			this.contentType = DEFAULT_CONTENT_TYPE;
		}
	}

	/**
	 * Never null.
	 */
	public MediaInfo getMediaInfo () {
		return this.mediaInfo;
	}

	/**
	 * https://developers.google.com/cast/docs/reference/messages#MediaInformation
	 */
	public Media toChromeCastMedia () {
		final Map<String, Object> metadata = new HashMap<>();
		metadata.put("metadataType", 0);
		metadata.put("title", this.title);
		metadata.put("images", Arrays.<Map<?, ?>>asList(Collections.<String, String>singletonMap("url", this.artUri)));
		return new Media(this.mediaInfo.getCurrentURI(), this.contentType,
				null, StreamType.BUFFERED, null, metadata, null, null);
	}

}
