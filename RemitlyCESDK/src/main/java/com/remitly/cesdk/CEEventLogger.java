package com.remitly.cesdk;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.schedulers.Schedulers;

class CEEventLogger {
    private static final String TAG = com.remitly.cesdk.RemitlyCE.TAG + "-Logger";
    private static final String ENDPOINT_PATH = "v1/humio";
    private static final MediaType MEDIA_JSON = MediaType.parse("application/json; charset=utf-8");

    private static CEEventLogger s_instance;

    private final RemitlyCE _remitly;
    private final Batcher _batcher;
    private final Map<String, Object> _properties;

    private static void initialize(RemitlyCE remitlyCE, OkHttpClient http) {
        try {
            if (s_instance != null) {
                throw new IllegalArgumentException("Already initialized");
            }

            Map<String, Object> properties = null;
            PackageInfo pInfo;
            try {
                pInfo = remitlyCE.hostActivity.getPackageManager().getPackageInfo(remitlyCE.hostActivity.getPackageName(), 0);

                if (pInfo != null) {
                    PackageInfo finalPInfo = pInfo;
                    properties = new HashMap<String, Object>() {{
                        put("appName", finalPInfo.packageName);
                        put("appVersion", finalPInfo.versionName);
                        put("appBuild", Integer.toString(finalPInfo.versionCode));
                        put("appId", remitlyCE.config.getAppId());
                    }};
                }
            } catch (PackageManager.NameNotFoundException e) {
                // ignore
            }

            s_instance = new CEEventLogger(
                    remitlyCE,
                    new Batcher(remitlyCE, http, 0),
                    properties
            );
        } catch (Exception ex) {
            Log.d(TAG, "initialization error", ex);
        }
    }

    static CEEventLogger rootLogger(final RemitlyCE remitlyCE, final OkHttpClient http) {
        if (s_instance == null) {
            initialize(remitlyCE, http);
        }
        return s_instance;
    }

    private CEEventLogger(final RemitlyCE remitlyCE, final Batcher batcher, final Map<String, Object> properties) {
        _remitly = remitlyCE;
        _batcher = batcher;
        _properties = properties;
    }

    public CEEventLogger withMergedProperties(final Map<String, ?> properties) {
        return new CEEventLogger(
                _remitly,
                _batcher,
                new HashMap<String, Object>(_properties) {{
                    putAll(properties);
                }}
        );
    }

    public void logEvent(String topic) {
        logEvent(topic, null);
    }

    public void logEvent(String topic, Map<String, ?> properties) {
        try {
            final JSONObject json = new JSONObject();

            // Ugly ISO8601 format string because cleaner APIs require higher Android API level
            String timestamp = String.format(
                    Locale.US,
                    "%tFT%<tT.%<tLZ",
                    Calendar.getInstance(TimeZone.getTimeZone("Z"))
            );

            DeviceEnvironmentProps de = _remitly.config.getDeviceEnvironment().get();

            // Two timestamp fields are required:
            // one for the ingest endpoint and one for the event parser.
            json.put("timestamp", timestamp);
            json.put("attributes", new JSONObject() {{
                put("@timestamp", timestamp);
                put("topic", topic);
                put("data", new JSONObject() {{
                    if (properties != null) {
                        for (final Map.Entry<String, ?> entry : properties.entrySet()) {
                            put(entry.getKey(), entry.getValue());
                        }
                    }
                }});
                put("sdk", "ConnectedExperience");
                put("forge", new JSONObject() {{
                    put("app", "remitly-client");
                    put("domain", _remitly.config.getDomain());
                }});
                put("env", new JSONObject(_properties) {{
                    put("platform", "android-sdk");
                    put("locale", Locale.getDefault());
                    put("sdkVersion", _remitly.config.getSdkVersion());
                }});
                if (de != null) {
                    put("device_environment_id", de.getId());
                }
            }});

            _batcher.queueEvent(json);
        } catch (JSONException ex) {
            Log.e(TAG, "Failed to serialize event", ex);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to log event", ex);
        }
    }

    void logRawEvent(final JSONObject rawEvent) {
        _batcher.queueEvent(rawEvent);
    }

    public void flush() {
        _batcher.flush();
    }

    private static class Batcher {
        private static final Scheduler.Worker FLUSH_WORKER = Schedulers.from(Executors.newSingleThreadExecutor()).createWorker();

        private final String LOGGING_ENDPOINT;
        private final DatabaseHelper _helper;
        private final OkHttpClient _http;
        private final int _batchInterval;

