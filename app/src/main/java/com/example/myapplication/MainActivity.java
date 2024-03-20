package com.example.myapplication;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    static class StaticInner implements View.OnClickListener {

        final private WeakReference<MainActivity> mainActivityWeakReference;

        public StaticInner(MainActivity activity) {
            mainActivityWeakReference=new WeakReference<>(activity);
        }

        @Override
        public void onClick(View v) {
            MainActivity activity= mainActivityWeakReference.get();
            if (activity!=null) {
                activity.activityMethod();
            }
        }
    }
    private List<Integer> outVar = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button button = (Button) findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activityMethod();
            }
        });

//        button.setOnClickListener(new StaticInner(this));
    }



    private int activityMethod() {
        if (new Random().nextInt(10)%2==0) {
            Log.e("mxz",outVar.toString());
            return 1;
        }
        Log.e("mxzz", outVar.toString());
        return 0;
    }
}