/**
 * SystemSens
 *
 * Copyright (C) 2009 Hossein Falaki
 */
package edu.ucla.cens.systemsens.util;


import android.os.SystemClock;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import org.json.JSONObject;
import org.json.JSONException;


//import android.util.Log;
import edu.ucla.cens.systemlog.Log;

import edu.ucla.cens.systemsens.SystemSens;



/**
 * This class implements mechanisms to  receive and use power models.
 *
 * @author  Hossein Falaki
 */
public class PowerModel
{
    /** Tag used for log messages */
    private static final String TAG = "SystemSensPowerModel";


    /** After this number of failiurs upload will abort */
    private static final int MAX_FAIL_COUNT = 5;


    /** Location of power model on the server */
    private static final String POWER_URL_BASE
        = "https://systemsens.cens.ucla.edu/service/viz/model/";

    /** Location of resource stats on the server */
    private static final String STATS_URL_BASE
        = "https://systemsens.cens.ucla.edu/service/viz/stats/";




    /** Location to set deadline on the server */
    private static final String DEADLINE_URL_BASE
        = "https://systemsens.cens.ucla.edu/service/viz/set_deadline/";


    /** Policy name strings */
    public static final String RATE_POLICY = "rate_policy";
    public static final String WORKLOAD_POLICY = "workload_policy";


    /** Model or Target identifiers */
    private static final int MODEL = 1;
    private static final int STATS = 2;



    private static final long ONE_MINUTE = 1000 * 60;
    private static final long ONE_HOUR = 60 * ONE_MINUTE;
    private static final long ONE_DAY = 24 * ONE_HOUR;

    private static final long MIN_MODEL_REFRESH_INTERVAL = 
        10 * ONE_HOUR;

    private static final String IMEI = SystemSens.IMEI;


    /** Model related members */
    private HashMap<String, Double> mModel;
    private HashMap<String, Double> mStats;
    private List mAdaptiveApps;


    
    private long mModelDate;
    private long mStatsDate;

    private boolean mPlugged;


    private HashMap<Long, Integer> mVoltages;
    private HashMap<Long, Integer> mLevels;
    private HashMap<Long, Long> mCurrents;


    /** Deadline related parameters */
    private boolean mDeadlineSet;
    private boolean mSubmitted = false;
    private int mDeadline;
    private Calendar mStartTime;
    private int mStartLevel;
    private double mSlope;

    SimpleDateFormat mSDF;




    /**
     * Constructor - creates an empty power model object. 
     *
     * @param   dbAdaptor       database adaptor object
     */
    public PowerModel()
    {
        this.mModel = new HashMap<String, Double>();
        mModelDate = 0L;

        mVoltages = new HashMap<Long, Integer>();
        mLevels   = new HashMap<Long, Integer>();
        mCurrents = new HashMap<Long, Long>();

        mPlugged = false;

        mAdaptiveApps = new ArrayList();
        mAdaptiveApps.add("gps");

        mSDF = new SimpleDateFormat("yyyy-MM-dd HH:mm",
            Locale.US);

    }


    /**
     * Indicates that the phone has been plugged.
     *
     */
    public void plugged()
    {
        mPlugged = true;
        mDeadlineSet = false;
    }

    /**
     * Indicates that the phone has been unplugged.
     *
     */
    public void unplugged()
    {
        mPlugged = false;
        mVoltages = new HashMap<Long, Integer>();
        mLevels   = new HashMap<Long, Integer>();
        mCurrents = new HashMap<Long, Long>();
    }



    /**
     * Records battery information with a timestamp.
     *
     * @param       voltage     The battery voltage
     * @param       level       The battery level 
     * @param       current     The battery discharge current
     */ 
    public void recordBatInfo(int voltage, int level, long current)
    {
        if (mPlugged)
            return;

        Long ts = SystemClock.elapsedRealtime();

        if (current < 0)
            current = -1 * current;

        mVoltages.put(ts, voltage);
        mLevels.put(ts, level);
        mCurrents.put(ts, current);
    }


