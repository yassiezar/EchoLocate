package com.upwork.jaycee.echolocate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class ViewVisualiser extends View
{
    private int numFftBins = 0;
    private int width, height;
    private double[] binHeights;
    private double peak, normFactor;

    private Context context;
    private Paint paint = new Paint();

    public ViewVisualiser(Context context)
    {
        super(context);
        this.context = context;
    }

    public ViewVisualiser(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        this.context = context;
    }

    public ViewVisualiser(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
        this.context = context;
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);

        int sizeFactor = width / numFftBins;

        if(binHeights != null)
        {
            float binWidth = width / numFftBins * sizeFactor;

            paint.setColor(Color.RED);
            paint.setStrokeWidth(binWidth);

            // Log.d("Visualiser", String.format("width: %d binWidth: %f height: %d maxBinHeight: %f", width, binWidth, height, peak));
            for(int i = 0; i < numFftBins; i ++)
            {
                // canvas.drawRect(new Rect((int)(i * binWidth), (int)(binHeights[i] * display.heightPixels / peak), (int)((i + 1) * binWidth), 0), paint);
                canvas.drawLine(sizeFactor*i, 0, sizeFactor*(i + 1), (int)(binHeights[i] * height), paint);
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

    public void setPeak(double peak)
    {
        this.peak = peak;
    }
}
