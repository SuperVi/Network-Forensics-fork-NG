package eu.faircode.netguard;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.NavUtils;



public class ActivityStats extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "NetGuard.Stats";

    private boolean running = false;

    //Pie Charts
    private PieChart pcOrganisations;
    private List<PieEntry> orgEntries = new ArrayList<>();
    private PieDataSet setOrg = new PieDataSet(orgEntries, "");
    private PieData dataOrg;
    private int [] colors = new int[]{R.color.colorAmberOff,R.color.colorBluePrimaryDark,R.color.colorGreenOff,R.color.colorOrangeOff,R.color.colorTealOff,R.color.colorGreenOn};

    private PieChart pcCountries;
    private List<PieEntry> countryEntries = new ArrayList<>();
    private PieDataSet setCountry = new PieDataSet(countryEntries, "");
    private PieData dataCountry;

    private AdapterLog adapter;
    private MenuItem menuSearch = null;

    private boolean live;

    private static final int REQUEST_PCAP = 1;

    private DatabaseHelper.LogChangedListener listener = new DatabaseHelper.LogChangedListener() {
        @Override
        public void onChanged() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateStats();
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Util.setTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.stats);
        running = true;

        getSupportActionBar().setTitle(R.string.menu_stats);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Get settings
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean log = prefs.getBoolean("log", false);

        pcOrganisations = findViewById(R.id.pcOrganisations);
        setOrg.setColors(colors,ActivityStats.this);

        pcCountries = findViewById(R.id.pcCountries);
        setCountry.setColors(colors,ActivityStats.this);

        live = true;
    }

    private void updateStats(){

        orgEntries.clear();
        pcOrganisations.setCenterText("Organisations");
        pcOrganisations.setHoleRadius(20.0f);
        long logEntries = DatabaseHelper.getInstance(ActivityStats.this).getLogCount();
        long logPerOrganisation =0;
        float percentOrganisation = 0.0f;
        Cursor orgCursor = DatabaseHelper.getInstance(ActivityStats.this).getOrganizations();
        for(orgCursor.moveToFirst(); !orgCursor.isAfterLast(); orgCursor.moveToNext()) {

            logPerOrganisation =  orgCursor.getLong(orgCursor.getColumnIndex("COUNT(*)"));
            percentOrganisation = ((float)logPerOrganisation / (float)logEntries)*100;
            if (percentOrganisation>5.0f){
                String label = orgCursor.getString(orgCursor.getColumnIndex("organization"));
                label = label.substring(8);
                PieEntry pe = new PieEntry(percentOrganisation, label);
                orgEntries.add(pe);
                dataOrg = new PieData(setOrg);
                setOrg.setValueTextColor(R.color.colorDarkText);
                setOrg.setValueTextSize(14.0f);
                pcOrganisations.setEntryLabelColor(R.color.colorDarkText);
                pcOrganisations.setData(dataOrg);
                pcOrganisations.invalidate(); // refresh
            }
        }
        orgCursor.close();

        countryEntries.clear();
        pcCountries.setCenterText("Countries");
        pcCountries.setHoleRadius(20.0f);
        long logPerCountry =0;
        float percentCountry = 0.0f;
        Cursor countryCursor = DatabaseHelper.getInstance(ActivityStats.this).getCountries();
        for(countryCursor.moveToFirst(); !countryCursor.isAfterLast(); countryCursor.moveToNext()) {

            logPerCountry =  countryCursor.getLong(countryCursor.getColumnIndex("COUNT(*)"));
            percentCountry = ((float)logPerCountry / (float)logEntries)*100;
            if (percentCountry>3.0f){
                String label = countryCursor.getString(countryCursor.getColumnIndex("country"));
                PieEntry pe = new PieEntry(percentCountry, label);
                countryEntries.add(pe);
                dataCountry = new PieData(setCountry);
                setCountry.setValueTextColor(R.color.colorDarkText);
                setCountry.setValueTextSize(14.0f);
                pcCountries.setEntryLabelColor(R.color.colorDarkText);
                pcCountries.setData(dataCountry);
                pcCountries.invalidate(); // refresh
            }
        }
        countryCursor.close();

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (live) {
            DatabaseHelper.getInstance(this).addLogChangedListener(listener);
            updateStats();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (live)
            DatabaseHelper.getInstance(this).removeLogChangedListener(listener);
    }

    @Override
    protected void onDestroy() {
        running = false;
        adapter = null;
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override    public void onSharedPreferenceChanged(SharedPreferences prefs, String name) {
        Log.i(TAG, "Preference " + name + "=" + prefs.getAll().get(name));
        if ("log".equals(name)) {
            // Get enabled
            boolean log = prefs.getBoolean(name, false);

            // Display disabled warning
            TextView tvDisabled = findViewById(R.id.tvDisabled);
            tvDisabled.setVisibility(log ? View.GONE : View.VISIBLE);

            // Check switch state
            SwitchCompat swEnabled = getSupportActionBar().getCustomView().findViewById(R.id.swEnabled);
            if (swEnabled.isChecked() != log)
                swEnabled.setChecked(log);

            ServiceSinkhole.reload("changed " + name, ActivityStats.this, false);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.logging, menu);

        menuSearch = menu.findItem(R.id.menu_search);
        SearchView searchView = (SearchView) menuSearch.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            private String getUidForName(String query) {
                if (query != null && query.length() > 0) {
                    for (Rule rule : Rule.getRules(true, ActivityStats.this))
                        if (rule.name != null && rule.name.toLowerCase().contains(query.toLowerCase())) {
                            String newQuery = Integer.toString(rule.uid);
                            Log.i(TAG, "Search " + query + " found " + rule.name + " new " + newQuery);
                            return newQuery;
                        }
                    Log.i(TAG, "Search " + query + " not found");
                }
                return query;
            }

            @Override
            public boolean onQueryTextSubmit(String query) {
                if (adapter != null)
                    adapter.getFilter().filter(getUidForName(query));
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (adapter != null)
                    adapter.getFilter().filter(getUidForName(newText));
                return true;
            }
        });
        searchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                if (adapter != null)
                    adapter.getFilter().filter(null);
                return true;
            }
        });

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // https://gist.github.com/granoeste/5574148
        File pcap_file = new File(getDir("data", MODE_PRIVATE), "netguard.pcap");

        boolean export = (getPackageManager().resolveActivity(getIntentPCAPDocument(), 0) != null);

        menu.findItem(R.id.menu_protocol_udp).setChecked(prefs.getBoolean("proto_udp", true));
        menu.findItem(R.id.menu_protocol_tcp).setChecked(prefs.getBoolean("proto_tcp", true));
        menu.findItem(R.id.menu_protocol_other).setChecked(prefs.getBoolean("proto_other", true));
        menu.findItem(R.id.menu_traffic_allowed).setEnabled(prefs.getBoolean("filter", false));
        menu.findItem(R.id.menu_traffic_allowed).setChecked(prefs.getBoolean("traffic_allowed", true));
        menu.findItem(R.id.menu_traffic_blocked).setChecked(prefs.getBoolean("traffic_blocked", true));

        menu.findItem(R.id.menu_refresh).setEnabled(!menu.findItem(R.id.menu_log_live).isChecked());
        menu.findItem(R.id.menu_log_resolve).setChecked(prefs.getBoolean("resolve", false));
        menu.findItem(R.id.menu_log_organization).setChecked(prefs.getBoolean("organization", false));
        menu.findItem(R.id.menu_pcap_enabled).setChecked(prefs.getBoolean("pcap", false));
        menu.findItem(R.id.menu_pcap_export).setEnabled(pcap_file.exists() && export);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final File pcap_file = new File(getDir("data", MODE_PRIVATE), "netguard.pcap");

        switch (item.getItemId()) {
            case android.R.id.home:
                Log.i(TAG, "Up");
                NavUtils.navigateUpFromSameTask(this);
                return true;

            case R.id.menu_protocol_udp:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean("proto_udp", item.isChecked()).apply();
                updateAdapter();
                return true;

            case R.id.menu_protocol_tcp:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean("proto_tcp", item.isChecked()).apply();
                updateAdapter();
                return true;

            case R.id.menu_protocol_other:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean("proto_other", item.isChecked()).apply();
                updateAdapter();
                return true;

            case R.id.menu_traffic_allowed:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean("traffic_allowed", item.isChecked()).apply();
                updateAdapter();
                return true;

            case R.id.menu_traffic_blocked:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean("traffic_blocked", item.isChecked()).apply();
                updateAdapter();
                return true;

            case R.id.menu_log_live:
                item.setChecked(!item.isChecked());
                live = item.isChecked();
                if (live) {
                    DatabaseHelper.getInstance(this).addLogChangedListener(listener);
                    updateAdapter();
                } else
                    DatabaseHelper.getInstance(this).removeLogChangedListener(listener);
                return true;

            case R.id.menu_refresh:
                updateAdapter();
                return true;

            case R.id.menu_log_resolve:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean("resolve", item.isChecked()).apply();
                adapter.setResolve(item.isChecked());
                adapter.notifyDataSetChanged();
                return true;

            case R.id.menu_log_organization:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean("organization", item.isChecked()).apply();
                adapter.setOrganization(item.isChecked());
                adapter.notifyDataSetChanged();
                return true;

            case R.id.menu_pcap_enabled:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean("pcap", item.isChecked()).apply();
                ServiceSinkhole.setPcap(item.isChecked(), ActivityStats.this);
                return true;

            case R.id.menu_pcap_export:
                startActivityForResult(getIntentPCAPDocument(), REQUEST_PCAP);
                return true;

            case R.id.menu_log_clear:
                new AsyncTask<Object, Object, Object>() {
                    @Override
                    protected Object doInBackground(Object... objects) {
                        DatabaseHelper.getInstance(ActivityStats.this).clearLog(-1);
                        if (prefs.getBoolean("pcap", false)) {
                            ServiceSinkhole.setPcap(false, ActivityStats.this);
                            if (pcap_file.exists() && !pcap_file.delete())
                                Log.w(TAG, "Delete PCAP failed");
                            ServiceSinkhole.setPcap(true, ActivityStats.this);
                        } else {
                            if (pcap_file.exists() && !pcap_file.delete())
                                Log.w(TAG, "Delete PCAP failed");
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Object result) {
                        if (running)
                            updateAdapter();
                    }
                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                return true;

            case R.id.menu_log_support:
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://github.com/M66B/NetGuard/blob/master/FAQ.md#user-content-faq27"));
                if (getPackageManager().resolveActivity(intent, 0) != null)
                    startActivity(intent);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateAdapter() {
        if (adapter != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            boolean udp = prefs.getBoolean("proto_udp", true);
            boolean tcp = prefs.getBoolean("proto_tcp", true);
            boolean other = prefs.getBoolean("proto_other", true);
            boolean allowed = prefs.getBoolean("traffic_allowed", true);
            boolean blocked = prefs.getBoolean("traffic_blocked", true);
            adapter.changeCursor(DatabaseHelper.getInstance(this).getLog(udp, tcp, other, allowed, blocked));
            if (menuSearch != null && menuSearch.isActionViewExpanded()) {
                SearchView searchView = (SearchView) menuSearch.getActionView();
                adapter.getFilter().filter(searchView.getQuery().toString());
            }
        }
    }

    private Intent getIntentPCAPDocument() {
        Intent intent;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            if (Util.isPackageInstalled("org.openintents.filemanager", this)) {
                intent = new Intent("org.openintents.action.PICK_DIRECTORY");
            } else {
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=org.openintents.filemanager"));
            }
        } else {
            intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_TITLE, "netguard_" + new SimpleDateFormat("yyyyMMdd").format(new Date().getTime()) + ".pcap");
        }
        return intent;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        Log.i(TAG, "onActivityResult request=" + requestCode + " result=" + requestCode + " ok=" + (resultCode == RESULT_OK));

        if (requestCode == REQUEST_PCAP) {
            if (resultCode == RESULT_OK && data != null)
                handleExportPCAP(data);

        } else {
            Log.w(TAG, "Unknown activity result request=" + requestCode);
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void handleExportPCAP(final Intent data) {
        new AsyncTask<Object, Object, Throwable>() {
            @Override
            protected Throwable doInBackground(Object... objects) {
                OutputStream out = null;
                FileInputStream in = null;
                try {
                    // Stop capture
                    ServiceSinkhole.setPcap(false, ActivityStats.this);

                    Uri target = data.getData();
                    if (data.hasExtra("org.openintents.extra.DIR_PATH"))
                        target = Uri.parse(target + "/netguard.pcap");
                    Log.i(TAG, "Export PCAP URI=" + target);
                    out = getContentResolver().openOutputStream(target);

                    File pcap = new File(getDir("data", MODE_PRIVATE), "netguard.pcap");
                    in = new FileInputStream(pcap);

                    int len;
                    long total = 0;
                    byte[] buf = new byte[4096];
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                        total += len;
                    }
                    Log.i(TAG, "Copied bytes=" + total);

                    return null;
                } catch (Throwable ex) {
                    Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                    return ex;
                } finally {
                    if (out != null)
                        try {
                            out.close();
                        } catch (IOException ex) {
                            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                        }
                    if (in != null)
                        try {
                            in.close();
                        } catch (IOException ex) {
                            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                        }

                    // Resume capture
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ActivityStats.this);
                    if (prefs.getBoolean("pcap", false))
                        ServiceSinkhole.setPcap(true, ActivityStats.this);
                }
            }

            @Override
            protected void onPostExecute(Throwable ex) {
                if (ex == null)
                    Toast.makeText(ActivityStats.this, R.string.msg_completed, Toast.LENGTH_LONG).show();
                else
                    Toast.makeText(ActivityStats.this, ex.toString(), Toast.LENGTH_LONG).show();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
