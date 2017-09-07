package edwards.alexander.panning_zooming;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private PaperView layout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        layout = new PaperView(this);
        setContentView(layout);
    }

    public class PaperView extends View {
        private float mScaleFactor; //How much to scale the canvas by
        private float lastTouchX; private float lastTouchY; //The x and y coordinates recorded in the last touch event
        private float posX; private float posY; //Canvas coordinates, these are used for allowing canvas translation
        private int Active; //Active will hold the pointer ID of the "active" finger. This allows us to track the priority touch when translating the canvas. (e.g. no random jumpiness).
        private float focusX; private float focusY; //Midpoint between two fingers zooming.
        private ScaleGestureDetector mScaleDetector; //Detects certain gestures (e.g. zoom, pan).
        private RectF test; //Drawable for reference.
        private Paint tPaint;

        /*
            Variables for pathing. goodluck llol
         */
        private ArrayList<Path> paths;
        private Path activePath;
        private Paint myPaint;
        private boolean drawing = true;
        private Paint bg;

        //haltTranslate acts as a toggle that stop canvas translation during the beginning of zoom detection. This allows for smoother performance.
        //An AtomicBoolean was chosen over a boolean because the touch listeners seem to run on separate threads which restricts boolean communication.
        // (AtomicBooleans are shared between threads).
        private AtomicBoolean haltTranslate;
        private AtomicBoolean focusOnce; //A toggle used to confirm focusX and focusY only being set at the beginning of the zoom.

        PaperView(Context context) {
            super(context);
            mScaleFactor = 1;
            lastTouchX = 0; lastTouchY = 0;
            posX = 0; posY = 0;
            focusX = 0; focusY = 0;
            focusOnce = new AtomicBoolean(true);
            haltTranslate = new AtomicBoolean(false);
            mScaleDetector = new ScaleGestureDetector(context, new ScaleListener()); //Giving the Gesture Detector a Listener.

            test = new RectF(0, 0, 200, 200);
            tPaint = new Paint();
            tPaint.setColor(Color.BLACK);
            tPaint.setStyle(Paint.Style.FILL);
            tPaint.setTextSize(48f);

            paths = new ArrayList<>();
            activePath = new Path();
            myPaint = new Paint();
            myPaint.setAntiAlias(true);
            myPaint.setDither(true);
            myPaint.setColor(Color.BLACK);
            myPaint.setStyle(Paint.Style.STROKE);
            myPaint.setStrokeJoin(Paint.Join.ROUND);
            myPaint.setStrokeCap(Paint.Cap.ROUND);
            myPaint.setStrokeWidth(12);

            bg = new Paint();
            bg.setColor(Color.LTGRAY);
        }

        private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener { //Listener for scaling the canvas.
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                mScaleFactor *= detector.getScaleFactor();  //Sets the canvas scalar by however much "scaling" is detected.
                if(focusOnce.get()) {    //Ensures focal point for zooming is only determined once when zoom gesture is detected.
                    focusOnce.set(false);
                    haltTranslate.set(false);   //Allows for canvas translation again.
                    focusX = detector.getFocusX();  //Determining focal point of zoom.
                    focusY = detector.getFocusY();
                }
                invalidate();   //Updates the screen after scaling the canvas.
                return true;
            }
        }

        @Override
        public void onDraw(Canvas canvas) { //This is what gets rendered when "invalidate()" is called.
            super.onDraw(canvas);
            canvas.save();
            canvas.translate(posX, posY);   //Translate the canvas to the specified location.
            canvas.scale(mScaleFactor, mScaleFactor, focusX, focusY);   //Scale the canvas// relative to the specified focal point.

            /*
                Rect: for debug, use to see bounds of initial canvas.
                activePath: draw the active path being moved by finger
                paths: ArrayList<Path> of old activePaths *****CURRENTLY NOT WORKING POR QUE??????????????????????*****
             */
            canvas.drawRect(0,0,canvas.getWidth(),canvas.getHeight(),bg);
            canvas.drawPath(activePath,myPaint);
            for(int i=0; i<paths.size(); ++i){
                canvas.drawPath(paths.get(i),myPaint);
            }
            canvas.restore();

            /*
                THESE ITEMS ARE STATIC TO SCREEN: THEY DO NOT SCALE OR TRANSLATE
                test rect is clickable area to toggle mode
                debug text on bottom
             */
            canvas.drawRect(test, tPaint);  //Draws the reference drawable.
            canvas.drawText("Drawing:\t" + drawing + "\t\t|\tOffset X:\t" + posX + "\t\t|\tOffset Y:\t" + posY,0,canvas.getHeight()-50,tPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {   //Listens for touch events (e.g. you putting a finger on the screen).
            if(!drawing) mScaleDetector.onTouchEvent(ev);    //Allows the ScaleDetector to check any and all events that trigger onTouchEvent.
            int index;  //Variable for holding a temporary pointer index. (The array slot the pressed finger is stored in).

            switch(ev.getActionMasked()) {  //Grabbing the event action that triggered onTouchEvent without any other pointer/index information.
                case MotionEvent.ACTION_DOWN:   //First touch.
                    lastTouchX = ev.getX(); //Storing the coordinates of the touch event.
                    lastTouchY = ev.getY();

                    /*
                        drawing: boolean value that dictates drawing mode or panning mode.
                            toggled when touching the black square on top left corner
                        move active path to location, but not working properly and it doesnt go to the correct spot after panning, who knows. maybe it should be a feature
                        PS: designed without multitouch protection NOT RESPONSIBLE for errors there sorry buds
                     */
                    if(drawing){
                        activePath.moveTo(lastTouchX,lastTouchY);
                    }
                    if((lastTouchX>0 && lastTouchX<200) && (lastTouchY>0 && lastTouchY<200)){
                        drawing = !drawing;
                    }
                    Active = 0; //The pointer of the first touch will always be zero, Active is set accordingly.
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    haltTranslate.set(true);    //Gives the scaling listener a second to figure out whether the canvas is being scaled or not.
                    return true;
                case MotionEvent.ACTION_MOVE:   //Any moving at all.
                    index = ev.findPointerIndex(Active);    //Determining the index of the active pointer ID. This is important because indices will change depending on the # of touches.
                    float x = ev.getX(index);   //Storing the coordinates of the specified touch event.
                    float y = ev.getY(index);
                    if(!haltTranslate.get() && !drawing) {  //Canvas translation halts until the scaling listener gets its shit together.
                        posX += (x - lastTouchX);   //Updating the position of the canvas according to the current touch event placement.
                        posY += (y - lastTouchY);
                    }

                    /*
                        your classic quadTo statement. Works fucking flawlessly except for the fact that its never in the right spot SMH
                     */
                    if(drawing){
                        activePath.quadTo(lastTouchX, lastTouchY, ((x + lastTouchX) / 2), ((y + lastTouchY) / 2));
                    }
                    lastTouchX = x; lastTouchY = y; //Updates the last touches recorded with the new touch event coordinates.
                    invalidate();   //Updates the screen after updating the values above.
                    return true;
                case MotionEvent.ACTION_POINTER_UP: //Any finger being lifted off the screen.
                    haltTranslate.set(false);   //A finger has been lifted up, meaning no scaling is happening, meaning we don't need to worry about pausing translation.
                    focusOnce.set(true);   //Allow for new focal point to be determined.
                    index = ev.getActionIndex() ^ 0x1;  //XOR for opposite affect on return value of ev.getActionIndex()
                    lastTouchX = ev.getX(index);    //Storing the coordinates of the specified touch event.
                    lastTouchY = ev.getY(index);
                    Active = index; //Updating the "active" finger to adjust for a finger being lifted.
                    return true;

                /*
                    simple add the active path to the drawqueue before resetting so it can create another path. it's definitely added, i dont know why it doesn't fucking show up on onDraw
                 */
                case MotionEvent.ACTION_UP:
                    if(drawing){
                        paths.add(activePath);
                    }
                    activePath.reset();
                    return true;
            }
            return false;
        }
    }
}
