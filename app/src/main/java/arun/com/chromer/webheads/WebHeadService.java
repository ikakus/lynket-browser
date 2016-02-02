package arun.com.chromer.webheads;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.IBinder;
import android.support.customtabs.CustomTabsSession;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.view.WindowManager;

import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;

import java.util.ArrayList;

import arun.com.chromer.R;
import arun.com.chromer.activities.WebHeadActivity;
import arun.com.chromer.chrometabutilites.CustomActivityHelper;
import timber.log.Timber;

public class WebHeadService extends Service implements WebHead.WebHeadClickListener, CustomActivityHelper.ConnectionCallback {

    public static final String SHOULD_REBIND = "should_rebind";
    public static final String REBIND_EVENT = "rebind_event";
    private static WebHeadService sInstance = null;
    private ArrayList<WebHead> mWebHeads = new ArrayList<>();
    private WindowManager mWindowManager;
    private CustomActivityHelper mCustomActivityHelper;
    private boolean mCustomTabConnected;
    private BroadcastReceiver mRebindReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean shouldRebind = intent.getBooleanExtra(SHOULD_REBIND, false);
            if (shouldRebind) bindToCustomTabSession();
        }
    };

    public WebHeadService() {
    }

    public static WebHeadService getInstance() {
        return sInstance;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;

        mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        // bind to custom tab session
        bindToCustomTabSession();

        LocalBroadcastManager.getInstance(this).registerReceiver(mRebindReceiver,
                new IntentFilter(REBIND_EVENT));
    }

    private void addWebHead(WebHead webHead) {
        mWindowManager.addView(webHead, webHead.getWindowParams());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        processIntentAndLaunchBubble(intent);

        return START_STICKY;
    }

    private void processIntentAndLaunchBubble(Intent intent) {
        if (intent == null) return; // don't do anything

        String urlToLoad = intent.getDataString();

        if (!isLinkAlreadyLoaded(urlToLoad)) {
            WebHead webHead = new WebHead(this, urlToLoad, mWindowManager);
            webHead.setImageDrawable(new IconicsDrawable(this)
                    .icon(GoogleMaterial.Icon.gmd_adjust)
                    .color(ContextCompat.getColor(this, R.color.primary))
                    .sizeDp(56));
            webHead.setOnWebHeadClickListener(this);
            addWebHead(webHead);

            if (mCustomTabConnected)
                mCustomActivityHelper.mayLaunchUrl(Uri.parse(urlToLoad), null, null);
            else
                deferMayLaunchUntilConnected(urlToLoad);
        }
    }

    private boolean isLinkAlreadyLoaded(String urlToLoad) {
        if (urlToLoad == null) return true;
        for (WebHead webHead : mWebHeads) {
            String webHeadUrl = webHead.getUrl();
            if (webHeadUrl != null && webHeadUrl.equalsIgnoreCase(urlToLoad)) {
                return true;
            }
        }
        return false;
    }

    private void deferMayLaunchUntilConnected(final String urlToLoad) {
        Thread deferThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        if (mCustomTabConnected) {
                            Thread.sleep(300);
                            boolean ok = mCustomActivityHelper.mayLaunchUrl(Uri.parse(urlToLoad),
                                    null,
                                    null);
                            Timber.d("Deferred may launch was %b", ok);
                            break;
                        }
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        deferThread.start();
    }

    private void bindToCustomTabSession() {
        if (mCustomActivityHelper != null) {
            // Already an instance exists, so we will un bind the current connection and then
            // bind again.
            Timber.d("Severing existing connection");
            mCustomActivityHelper.unbindCustomTabsService(this);
        }

        mCustomActivityHelper = new CustomActivityHelper();
        mCustomActivityHelper.setConnectionCallback(this);

        boolean ok = mCustomActivityHelper.bindCustomTabsService(this);
        if (ok) Timber.d("Binding successful");
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mRebindReceiver);
        if (mCustomActivityHelper != null) mCustomActivityHelper.unbindCustomTabsService(this);

        for (WebHead webHead : mWebHeads) {
            mWindowManager.removeView(webHead);
        }

        sInstance = null;
        super.onDestroy();
    }

    @Override
    public void onClick(WebHead webHead) {
        if (webHead.getUrl() != null && webHead.getUrl().length() != 0) {
            Intent webHeadActivity = new Intent(this, WebHeadActivity.class);
            webHeadActivity.setData(Uri.parse(webHead.getUrl()));
            webHeadActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(webHeadActivity);
        }
    }

    @Override
    public void onCustomTabsConnected() {
        mCustomTabConnected = true;
        Timber.d("Connected to custom tabs successfully");
    }

    @Override
    public void onCustomTabsDisconnected() {
        mCustomTabConnected = false;
    }

    public CustomTabsSession getTabSession() {
        if (mCustomActivityHelper != null) {
            return mCustomActivityHelper.getSession();
        }
        return null;
    }
}