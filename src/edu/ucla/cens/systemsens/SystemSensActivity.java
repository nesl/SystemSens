package edu.ucla.cens.systemsens;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Vibrator;
import android.os.RemoteException;
import android.widget.Button;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.MotionEvent;


import edu.ucla.cens.systemsens.util.Status;

import java.util.Calendar;
import java.util.HashMap;
import java.text.DecimalFormat;
import org.json.JSONObject;
import org.json.JSONException;


public class SystemSensActivity extends Activity {
	
	private static final String TAG = "SystemSensActivity";

    private IPowerMonitor mSystemSens;
    private boolean mIsBound = false;

    private int mDeadline;
    private boolean mChanged = false;

    private TextView mValText;
    private TextView mStatusText;
    private SeekBar mSeekBar;
    private Button mSubmitButton;
    //private Button mOkButton;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
        Vibrator vib = (Vibrator)
            getSystemService(Context.VIBRATOR_SERVICE);
        vib.vibrate(500);
        */


        setContentView(R.layout.main);

        startService(new Intent(this, SystemSens.class));

        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(1);



        mValText = (TextView) findViewById(R.id.Value);
        mValText.setText("Scroll to set battery goal within next "
               + df.format(Status.getLevel()/6) + " hours.");




        mStatusText = (TextView) findViewById(R.id.Status);




        //mOkButton = (Button) findViewById(R.id.Ok);
        //mOkButton.setOnClickListener(mOkListener);

        mSubmitButton = (Button) findViewById(R.id.Submit);
        mSubmitButton.setOnClickListener(mSubmitListener);

        
        if (!mIsBound)
            bindService(new Intent(IPowerMonitor.class.getName()), 
                    mSystemSensConnection,
                    Context.BIND_AUTO_CREATE);


        /*
        mSeekBar = (SeekBar) findViewById(R.id.SeekBar);
        if (Status.isPlugged())
        {
            mValText.setText("Set battery goal after charging.");
            mSubmitButton.setEnabled(true);
            mSeekBar.setOnTouchListener(new OnTouchListener()
                {
                    @Override
                    public boolean onTouch(View v, MotionEvent event)
                    {
                        return true;
                    }
                }
            );
        }
        else
        {
            mSeekBar.setOnSeekBarChangeListener(mSBListener);
        }
        mSeekBar.setOnSeekBarChangeListener(mSBListener);

        */


    }

    @Override
    public void onPause()
    {

        super.onPause();

        if (mIsBound)
        {
            unbindService(mSystemSensConnection);
            mIsBound = false;
        }
        else
        {
            Log.i(TAG, "Flag is not set.");
        }



    }


    private ServiceConnection mSystemSensConnection =  
        new ServiceConnection()
    {
        public void onServiceConnected(ComponentName classname,
                IBinder service)
        {
            mSystemSens = IPowerMonitor.Stub.asInterface(service);
                
            mIsBound = true;
            Log.i(TAG, "Got SystemSens object");

            readStatus();

        }

        public void onServiceDisconnected(ComponentName className)
        {
            mIsBound = false;
            mSystemSens = null;

        }
    };


    private OnClickListener mOkListener = new OnClickListener()
    {
        public void onClick(View v)
        {
            SystemSensActivity.this.finish();

        }
    };




    private OnClickListener mSubmitListener = new OnClickListener()
    {
        public void onClick(View v)
        {
            if (mIsBound)
            {
                try
                {
                    if (mChanged)
                    {
                        mSystemSens.setDeadline(mDeadline);
                        Log.i(TAG, "Setting battery deadline to " + 
                                mDeadline);
                    }
                }
                catch (RemoteException re)
                {
                    Log.e(TAG, "Could not set deadline", re);
                }
                //Status.setDeadline(mDeadline);
            }
            else
            {
                Log.i(TAG, "Not connected to SystemSens.");
            }

            SystemSensActivity.this.finish();

        }
    };

    private OnSeekBarChangeListener  mSBListener = 
        new OnSeekBarChangeListener()
    {
        @Override
        public void onStopTrackingTouch(SeekBar seekBar)
        {
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar)
        {
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress,
                boolean fromUser)
        {
            Log.i(TAG, "Progress is " + progress);
            mValText.setText("Set battery goal to: \n" +
                    Status.deadlineStr(progress * 10));
            mDeadline = progress * 10;
            mChanged = true;
            mSubmitButton.setText("Submit");
        }
    };


    private void readStatus()
    {
        mStatusText.setText(Status.getString());
        double curDeadline =  Status.getDeadline();
        if (Double.isNaN(curDeadline))
        {
            Log.i(TAG, "curDeadline is NaN");
            curDeadline = 0.0D;

        }

        Log.i(TAG, "curLevel is " + Status.getLevel());
        mDeadline = (int)curDeadline;

        Log.i(TAG, "curDeadline is " + mDeadline);
        //mSeekBar.setMax(1440);

        mSeekBar = (SeekBar) findViewById(R.id.SeekBar);
        
        int maxTime = (int)Status.getLevel();
        mSeekBar.setMax(maxTime);

        mSeekBar.setProgress(mDeadline);

        if (Status.isPlugged())
        {
            mValText.setText("Set battery goal after charging.");
            mSubmitButton.setEnabled(true);
            mSeekBar.setOnTouchListener(new OnTouchListener()
                {
                    @Override
                    public boolean onTouch(View v, MotionEvent event)
                    {
                        return true;
                    }
                }
            );
        }
        else
        {
            mSeekBar.setOnSeekBarChangeListener(mSBListener);
        }
        /*
        mSeekBar.setOnSeekBarChangeListener(mSBListener);
        */


        mChanged = false;

    }
}