    /**
     * Calculates the expected battery curve for a given deadline.
     *
     * @param   deadline    expected deadline in minutes from now
     */
    public void setDeadline(Calendar setTime, int deadline, int level)
    {

        if (setTime == null)
        {
            mDeadlineSet = false;
            return;
        }

        mStartTime = setTime;
        mDeadlineSet = true;
        mDeadline = deadline;
        mSubmitted = false;



        //mStartLevel = (int)mLevels.get(Collections.max(mLevels.keySet()));
        mStartLevel = level;
        mSlope = -1.0 * mStartLevel/mDeadline;


        Log.i(TAG, getDeadlineStr());

        Log.i(TAG, "Deadline set to " + deadline);

    }



    private String getDeadlineStr()
    {
        if (!mDeadlineSet)
            return "Deadline not set.";

        Calendar deadline = (Calendar)mStartTime.clone();
        deadline.add(Calendar.MINUTE, mDeadline);

        String resStr = "Deadline is " 
            + mSDF.format(deadline.getTime())   
            + " submitted at "
            + mSDF.format(mStartTime.getTime())
            + " (" + mStartLevel + "%)";


        return resStr;

    }

    /**
     * Submits the deadline to the back-end server for visualization
     * and evaluation.
     *
     */
    public void submitDeadline()
    {

        Log.i(TAG, "Submitting the deadling.");

        if (mSubmitted)
        {
            Log.i(TAG, "Deadline already submitted.");
            return;
        }

        if (!mDeadlineSet)
        {
            Log.i(TAG, "Deadline is not set.");
            return;
        }

        if (mStartTime == null)
        {
            Log.i(TAG, "StartTime is null. It should not be null!");
            return;
        }


        

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm",
                Locale.US);


        Calendar deadline = (Calendar)mStartTime.clone();
        deadline.add(Calendar.MINUTE, mDeadline);

        String dest = DEADLINE_URL_BASE + IMEI + "/"
            + sdf.format(mStartTime.getTime()) + "/"
            + sdf.format(deadline.getTime())   + "/"
            + mStartLevel + "/";

        Log.i(TAG, getDeadlineStr());


        int respCode;
        String respMsg = "";
        HttpURLConnection con;
        URL url;



        try
        {
            url = new URL(dest);
        }
        catch (MalformedURLException e)
        {
            Log.e(TAG, "Exception", e);
            return;
        }


        try
        {
            con = (HttpURLConnection) url.openConnection();
        }
        catch (IOException e)
        {
            Log.e(TAG, "Exception", e);
            return;
        }


        try
        {
            con.setRequestMethod("GET");
        }
        catch (java.net.ProtocolException e)
        {
            Log.e(TAG, "Exception", e);
            return;
        }
        con.setUseCaches(false);
        con.setDoOutput(true);
        con.setDoInput(true);

        try
        {
            con.connect();

            respMsg = con.getResponseMessage();
            respCode = con.getResponseCode();
        }
        catch (IOException e)
        {
            Log.e(TAG, "Exception", e);
            con.disconnect();
            return;
        }



