package bv.dev.aspanta.livecoininfo;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.JsonReader;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

public class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks,
        IDialogFragmentCallback {
    public static final String LOG_TAG = "bv_log";

    private static final int CURRENCY_PAIR_INFO_LOADER = 1;

    private static final String API_URL = "https://api.livecoin.net/exchange/ticker";
    private static final String LIST_ITEM_KEY_NAME = "name";
    private static final String LIST_ITEM_KEY_VAL = "val";

    private static boolean shouldUseLocalData = false; // use build-in data or download from network

    private ScrollView svPersData = null;
    private LinearLayout linlayExchangeData = null;

    private Spinner spinLeft = null;
    private Spinner spinRight = null;
    private ListView lvInfo = null;

    private ArrayList<CurrencyPairInfo> alCPInfo = new ArrayList<>(); // all data
    //private HashSet<String> setCurrency = new HashSet<>(); // unique currency set
    private HashMap<String, ArrayList<String>> mapCurPairs = new HashMap<>(); // pairs by unique currency
    private HashMap<String, ArrayList<Integer>> mapCurPairIds = new HashMap<>(); // pairs ids by unique currency
    private ArrayList<String> alSpinLeftData = new ArrayList<>();
    private ArrayList<String> alSpinRightData = new ArrayList<>();

    /* unused
    private ArrayList<ArrayList<String>> al2dCurPairRight = new ArrayList<>(); // possible right parts of cur pair
    private ArrayList<ArrayList<Integer>> al2dCurPairId = new ArrayList<>(); // Ids of info structs for cur pair
    */

    private String spinLeftItem = null; // selected in left spinner
    private String spinRightItem = null; // selected in left spinner
    private ArrayList<Map<String, String>> alLvInfoData = new ArrayList<>(); // ListViewInfo data
    private ArrayAdapter<String> spinLeftAdp = null;
    private ArrayAdapter<String> spinRightAdp = null;
    private SimpleAdapter lvInfoAdp = null;
    private Handler handlerUI = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        svPersData = (ScrollView) findViewById(R.id.sv_personal_data);
        linlayExchangeData = (LinearLayout) findViewById(R.id.linlay_exchange_data);

        svPersData.setVisibility(View.GONE);
        linlayExchangeData.setVisibility(View.GONE);

        lvInfo = (ListView) findViewById(R.id.list_info);
        spinLeft = (Spinner) findViewById(R.id.spinnerLeft);
        spinRight = (Spinner) findViewById(R.id.spinnerRight);

        lvInfoAdp = new SimpleAdapter(this, alLvInfoData, R.layout.list_info_item,
                new String[] {LIST_ITEM_KEY_NAME, LIST_ITEM_KEY_VAL}, new int[] {R.id.tv_name, R.id.tv_val});
        lvInfo.setAdapter(lvInfoAdp);

        spinLeftAdp = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, alSpinLeftData);
        spinRightAdp = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, alSpinRightData);
        spinLeft.setAdapter(spinLeftAdp);
        spinRight.setAdapter(spinRightAdp);
        spinLeft.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                alLvInfoData.clear();
                lvInfoAdp.notifyDataSetChanged();
                try {
                    /* // not only remove same, but all that can't be combined
                    spinLeftItem = alSpinLeftData.get(position);
                    spinRightItem = alSpinRightData.get(spinRight.getSelectedItemPosition());
                    alSpinRightData.clear();
                    alSpinRightData.addAll(alSpinLeftData); // restore full list
                    alSpinRightData.remove(position); // hide selected in left spinner
                    spinRightAdp.notifyDataSetChanged();
                    int newPos = spinRightAdp.getPosition(spinRightItem);
                    if(newPos >= 0) { // was not removed
                        spinRight.setSelection(newPos); // restore selection
                    } else { // was removed, save new
                        spinRightItem = alSpinRightData.get(spinRight.getSelectedItemPosition());
                    }
                    */
                    spinLeftItem = alSpinLeftData.get(position);

                    alSpinRightData.clear();
                    alSpinRightData.addAll(mapCurPairs.get(spinLeftItem)); // list for left part
                    spinRightAdp.notifyDataSetChanged();
                    spinRight.setSelection(0); // reset selection
                    spinRightItem = alSpinRightData.get(spinRight.getSelectedItemPosition());

                    if(spinLeft.getSelectedItemPosition() >= 0
                            && spinRight.getSelectedItemPosition() >= 0) {
                        showCurrencyPairInfo();
                    }
                } catch(IndexOutOfBoundsException iobe) {
                    Log.e(LOG_TAG, "Error @ Spinner.onItemSelected : IndexOutOfBoundsException", iobe);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                Log.i(LOG_TAG, "SpinnerLeft.onNothingSelected");
                spinLeftItem = null;
                alLvInfoData.clear();
                lvInfoAdp.notifyDataSetChanged();
            }
        });
        spinRight.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                alLvInfoData.clear();
                lvInfoAdp.notifyDataSetChanged();
                try {
                    spinRightItem = alSpinRightData.get(position);
                    if(spinLeft.getSelectedItemPosition() >= 0
                            && spinRight.getSelectedItemPosition() >= 0) {
                        showCurrencyPairInfo();
                    }
                } catch(IndexOutOfBoundsException iobe) {
                    Log.e(LOG_TAG, "Error @ Spinner.onItemSelected : IndexOutOfBoundsException", iobe);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                Log.i(LOG_TAG, "SpinnerRight.onNothingSelected");
                alLvInfoData.clear();
                lvInfoAdp.notifyDataSetChanged();
                spinRightItem = null;
            }
        });

        showChooserDialog();
    }

    private void showChooserDialog() {
        // choose data source
        ChooserDialogFragment cdf = new ChooserDialogFragment();
        cdf.show(getSupportFragmentManager(), ChooserDialogFragment.class.getSimpleName());
    }

    // after user choosed settings
    private void postOnCreate() {
        getSupportLoaderManager().restartLoader(CURRENCY_PAIR_INFO_LOADER, null, this);
    }

    @Override
    public void onDone() {
        postOnCreate();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.mi_load_data :
                getSupportLoaderManager().restartLoader(CURRENCY_PAIR_INFO_LOADER, null, this);
                return true;
            case R.id.mi_choose_data :
                showChooserDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void ctgPersonalClick(View v) {
        if(v.getTag() == null) {
            showPersonalCtg();
            hideExchangeCtg();
            v.setTag(v);
        } else {
            hidePersonalCtg();
            v.setTag(null);
        }
    }

    public void ctgExchangeClick(View v) {
        if(v.getTag() == null) {
            showExchangeCtg();
            hidePersonalCtg();
            v.setTag(v);
        } else {
            hideExchangeCtg();
            v.setTag(null);
        }
    }

    private void showPersonalCtg() {
        setLookPersCtg(android.R.drawable.arrow_up_float, View.VISIBLE);
    }

    private void hidePersonalCtg() {
        setLookPersCtg(android.R.drawable.arrow_down_float, View.GONE);
    }

    private void showExchangeCtg() {
        setLookExchCtg(android.R.drawable.arrow_up_float, View.VISIBLE);
    }

    private void hideExchangeCtg() {
        setLookExchCtg(android.R.drawable.arrow_down_float, View.GONE);
    }

    private void setLookPersCtg(int drawRes, int visibility) {
        ((ImageView) findViewById(R.id.iv_personal_arrow)).setImageResource(drawRes);
        svPersData.setVisibility(visibility);
    }

    private void setLookExchCtg(int drawRes, int visibility) {
        ((ImageView) findViewById(R.id.iv_exchange_arrow)).setImageResource(drawRes);
        linlayExchangeData.setVisibility(visibility);
    }

    //------------------------
    // LoaderCallbacks:
    @Override
    public Loader onCreateLoader(int id, Bundle args) {
        if(id == CURRENCY_PAIR_INFO_LOADER) {
            Toast.makeText(this, R.string.text_loading_data, Toast.LENGTH_LONG).show();
            return new CurrencyPairInfoLoader(this, handlerUI);
        } else {
            return null;
        }
    }

    @Override
    public void onLoadFinished(Loader loader, Object data) {
        if(data == null) {
            Log.w(LOG_TAG, "Data not loaded : null");
            //Toast.makeText(this, R.string.text_data_not_loaded, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            alCPInfo.clear();
            //setCurrency.clear(); unused
            mapCurPairs.clear();
            mapCurPairIds.clear();
            alSpinLeftData.clear();
            alSpinRightData.clear();

            ArrayList<?> alObj = (ArrayList<?>) data;
            for (Object obj : alObj) {
                CurrencyPairInfo cpi = (CurrencyPairInfo) obj;
                alCPInfo.add(cpi);
                //setCurrency.add(cpi.cur); unused
                if(! mapCurPairs.containsKey(cpi.cur)) {
                    mapCurPairs.put(cpi.cur, new ArrayList<String>());
                    mapCurPairIds.put(cpi.cur, new ArrayList<Integer>());
                }
                try {
                    String[] splitRes = cpi.symbol.split("\\p{Punct}");
                    //Log.d(LOG_TAG, "Split res for " + cpi.symbol + " : " + Arrays.toString(splitRes));
                    mapCurPairs.get(cpi.cur).add(splitRes[1]); // 2nd part of pair
                    mapCurPairIds.get(cpi.cur).add(alCPInfo.size() - 1); // index in array
                } catch(PatternSyntaxException | NullPointerException | IndexOutOfBoundsException e) {
                    Log.e(LOG_TAG, "Error while parsing currency : ", e);
                    Toast.makeText(this, R.string.err_cant_proc_data, Toast.LENGTH_LONG).show();
                }
            }

            //alSpinLeftData.addAll(setCurrency); unused
            alSpinLeftData.addAll(mapCurPairs.keySet());
            Collections.sort(alSpinLeftData);
            alSpinRightData.addAll(alSpinLeftData);
            spinLeftAdp.notifyDataSetChanged();
            spinRightAdp.notifyDataSetChanged();
            spinLeft.setSelection(1, true); // to reset state and ensure calling onSelected()
            spinLeft.setSelection(0);
            spinRight.setSelection(0);

            Toast.makeText(this, R.string.text_data_loaded, Toast.LENGTH_SHORT).show();
            Log.d(LOG_TAG, "Loaded data : " + alObj);
        } catch (ClassCastException cce) {
            Log.e(LOG_TAG, "Internal Error : ClassCastException : ", cce);
            Toast.makeText(this, R.string.text_data_not_loaded, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onLoaderReset(Loader loader) {
        Log.i(LOG_TAG, "Loading reset");
        Toast.makeText(this, R.string.text_data_not_loaded, Toast.LENGTH_SHORT).show();
    }
    //------------------------

    private String formatFloat(double val) {
        /* old way
        if(val < Math.pow(10, -10)) {
            return "" + val;
        } else {
            return String.format(Locale.getDefault(), "%.10f", val);
        }
        */
        return String.format(Locale.getDefault(), "%g", val);
    }

    private void showCurrencyPairInfo() {
        Log.d(LOG_TAG, "showCurrencyPairInfo");
        if(TextUtils.isEmpty(spinLeftItem) || TextUtils.isEmpty(spinRightItem)) {
            Log.w(LOG_TAG, "showCurrencyPairInfo : nothing selected");
            return;
        }

        boolean found = false;
        String curPair = spinLeftItem + "/" + spinRightItem;
        //for(CurrencyPairInfo cpi : alCPInfo) { unused
        for(int id : mapCurPairIds.get(spinLeftItem)) {
            CurrencyPairInfo cpi = alCPInfo.get(id);
            if(curPair.equalsIgnoreCase(cpi.symbol)) {
                found = true;
                alLvInfoData.clear();

                HashMap<String, String> mapSymbol = new HashMap<>(2);
                mapSymbol.put(LIST_ITEM_KEY_NAME, "Symbol");
                mapSymbol.put(LIST_ITEM_KEY_VAL, cpi.symbol);
                alLvInfoData.add(mapSymbol);

                HashMap<String, String> mapLast = new HashMap<>(2);
                mapLast.put(LIST_ITEM_KEY_NAME, "Last");
                mapLast.put(LIST_ITEM_KEY_VAL, formatFloat(cpi.last));
                alLvInfoData.add(mapLast);

                HashMap<String, String> mapHigh = new HashMap<>(2);
                mapHigh.put(LIST_ITEM_KEY_NAME, "High");
                mapHigh.put(LIST_ITEM_KEY_VAL, formatFloat(cpi.high));
                alLvInfoData.add(mapHigh);

                HashMap<String, String> mapLow = new HashMap<>(2);
                mapLow.put(LIST_ITEM_KEY_NAME, "Low");
                mapLow.put(LIST_ITEM_KEY_VAL, formatFloat(cpi.low));
                alLvInfoData.add(mapLow);

                HashMap<String, String> mapVol = new HashMap<>(2);
                mapVol.put(LIST_ITEM_KEY_NAME, "Volume");
                mapVol.put(LIST_ITEM_KEY_VAL, formatFloat(cpi.volume));
                alLvInfoData.add(mapVol);

                HashMap<String, String> mapVwap = new HashMap<>(2);
                mapVwap.put(LIST_ITEM_KEY_NAME, "vwap");
                mapVwap.put(LIST_ITEM_KEY_VAL, formatFloat(cpi.vwap));
                alLvInfoData.add(mapVwap);

                HashMap<String, String> mapMaxBid = new HashMap<>(2);
                mapMaxBid.put(LIST_ITEM_KEY_NAME, "Max bid");
                mapMaxBid.put(LIST_ITEM_KEY_VAL, formatFloat(cpi.maxBid));
                alLvInfoData.add(mapMaxBid);

                HashMap<String, String> mapMinAsk = new HashMap<>(2);
                mapMinAsk.put(LIST_ITEM_KEY_NAME, "Min ask");
                mapMinAsk.put(LIST_ITEM_KEY_VAL, formatFloat(cpi.minAsk));
                alLvInfoData.add(mapMinAsk);

                HashMap<String, String> mapBestBid = new HashMap<>(2);
                mapBestBid.put(LIST_ITEM_KEY_NAME, "Best bid");
                mapBestBid.put(LIST_ITEM_KEY_VAL, formatFloat(cpi.bestBid));
                alLvInfoData.add(mapBestBid);

                HashMap<String, String> mapBestAsk = new HashMap<>(2);
                mapBestAsk.put(LIST_ITEM_KEY_NAME, "Best ask");
                mapBestAsk.put(LIST_ITEM_KEY_VAL, formatFloat(cpi.bestAsk));
                alLvInfoData.add(mapBestAsk);

                lvInfoAdp.notifyDataSetChanged();
                break;
            }
        }

        if(! found) {
            Log.i(LOG_TAG, getString(R.string.text_cur_pair_not_available) + curPair);
            Toast.makeText(this, getString(R.string.text_cur_pair_not_available) + curPair,
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Simple structure with info about currency pair
     *
     * example:
     *         "cur":"BTC",
     *         "symbol": "BTC/USD",
     *         "last": 431.15098,
     *         "high": 447,
     *         "low": 420,
     *         "volume": 491.24533286,
     *         "vwap": 440.11749153,
     *         "max_bid": 447,
     *         "min_ask": 420,
     *         "best_bid": 429.26,
     *         "best_ask": 431.125
     */
    private static class CurrencyPairInfo {
        String cur;
        String symbol;
        double last;
        double high;
        double low;
        double volume;
        double vwap;
        double maxBid;
        double minAsk;
        double bestBid;
        double bestAsk;

        @Override
        public String toString() {
            return "{" +
                    "cur == " + cur + "; " +
                    "symbol == " + symbol + "; " +
                    "last == " + last + "; " +
                    "high == " + high + "; " +
                    "low == " + low + "; " +
                    "volume == " + volume + "; " +
                    "vwap == " + vwap + "; " +
                    "maxBid == " + maxBid + "; " +
                    "minAsk == " + minAsk + "; " +
                    "bestBid == " + bestBid + "; " +
                    "bestAsk == " + bestAsk + "; " +
                    "}";

        }
    }

    private static class CurrencyPairInfoLoader extends AsyncTaskLoader<ArrayList<CurrencyPairInfo>> {
        private ArrayList<CurrencyPairInfo> storedData = null; // store loaded data

        private StringBuilder sbMsg = new StringBuilder();
        private int responseCode = -2;
        private String responseMsg;
        private Handler handlerUI = null;

        // second arg could be Bundle
        public CurrencyPairInfoLoader(MainActivity mainActivity, Handler hndlr) {
            super(mainActivity);
            handlerUI = hndlr;
            // to init smth else better use application's context: getContext()
            Log.d(LOG_TAG, "CurrencyPairInfoLoader()");
        }

        @Override
        public ArrayList<CurrencyPairInfo> loadInBackground() {
            Log.d(LOG_TAG, "CurrencyPairInfoLoader.loadInBackground()");
            if(isLoadInBackgroundCanceled()) {
                return null;
            }

            HttpURLConnection conn = null;
            JsonReader jsonRdr = null;
            try {
                if(shouldUseLocalData) {
                    // can pass charset
                    jsonRdr = new JsonReader(new InputStreamReader(getContext().getResources()
                            .openRawResource(R.raw.api_livecoin_net__exchange__ticker)));
                } else {
                    URL url = new URL(API_URL);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setReadTimeout(10000);
                    conn.setConnectTimeout(15000);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.connect();
                    responseCode = conn.getResponseCode();
                    responseMsg = conn.getResponseMessage();
                    Log.i(LOG_TAG, "Load Data : ResponseCode = " + responseCode);
                    Log.i(LOG_TAG, "Load Data : ResponseMessage = " + responseMsg);
                    Log.i(LOG_TAG, "Load Data : ContentType = " + conn.getContentType());
                    jsonRdr = new JsonReader(new InputStreamReader(conn.getInputStream())); // can pass charset
                }
                return readJSONArray(jsonRdr);
            } catch (IOException ioe) {
                sbMsg.append("Error: ")
                        .append(ioe.getMessage()).append("; ")
                        .append(responseMsg)
                        .append(" (").append(responseCode).append(")");
                Log.e(LOG_TAG, "Error while loading data " + sbMsg, ioe);
                if(handlerUI == null) {
                    Log.e(LOG_TAG, "loadInBackground : handler == null");
                } else {
                    handlerUI.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getContext(), sbMsg, Toast.LENGTH_LONG).show();
                        }
                    });
                }
                return null;
            } finally {
                if(conn != null) {
                    conn.disconnect();
                }
                if(jsonRdr != null) {
                    try {
                        jsonRdr.close();
                    } catch (IOException ignored) {}
                }
            }
        }

        private ArrayList<CurrencyPairInfo> readJSONArray(JsonReader jsonRdr) throws IOException {
            ArrayList<CurrencyPairInfo> alInfo = new ArrayList<>();
            jsonRdr.beginArray();
            while(jsonRdr.hasNext()) {
                // structure element data
                CurrencyPairInfo cpi = new CurrencyPairInfo();

                jsonRdr.beginObject();
                while(jsonRdr.hasNext()) {
                    String name = jsonRdr.nextName();
                    // for NULLable objects can use
                    //jsonRdr.peek() == JsonToken.NULL
                    switch(name) {
                        case "cur":
                            cpi.cur = jsonRdr.nextString();
                            break;
                        case "symbol":
                            cpi.symbol = jsonRdr.nextString();
                            break;
                        case "last":
                            cpi.last = jsonRdr.nextDouble();
                            break;
                        case "high":
                            cpi.high = jsonRdr.nextDouble();
                            break;
                        case "low":
                            cpi.low = jsonRdr.nextDouble();
                            break;
                        case "volume":
                            cpi.volume = jsonRdr.nextDouble();
                            break;
                        case "vwap":
                            cpi.vwap = jsonRdr.nextDouble();
                            break;
                        case "max_bid":
                            cpi.maxBid = jsonRdr.nextDouble();
                            break;
                        case "min_ask":
                            cpi.minAsk = jsonRdr.nextDouble();
                            break;
                        case "best_bid":
                            cpi.bestBid = jsonRdr.nextDouble();
                            break;
                        case "best_ask":
                            cpi.bestAsk = jsonRdr.nextDouble();
                            break;
                        default:
                            jsonRdr.skipValue();
                            break;
                    }
                }
                jsonRdr.endObject();

                alInfo.add(cpi);
            }
            jsonRdr.endArray();

            return alInfo;
        }

        @Override
        public void deliverResult(ArrayList<CurrencyPairInfo> data) {
            Log.d(LOG_TAG, "CurrencyPairInfoLoader.deliverResult()");
            if(isReset() && data != null) {
                releaseResources(data);
            }
            ArrayList<CurrencyPairInfo> oldData = storedData;
            storedData = data;
            if(isStarted()) { // deliver now
                super.deliverResult(data);
            }
            // new result delivered, old is not needed no more
            // so time to release old resources
            if(oldData != null) {
                releaseResources(oldData);
            }
        }


        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        @Override
        protected void onStopLoading() {
            cancelLoad();
        }

        @Override
        public void onCanceled(ArrayList<CurrencyPairInfo> data) {
            super.onCanceled(data);
            releaseResources(data);
        }

        @Override
        protected void onReset() {
            super.onReset();
            // stop if not stopped
            onStopLoading();
            if(storedData != null) {
                releaseResources(storedData);
                storedData = null;
            }
        }

        private void releaseResources(ArrayList<CurrencyPairInfo> data) {
            // nothing yet
        }
    }

    //----------------------
    // Dialog fragment
    public static class ChooserDialogFragment extends DialogFragment {
        private IDialogFragmentCallback dfc;
        private boolean dissmissed = false;

        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            try {
                dfc = (IDialogFragmentCallback) getActivity();
            } catch (ClassCastException cce) {
                Log.e(LOG_TAG, "Error", cce);
                Toast.makeText(getActivity(), R.string.err_cant_show_dlg, Toast.LENGTH_LONG).show();
                return super.onCreateDialog(savedInstanceState);
            }

            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.text_dlg_chooser_title)
                    .setMessage(R.string.text_dlg_chooser_msg)
                    .setPositiveButton(R.string.text_dlg_btn_dload, cdfOcl)
                    .setNegativeButton(R.string.text_dlg_btn_use_local, cdfOcl)
                    .setCancelable(false)
                    .create();
            // can't set onCancel / Dismiss listeners, overridden methods instead
        }

        // heard about bug with onCancel / onDismiss callbacks
        // but it works correctly even without handling this event
        // could be deleted
        @Override
        public void onCancel(DialogInterface dialog) {
            super.onCancel(dialog);
            //Log.d(LOG_TAG, "onCancel()"); // not needed
            if(! dissmissed) {
                dissmissed = true;
                dfc.onDone();
            }
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
            //Log.d(LOG_TAG, "onDismiss()"); // not needed
            if(! dissmissed) {
                dissmissed = true;
                dfc.onDone();
            }
        }

        private DialogInterface.OnClickListener cdfOcl = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch(which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        shouldUseLocalData = false;
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                    default:
                        shouldUseLocalData = true;
                        break;

                }
            }
        };

    }
}
