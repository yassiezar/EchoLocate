package com.upwork.jaycee.echolocate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ViewVisualiser extends View
{
    private int numFftBins = 0;
    private int width, height;
    private double[] binHeights;

    public ViewVisualiser(Context context)
    {
        super(context);
    }

    public ViewVisualiser(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public ViewVisualiser(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);
        // canvas.drawColor(Color.RED);
        if(binHeights != null)
        {
            Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setStrokeWidth(2.f);

            float binWidth = width / numFftBins;
            Log.d("Visualiser", String.format("binWidth: %f height: %d maxBinHeight: %f", binWidth, height, binHeights[20]));
            for(int i = 0; i < binHeights.length; i ++)
            {
                canvas.drawRect(new Rect((int)(i * binWidth), (int)(binHeights[i]), (int)(i * binWidth + binWidth), 0), paint);
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh)
    {
        super.onSizeChanged(w, h, oldw, oldh);
        this.width = w;
        this.height = h;
    }

    public void setNumFftBins(int numFftBins)
    {
        this.numFftBins = numFftBins;
    }

    public void setBinHeights(double[] binHeights)
    {
        this.binHeights = binHeights;
    }
}
