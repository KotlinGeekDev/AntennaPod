package de.danoeh.antennapod.net.discovery;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.core.service.download.AntennapodHttpClient;
import de.danoeh.antennapod.net.sync.nostr.NostrService;
import de.danoeh.antennapod.net.sync.nostr.NostrServiceException;
import de.danoeh.antennapod.net.sync.nostr.model.NostrEvent;
import de.danoeh.antennapod.net.sync.nostr.model.NostrFilter;
import de.danoeh.antennapod.net.sync.nostr.model.PodcastMetadataEvent;
import de.danoeh.antennapod.net.sync.nostr.util.Bech32;
import de.danoeh.antennapod.net.sync.nostr.util.NostrException;
import de.danoeh.antennapod.net.sync.nostr.util.NostrUtil;
import de.danoeh.antennapod.net.sync.nostr.util.TlvInputParser;
import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class NostrPodcastSearcher implements PodcastSearcher {


    @Override
    public Single<List<PodcastSearchResult>> search(String query) {
        List<String> parsedData;
        try {
            String convertedHex = Bech32.fromBech32(query);
            parsedData = TlvInputParser.profile(NostrUtil.hexToBytes(convertedHex));
            if (!parsedData.get(1).startsWith("wss")) {
                throw new NostrException("No relay hint found");
            }
        } catch (NostrException e) {
            throw new RuntimeException(e);
        }
        String requestJson;
        try {
            JSONObject filterJson = NostrFilter.newFilter()
                    .authors(Collections.singletonList(parsedData.get(0)))
                    .kinds(Collections.singletonList(0))
                    .build().toJson();
            JSONArray request = new JSONArray();
            request.put("REQ");
            request.put("req" + query.substring(0, 17));
            request.put(filterJson);
            requestJson = request.toString();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        String relay = parsedData.get(1);
        return Single.create((SingleOnSubscribe<List<PodcastSearchResult>>) subscriber -> {
            NostrService service = new NostrService(AntennapodHttpClient.getHttpClient(), relay);
            try {
                List<NostrEvent> matchingMetadataEvents = service.fetchEvents(requestJson);
                List<PodcastSearchResult> results = new ArrayList<>();
                for (NostrEvent event : matchingMetadataEvents) {
                    results.add(PodcastSearchResult.fromNostrMetadataEvent((PodcastMetadataEvent) event, query));
                }
                subscriber.onSuccess(results);
            } catch (Exception e) {
                if (e instanceof NostrException || e instanceof JSONException || e instanceof NostrServiceException) {
                    e.printStackTrace();
                    subscriber.onError(e);
                } else if (e instanceof NullPointerException) {
                    e.printStackTrace();
                    subscriber.onError(e);
                }
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread());

    }

    @Override
    public Single<String> lookupUrl(String resultUrl) {
        return null;
    }

    @Override
    public boolean urlNeedsLookup(String resultUrl) {
        return false;
    }

    @Override
    public String getName() {
        return "Nostr";
    }
}