        private Subscription _flushSubscription;

        Batcher(final RemitlyCE remitlyCE, final OkHttpClient http, final int batchInterval) {
            _helper = new DatabaseHelper(remitlyCE.hostActivity);
            _http = http;
            _batchInterval = batchInterval;
            LOGGING_ENDPOINT = new HttpUrl.Builder()
                    .scheme("https")
                    .host(remitlyCE.config.getApiHost())
                    .addPathSegments(ENDPOINT_PATH)
                    .build()
                    .toString();

            _queueFlush();
        }

        void queueEvent(final JSONObject event) {
            Observable.just(event)
                    .subscribeOn(Schedulers.io())
                    .subscribe(ev -> {
                        final ContentValues values = new ContentValues();
                        values.put("json", ev.toString());

                        final SQLiteDatabase db = _helper.getWritableDatabase();
                        try {
                            db.insert("events", null, values);
                        } catch (SQLiteException ex) {
                            Log.e(TAG, "Failed to log event", ex);
                        }

                        _queueFlush();
                    });
        }

        void flush() {
            // flush is a no-op without a batch time, as all events are
            // immediately posted in that case.
            if (_batchInterval > 0) {
                _immediateFlush();
            }
        }

        private void _queueFlush() {
            if (_batchInterval > 0) {
                synchronized (this) {
                    // we'll only set a timer if we don't already have one
                    if (_flushSubscription == null) {
                        _flushSubscription = FLUSH_WORKER.schedule(this::_postEvents, _batchInterval, TimeUnit.SECONDS);
                    }
                }
            } else {
                _immediateFlush();
            }
        }

        private void _immediateFlush() {
            synchronized (this) {
                // clear out any already-scheduled flush actions, as it could
                // be an batch-based flush which includes a delay.
                if (_flushSubscription != null) {
                    _flushSubscription.unsubscribe();
                }

                _flushSubscription = FLUSH_WORKER.schedule(this::_postEvents);
            }
        }

        @SuppressLint("Range")
        private void _postEvents() {
            // clear out our subscription now, allowing other events to queue new flushes
            synchronized (this) {
                _flushSubscription.unsubscribe();
                _flushSubscription = null;
            }

            final SQLiteDatabase db = _helper.getWritableDatabase();
            long lastId;

            try {
                do {
                    final Cursor cursor = db.query("events", null, null, null, null, null, "id ASC", "50");
                    final JSONArray events = new JSONArray();
                    final JSONObject innerPayload = new JSONObject();
                    final JSONArray outerPayload = new JSONArray();

                    lastId = -1;
                    while (cursor.moveToNext()) {
                        // Need to drop events up to this id
                        if (cursor.isLast()) {
                            lastId = cursor.getLong(cursor.getColumnIndex("id"));
                        }

                        try {
                            final String raw = cursor.getString(cursor.getColumnIndex("json"));
                            events.put(new JSONObject(raw));
                        } catch (JSONException e) {
                            Log.w(TAG, "Failed to parse event", e);
                        }
                    }
                    cursor.close();

                    try {
                        innerPayload.put("events", events);
                        outerPayload.put(innerPayload);
                    } catch (JSONException e) {
                        Log.w(TAG, "Failed to build payload", e);
                    }

                    // Post any events that we loaded
                    if (lastId != -1) {
                        final Request request = new Request.Builder()
                                .header("Content-Type", "application/json")
                                .post(RequestBody.create(outerPayload.toString(), MEDIA_JSON))
                                .url(LOGGING_ENDPOINT)
                                .build();
                        final Response response = _http.newCall(request).execute();

                        // Drop the batch of events we just sent, otherwise we'll try again later
                        if (response.isSuccessful()) {
                            db.delete("events", "id <= ?", new String[]{Long.toString(lastId)});
                        } else {
                            Log.w(TAG, String.format("Failed to post events: code=%d", response.code()));
                            break;
                        }
                    }
                } while (lastId != -1);
            } catch (IOException ex) {
                Log.e(TAG, "Failed to upload events", ex);
            } catch (SQLiteException ex) {
                Log.e(TAG, "Failed to query events", ex);
            } catch (Exception ex) {
                Log.e(TAG, "Crashed", ex);
            }
        }
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context) {
            super(context, "remitly~", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, json TEXT NOT NULL)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }

    private static String _nullToEmpty(String string) {
        return string == null ? "" : string;
    }
}
