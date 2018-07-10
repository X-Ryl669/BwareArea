package fr.byped.bwarearea;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.InputStream;

public class WhyPermissionActivity extends AppCompatActivity {

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;

    /** The seek bar for the page selection */
    private SeekBar   slider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_why_permission);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
//        toolbar.setVisibility(View.GONE);
        //getActionBar().setIcon(R.drawable.ic_bware);
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        mViewPager.addOnPageChangeListener(
                new ViewPager.OnPageChangeListener() {
                    int lastPosition = 0;
                    @Override
                    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

                    }

                    @Override
                    public void onPageSelected(int position) {
                        slider.setProgress(position);
                        if (lastPosition > 0 && position != lastPosition)
                        {
                            switch(lastPosition)
                            {
                                case 1:
                                    askForLocationFetchingPermission();
                                    break;
                                case 2:
                                    askForSystemOverlayPermission();
                                    break;
                                case 3:
                                    askForExternalStorage();
                                    break;
                                case 4:
                                    askToDozeWhitelisting();
                                    break;

                            }
                        }
                        lastPosition = position;
                        if (position == 5) finish();
                    }

                    @Override
                    public void onPageScrollStateChanged(int state) {

                    }
                }
        );

        slider = (SeekBar)findViewById(R.id.positionPerms);
    }

    private static final int GET_GPS_POS = 125;
    private boolean askForLocationFetchingPermission() {
        if (Build.VERSION.SDK_INT >= 23)
        {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED)
            {
                Log.v("BwareArea","Fine location permission granted");
                return true;
            } else {
                Log.v("BwareArea","Fine location permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{ Manifest.permission.ACCESS_FINE_LOCATION }, GET_GPS_POS);
                return false;
            }
        }
        else { // permission is automatically granted on sdk<23 upon installation
            Log.v("BwareArea","Fine location permission granted");
            return true;
        }
    }

    private static final int READ_EXT_STORAGE = 126;
    public boolean askForExternalStorage() {
        if (Build.VERSION.SDK_INT >= 23)
        {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)  == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            {
                Log.v("BwareArea","Read / Write storage permission granted");
                return true;
            } else {
                Log.v("BwareArea","Read / Write storage Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE }, READ_EXT_STORAGE);
                return false;
            }
        }
        else { // permission is automatically granted on sdk<23 upon installation
            Log.v("BwareArea","Read / Write permission is granted");
            return true;
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode)
        {
            case READ_EXT_STORAGE:
                Log.d("BwareArea", "External storage");
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    Log.v("BwareArea","Permission: "+ permissions[0] + "was " + grantResults[0]);
                }
                break;
            case GET_GPS_POS:
                Log.d("BwareArea", "GPS fetching");
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    Log.v("BwareArea","Permission: "+ permissions[0] + "was " + grantResults[0]);
                }
                break;
            default: break;
        }
    }
    private static final int DRAW_OVER_OTHER_APP_PERMISSION = 123;
    private void askForSystemOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this))
        {
            // If the draw over permission is not available to open the settings screen
            // to grant the permission.
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, DRAW_OVER_OTHER_APP_PERMISSION);
        }
    }
    private static final int DISABLE_DOZE = 128;
    private void askToDozeWhitelisting() {
        PowerManager manager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !manager.isIgnoringBatteryOptimizations(getPackageName()))
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, DISABLE_DOZE);

            } catch (ActivityNotFoundException e) {
                e.printStackTrace();
            }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == DRAW_OVER_OTHER_APP_PERMISSION) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    // Permission is not available. Display error text.
                    errorToast();
                }
            }
        }
        else if (requestCode == DISABLE_DOZE)
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!((PowerManager) getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName()))
                    errorToast();
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_why_permission, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
      //  if (id == R.id.action_settings) {
      //      return true;
      //  }

        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        public PlaceholderFragment() {
        }

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {

            int sectionNumber = getArguments().getInt(ARG_SECTION_NUMBER);
            View rootView = inflater.inflate(R.layout.fragment_why_permission, container, false);

            TextView textView = (TextView) rootView.findViewById(R.id.describePerm);
            TextView titleView = (TextView) rootView.findViewById(R.id.titlePerm);

            switch(sectionNumber)
            {
                case 0:
                    textView.setText(getString(R.string.perm_page0));
                    titleView.setText(getString(R.string.perm_title0));
                    break;
                case 1:
                    titleView.setText(R.string.perm_title1);
                    textView.setText(R.string.perm_page1);
                    break;
                case 2:
                    titleView.setText(R.string.perm_title2);
                    textView.setText(R.string.perm_page2);
                    break;
                case 3:
                    titleView.setText(R.string.perm_title3);
                    textView.setText(R.string.perm_page3);
                    break;
                case 4:
                    titleView.setText(R.string.perm_title4);
                    textView.setText(R.string.perm_page4);
                    break;
                case 5:
                    titleView.setText(R.string.perm_title5);
                    textView.setText(R.string.perm_page5);
                    break;



            }
            //textView.setText(getString(R.string.section_format, getArguments().getInt(ARG_SECTION_NUMBER)));
            return rootView;
        }
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            return PlaceholderFragment.newInstance(position);
        }



        @Override
        public int getCount() {
            return 6;
        }
    }

    private void errorToast() {
        Toast.makeText(this, R.string.error_permissions, Toast.LENGTH_LONG).show();
    }

}