        if (respCode != HttpURLConnection.HTTP_OK)
        {
            Log.e(TAG, "post failed with error: " 
                    + respMsg);
            con.disconnect();
            return;
        }
        else
        {
            mSubmitted = true;
        }

    }

    /**
     * Returns the ideal battery level based on time and current 
     * deadline information.
     * 
     */
    private int getIdealLevel()
    {
        if (!mDeadlineSet)
            return -1;

        if (mStartTime == null)
            return -1;
 
        long now = Calendar.getInstance().getTimeInMillis();

        double passed = (now -  mStartTime.getTimeInMillis())/ONE_MINUTE;


        int ideal = (int) (mStartLevel + passed*mSlope);


        return ideal;
    }


    /**
     * Returns the difference between current battery level and ideal
     * battery level. The return value currentLevel - idealLevel
     *
     * @return      percentage points of difference between current
     *              and ideal
     */
    public int levelGap()
    {
        int currentLevel = (int)Status.getLevel();
        int ideal = getIdealLevel();

        if (ideal < 0)
        {
            Log.i(TAG, "There seems to be no battery goal");
            return Integer.MAX_VALUE;
        }

        int gap = currentLevel - ideal;


        Log.i(TAG, "current (" + currentLevel + ") - ideal ("
                + ideal + ") = " + gap);

        return gap;

    }

    /**
     * Returns the suggested "Rate change" for the given work unit.
     * The value returned by this method will be multiplied by the
     * privously reported sum of work units and suggested for the next
     * horizon.
     *
     * Special returned values are:
     * NaN: No budget needs to be set
     * 1.0: The current rate is good
     *
     * @param       unitName        Name of the unit to calculate rate
     *                              for
     * @param           the rate to be multiplied by the past work to
     *                  give the future budget
     */
    public double suggestRate(String unitName)
    {
        Log.i(TAG, "Suggesting rate");

        if (mPlugged)
        {
            Log.i(TAG, "Freedom when phone is plugged");
            return Double.NaN;
        }

        if (!mDeadlineSet)
        {
            Log.i(TAG, "Freedome with no deadline");
            return Double.NaN;
        }


        if (mModel == null)
        {
            Log.i(TAG, "No model found");
            return Double.NaN;
        }

        int gap = levelGap();

        if (gap == Integer.MAX_VALUE)
        {
            Log.i(TAG, "Something wrong. Gap is too large");
            return Double.NaN;
        }


        if (!mModel.containsKey(unitName))
        {
            Log.i(TAG, "Model does not inclulde " + unitName);
            return Double.NaN;
        }

        double coef = mModel.get(unitName);

        Log.i(TAG, getDeadlineStr());
        
        if (gap <= -1)
        {
            Log.i(TAG, "Reducing rate");
            return 0.5;
        }
        else if (gap >= 1)
        {
            Log.i(TAG, "Increasing rate");
            return 2.0;
        }
        else 
        {
            Log.i(TAG, "Keeping rate");
            return 1.0;
        }
    }


    /**
     * Returns the allowed total units of work for the next horizon
     * for the given unitName.
     *
     *
     * @param       unitName        Name of the unit to calculate rate
     *                              for
     * @param                       the number of allowed units for
     *                              the next horizon.
     */

    public double suggestWorkLimit(String unitName)
    {
        if (mPlugged)
            return Double.NaN;

        if (!mDeadlineSet)
            return Double.NaN;



        if ((mModel == null) || (mStats == null))
            return Double.NaN;


        if (!mModel.containsKey(unitName))
            return Double.NaN;


        long curTime =
            Calendar.getInstance().getTimeInMillis();


        Calendar deadline = Calendar.getInstance();
        deadline.add(Calendar.MINUTE, mDeadline);
        long deadlineTime = deadline.getTimeInMillis();

        long left = ((deadlineTime - curTime)/ONE_MINUTE)/1400;

        if (left < 0)
        {
            Log.i(TAG, "Deadline already missed.");
            return Double.NaN;
        }


        double curResource, interactive = 0.0;
        double mean, coef;

        for (String resource: mModel.keySet())
        {
            if ((!mAdaptiveApps.contains(resource)) &&
                    (mStats.containsKey(resource)))
            {
                mean = mStats.get(resource);
                coef = mModel.get(resource);

                curResource = (mean * coef)/left;
                interactive += curResource;
            }
        }


        double available = Status.getLevel() - interactive;

        if (available < 0.0)
            available = 0.0;

        double workLoad = available/mModel.get(unitName);
            


        return workLoad;


    }


    /**
     * Returns true if the current model is stale.
     *
     * @param       target      specifying model or stat
     * @return      true if the model is too old
     */

    public boolean isStale(int target)
    {
        long curTime = Calendar.getInstance().getTimeInMillis();
        long targetDate;

        if (target == MODEL)
            targetDate = mModelDate;
        else
            targetDate = mStatsDate;



        if (curTime - targetDate < MIN_MODEL_REFRESH_INTERVAL) 
            return false;
        else
            return true;

    }



    /**
     * Gets the power model for this phone from the server.
     *
     * @return          if the model could be successfully fetched
     */
    public boolean getModel()
    {
        Log.i(TAG, "Getting power model started");


        long curTime = Calendar.getInstance().getTimeInMillis();

        if (!isStale(MODEL))
            return false;

        String dest = POWER_URL_BASE + IMEI;

        InputStream inputstream;
        int maxLen = 1024;
        byte[] buff = new byte[maxLen];
        int respCode;
        String respMsg = "";
        HttpURLConnection con;
        URL url;


        try
        {
            url = new URL(dest);
        }
        catch (MalformedURLException e)
        {
            Log.e(TAG, "Exception", e);
            return false;
        }


        try
        {
            con = (HttpURLConnection) url.openConnection();
        }
        catch (IOException e)
        {
            Log.e(TAG, "Exception", e);
            return false;
        }


        try
        {
            con.setRequestMethod("GET");
        }
        catch (java.net.ProtocolException e)
        {
            Log.e(TAG, "Exception", e);
            return false;
        }
        con.setUseCaches(false);
        con.setDoOutput(true);
        con.setDoInput(true);

        try
        {
            con.connect();

            respMsg = con.getResponseMessage();
            respCode = con.getResponseCode();
        }
        catch (IOException e)
        {
            Log.e(TAG, "Exception", e);
            con.disconnect();
            return false;
        }



        if (respCode != HttpURLConnection.HTTP_OK)
        {
            Log.e(TAG, "post failed with error: " 
                    + respMsg);
            con.disconnect();
            return false;
        }


        try
        {
            inputstream = con.getInputStream();
            int bytesRead = inputstream.read(buff, 0, maxLen);
        }
        catch (Exception e)
        {
            Log.e(TAG, "Could not read", e);
            return false;
        }

        con.disconnect();

        JSONObject jsonModel;

        try
        {
            jsonModel = new JSONObject(new String(buff));
        }
        catch (JSONException je)
        {
            Log.e(TAG, "Could not parse the model to JSON", je);
            return false;
        }

        try
        {
            String param;
            for (Iterator<String> key = jsonModel.keys();
                    key.hasNext(); )
            {
                param = key.next();
                mModel.put(param, (Double)jsonModel.get(param));
            }

        }
        catch (JSONException je)
        {
            Log.e(TAG, "Could not read the JSON model", je);
            return false;
        }


        Log.i(TAG, "Built the model: " + mModel.toString());


        mModelDate = curTime;
        return true;

    }

    /**
     * Gets the statistics of resource consumption for the user.
     *
     * @return          if the model could be successfully fetched
     */
    public boolean getStats()
    {
        Log.i(TAG, "Getting resource stats started");


        long curTime = Calendar.getInstance().getTimeInMillis();

        if (!isStale(STATS))
            return false;

        String dest = STATS_URL_BASE + IMEI;

        InputStream inputstream;
        int maxLen = 2024;
        byte[] buff = new byte[maxLen];
        int respCode;
        String respMsg = "";
        HttpURLConnection con;
        URL url;


        try
        {
            url = new URL(dest);
        }
        catch (MalformedURLException e)
        {
            Log.e(TAG, "Exception", e);
            return false;
        }


        try
        {
            con = (HttpURLConnection) url.openConnection();
        }
        catch (IOException e)
        {
            Log.e(TAG, "Exception", e);
            return false;
        }


        try
        {
            con.setRequestMethod("GET");
        }
        catch (java.net.ProtocolException e)
        {
            Log.e(TAG, "Exception", e);
            return false;
        }
        con.setUseCaches(false);
        con.setDoOutput(true);
        con.setDoInput(true);

        try
        {
            con.connect();

            respMsg = con.getResponseMessage();
            respCode = con.getResponseCode();
        }
        catch (IOException e)
        {
            Log.e(TAG, "Exception", e);
            con.disconnect();
            return false;
        }



        if (respCode != HttpURLConnection.HTTP_OK)
        {
            Log.e(TAG, "Get failed with error: " 
                    + respMsg);
            con.disconnect();
            return false;
        }


        try
        {
            inputstream = con.getInputStream();
            int bytesRead = inputstream.read(buff, 0, maxLen);
        }
        catch (Exception e)
        {
            Log.e(TAG, "Could not read", e);
            return false;
        }

        con.disconnect();

        JSONObject jsonModel;

        try
        {
            jsonModel = new JSONObject(new String(buff));
        }
        catch (JSONException je)
        {
            Log.e(TAG, "Could not parse resource stats to JSON", je);
            return false;
        }

        try
        {
            String param;
            for (Iterator<String> key = jsonModel.keys();
                    key.hasNext(); )
            {
                param = key.next();
                mStats.put(param, (Double)jsonModel.get(param));
            }

        }
        catch (JSONException je)
        {
            Log.e(TAG, "Could not read the JSON model", je);
            return false;
        }


        Log.i(TAG, "read resource stats: " + mStats.toString());


        mStatsDate = curTime;
        return true;

    }




}
